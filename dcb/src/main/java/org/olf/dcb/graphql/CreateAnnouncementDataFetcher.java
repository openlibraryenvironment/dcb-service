package org.olf.dcb.graphql;

import static java.util.UUID.randomUUID;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.branding.BrandingValidator;
import org.olf.dcb.core.model.Announcement;
import org.olf.dcb.core.model.ScopeLevel;
import org.olf.dcb.storage.AnnouncementRepository;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Publishing an announcement.
 *
 * Every announcement rule that can be enforced on write is enforced here or by the
 * column definitions, rather than left to the admin form: a form is one client, and the
 * next one will not know the rules.
 */
@Singleton
@Slf4j
public class CreateAnnouncementDataFetcher implements DataFetcher<CompletableFuture<Announcement>> {

	private final AnnouncementRepository announcements;
	private final AnnouncementAuthorisation authorisation;
	private final BrandingValidator brandingValidator;

	public CreateAnnouncementDataFetcher(AnnouncementRepository announcements,
		AnnouncementAuthorisation authorisation, BrandingValidator brandingValidator) {

		this.announcements = announcements;
		this.authorisation = authorisation;
		this.brandingValidator = brandingValidator;
	}

	@Override
	public CompletableFuture<Announcement> get(DataFetchingEnvironment env) {
		// Who may reach it at all; AnnouncementAuthorisation then decides which scope.
		GraphQLRoles.require(env, "createAnnouncement", GraphQLRoles.ADMINISTRATIVE);

		final Map<String, Object> input = env.getArgument("input");

		final var userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		final var scopeType = AnnouncementInputs.scopeLevel(input);
		final var scopeId = AnnouncementInputs.uuid(input, "scopeId");
		final var startsAt = AnnouncementInputs.instant(input, "startsAt");
		final var expiresAt = AnnouncementInputs.instant(input, "expiresAt");

		AnnouncementInputs.assertWindow(startsAt, expiresAt);

		// Rendered as a link in the admin apps, so held to the same scheme check as any link
		final var changeReferenceUrl = brandingValidator.linkUrl(
			InputValues.stringValue(input, "changeReferenceUrl"));

		return authorisation.assertMayWrite(env, scopeType, scopeId)
			.flatMap(permitted -> Mono.from(announcements.save(Announcement.builder()
				.id(randomUUID())
				.scopeType(scopeType)
				.scopeId(permitted)
				.headline(AnnouncementInputs.required(input, "headline", 200))
				.body(AnnouncementInputs.required(input, "body", 1000))
				.urgent(AnnouncementInputs.flag(input, "urgent", false))
				.dismissible(AnnouncementInputs.flag(input, "dismissible", true))
				.startsAt(startsAt)
				.expiresAt(expiresAt)
				.lastEditedBy(userString)
				.reason(InputValues.stringValue(input, "reason"))
				.changeCategory(InputValues.stringValue(input, "changeCategory"))
				.changeReferenceUrl(changeReferenceUrl)
				.build())))
			.cast(Announcement.class)
			.doOnSuccess(saved -> log.info("Announcement published at {} scope by {}",
				saved.getScopeType(), userString))
			.toFuture();
	}

	/**
	 * Reading and checking an announcement's input, shared by create and update.
	 *
	 * Separate from either fetcher because the two must agree: an update that accepted a
	 * window create refuses is how a notice with no end date gets into the table anyway.
	 */
	static final class AnnouncementInputs {

		private AnnouncementInputs() {
		}

		static ScopeLevel scopeLevel(Map<String, Object> input) {
			final var raw = InputValues.stringValue(input, "scopeType");

			if (raw == null) {
				throw badRequest("scopeType is required");
			}

			try {
				return ScopeLevel.valueOf(raw);
			}
			catch (IllegalArgumentException e) {
				throw badRequest("unknown scopeType '" + raw + "'");
			}
		}

		static UUID uuid(Map<String, Object> input, String key) {
			final var raw = InputValues.stringValue(input, key);

			if (raw == null) {
				throw badRequest(key + " is required");
			}

			try {
				return UUID.fromString(raw);
			}
			catch (IllegalArgumentException e) {
				throw badRequest(key + " is not an id");
			}
		}

		static Instant instant(Map<String, Object> input, String key) {
			final var raw = InputValues.stringValue(input, key);

			if (raw == null) {
				// The expiry is required and there is deliberately no "never" value.
				// The failure mode of every announcement banner ever built is the notice from
				// March still up in November.
				throw badRequest(key + " is required; an announcement with no window is a "
					+ "notice nobody will take down");
			}

			try {
				return Instant.parse(raw);
			}
			catch (DateTimeParseException e) {
				throw badRequest(key + " must be an ISO-8601 instant, for example "
					+ "2026-09-30T09:00:00Z");
			}
		}

		static void assertWindow(Instant startsAt, Instant expiresAt) {
			// The database refuses this too. Both, because a constraint violation reaches an
			// administrator as a 500 with a Postgres message in it, and this reaches them as
			// a sentence about the thing they typed.
			if (!expiresAt.isAfter(startsAt)) {
				throw badRequest("expiresAt must be after startsAt");
			}
		}

		static String required(Map<String, Object> input, String key, int maxLength) {
			final var value = InputValues.stringValue(input, key);

			if (value == null || value.isBlank()) {
				throw badRequest(key + " is required");
			}

			if (value.length() > maxLength) {
				throw badRequest(key + " is longer than " + maxLength + " characters");
			}

			return value;
		}

		static boolean flag(Map<String, Object> input, String key, boolean whenAbsent) {
			final var value = input.get(key);

			return value instanceof Boolean flag ? flag : whenAbsent;
		}

		static HttpStatusException badRequest(String message) {
			return new HttpStatusException(HttpStatus.BAD_REQUEST, message);
		}
	}
}
