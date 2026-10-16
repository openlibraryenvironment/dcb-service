package org.olf.dcb.graphql;

import static org.olf.dcb.graphql.CreateAnnouncementDataFetcher.AnnouncementInputs;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.olf.dcb.core.branding.BrandingValidator;
import org.olf.dcb.core.model.Announcement;
import org.olf.dcb.storage.AnnouncementRepository;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Correcting an announcement that is already published.
 *
 * <h2>The scope cannot be edited, and that is deliberate</h2>
 *
 * Moving a library's notice to consortium scope by editing it would widen its blast radius
 * to every patron of every member library through a path that never asks the question the
 * create path asks. Authorisation is checked against the scope the row ALREADY has; a
 * notice that belongs somewhere else is withdrawn and republished, which is one extra click
 * and one honest audit entry instead of a silent escalation.
 */
@Singleton
@Slf4j
public class UpdateAnnouncementDataFetcher implements DataFetcher<CompletableFuture<Announcement>> {

	private final AnnouncementRepository announcements;
	private final AnnouncementAuthorisation authorisation;
	private final BrandingValidator brandingValidator;

	public UpdateAnnouncementDataFetcher(AnnouncementRepository announcements,
		AnnouncementAuthorisation authorisation, BrandingValidator brandingValidator) {

		this.announcements = announcements;
		this.authorisation = authorisation;
		this.brandingValidator = brandingValidator;
	}

	@Override
	public CompletableFuture<Announcement> get(DataFetchingEnvironment env) {
		// Who may reach it at all; AnnouncementAuthorisation then decides which scope.
		GraphQLRoles.require(env, "updateAnnouncement", GraphQLRoles.ADMINISTRATIVE);

		final Map<String, Object> input = env.getArgument("input");

		final var userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		final var id = UUID.fromString(AnnouncementInputs.required(input, "id", 64));

		return Mono.from(announcements.findById(id))
			.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
				"No announcement with id " + id)))
			.flatMap(existing -> authorisation
				.assertMayWrite(env, existing.getScopeType(), existing.getScopeId())
				.thenReturn(existing))
			.map(existing -> applyTo(existing, input, userString, brandingValidator))
			.flatMap(updated -> Mono.from(announcements.update(updated)))
			.cast(Announcement.class)
			.doOnSuccess(saved -> log.info("Announcement at {} scope edited by {}",
				saved.getScopeType(), userString))
			.toFuture();
	}

	/**
	 * An absent key leaves the field alone; a present one replaces it.
	 *
	 * The window is validated whenever EITHER end is supplied, against the resulting pair
	 * rather than the supplied one — moving only the start past an unchanged end is exactly
	 * the edit that would otherwise produce a notice that can never show.
	 */
	private static Announcement applyTo(Announcement existing, Map<String, Object> input,
		String userString, BrandingValidator brandingValidator) {

		final var startsAt = input.containsKey("startsAt")
			? AnnouncementInputs.instant(input, "startsAt")
			: existing.getStartsAt();

		final var expiresAt = input.containsKey("expiresAt")
			? AnnouncementInputs.instant(input, "expiresAt")
			: existing.getExpiresAt();

		AnnouncementInputs.assertWindow(startsAt, expiresAt);

		existing.setStartsAt(startsAt);
		existing.setExpiresAt(expiresAt);

		if (input.containsKey("headline")) {
			existing.setHeadline(AnnouncementInputs.required(input, "headline", 200));
		}
		if (input.containsKey("body")) {
			existing.setBody(AnnouncementInputs.required(input, "body", 1000));
		}
		if (input.containsKey("urgent")) {
			existing.setUrgent(AnnouncementInputs.flag(input, "urgent", false));
		}
		if (input.containsKey("dismissible")) {
			existing.setDismissible(AnnouncementInputs.flag(input, "dismissible", true));
		}

		existing.setLastEditedBy(userString);
		Optional.ofNullable(InputValues.stringValue(input, "reason")).ifPresent(existing::setReason);
		Optional.ofNullable(InputValues.stringValue(input, "changeCategory"))
			.ifPresent(existing::setChangeCategory);
		if (input.containsKey("changeReferenceUrl")) {
			existing.setChangeReferenceUrl(brandingValidator.changedLinkUrl(
				InputValues.stringValue(input, "changeReferenceUrl"), existing.getChangeReferenceUrl()));
		}

		return existing;
	}
}
