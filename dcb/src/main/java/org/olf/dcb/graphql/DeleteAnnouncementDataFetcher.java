package org.olf.dcb.graphql;

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
 * Taking an announcement down before it expires.
 *
 * The outage is over, or it was published in error. Authorised at the scope the
 * announcement is ALREADY at, read from the row rather than the request: a caller who
 * could name any scope in the body could delete a consortium notice by claiming it was
 * their library's.
 */
@Singleton
@Slf4j
public class DeleteAnnouncementDataFetcher implements DataFetcher<CompletableFuture<Map<String, Object>>> {

	private final AnnouncementRepository announcements;
	private final AnnouncementAuthorisation authorisation;
	private final BrandingValidator brandingValidator;

	public DeleteAnnouncementDataFetcher(AnnouncementRepository announcements,
		AnnouncementAuthorisation authorisation, BrandingValidator brandingValidator) {

		this.announcements = announcements;
		this.authorisation = authorisation;
		this.brandingValidator = brandingValidator;
	}

	@Override
	public CompletableFuture<Map<String, Object>> get(DataFetchingEnvironment env) {
		// Who may reach it at all; AnnouncementAuthorisation then decides which scope.
		GraphQLRoles.require(env, "deleteAnnouncement", GraphQLRoles.ADMINISTRATIVE);

		final Map<String, Object> input = env.getArgument("input");

		final var userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
			.map(Object::toString)
			.orElse("User not detected");

		final var id = UUID.fromString(
			CreateAnnouncementDataFetcher.AnnouncementInputs.required(input, "id", 64));

		return Mono.from(announcements.findById(id))
			.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
				"No announcement with id " + id)))
			.flatMap(existing -> authorisation
				.assertMayWrite(env, existing.getScopeType(), existing.getScopeId())
				.thenReturn(existing))
			// Stamped first: the audit trigger records the deleted row, so without this the log
			// names the last editor and their reason as though they had withdrawn it
			.map(existing -> stampWithdrawal(existing, input, userString))
			.flatMap(existing -> Mono.from(announcements.update(existing)).thenReturn(existing))
			.flatMap(existing -> Mono.from(announcements.delete(existing.getId()))
				.thenReturn(existing))
			.map(deleted -> result(deleted, userString))
			.toFuture();
	}

	private Announcement stampWithdrawal(Announcement existing, Map<String, Object> input,
		String userString) {

		existing.setLastEditedBy(userString);
		existing.setReason(InputValues.stringValue(input, "reason"));
		existing.setChangeCategory(InputValues.stringValue(input, "changeCategory"));
		existing.setChangeReferenceUrl(brandingValidator.linkUrl(
			InputValues.stringValue(input, "changeReferenceUrl")));

		return existing;
	}

	private Map<String, Object> result(Announcement deleted, String userString) {
		log.info("Announcement at {} scope withdrawn by {}", deleted.getScopeType(), userString);

		return Map.of("success", true,
			"message", "Announcement withdrawn");
	}
}
