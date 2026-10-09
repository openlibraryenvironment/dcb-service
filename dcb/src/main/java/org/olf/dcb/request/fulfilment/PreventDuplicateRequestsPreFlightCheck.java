package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.StringUtils.hasText;
import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.List;

import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.security.StaffClients;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Refuses a request a patron places for a title, and volume, they already have an active request
 * for: in flight or on loan, and still tracked. Staff are never refused, so a request this refuses
 * can always be placed by a library. A request for a chosen copy is never refused either: a patron
 * who picks a different copy of a title they already have on request means to.
 */
@Slf4j
@Singleton
@AllArgsConstructor
@Requires(property = "dcb.requests.preflight-checks.duplicate-requests.enabled", defaultValue = "false", notEquals = "false")
public class PreventDuplicateRequestsPreFlightCheck implements PreflightCheck {
	public static final String DUPLICATE_REQUEST_ATTEMPT = "DUPLICATE_REQUEST_ATTEMPT";

	private final PatronRequestRepository patronRequestRepository;
	private final IntMessageService intMessageService;
	private final StaffClients staffClients;

	@PostConstruct
	void announce() {
		if (!staffClients.isConfigured()) {
			log.warn("Duplicate request check is OFF: dcb.requests.staff-client-ids is not set, so staff "
				+ "could not place a request it refused");
		}
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var hostLmsCode = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalSystemCode);
		final var patronLocalId = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalId);
		final var citation = getValueOrNull(command, PlacePatronRequestCommand::getCitation);
		final var bibClusterId = getValueOrNull(citation, PlacePatronRequestCommand.Citation::getBibClusterId);
		final var volumeDesignator = getValueOrNull(citation, PlacePatronRequestCommand.Citation::getVolumeDesignator);
		final var chosenCopy = getValueOrNull(command, PlacePatronRequestCommand::getItem,
			PlacePatronRequestCommand.Item::getLocalId);

		// Resolution does not yet choose a copy by volume, so a volume request cannot be told
		// from a request for another volume of the same set: exempt until it can
		if (!staffClients.isConfigured() || Boolean.TRUE.equals(command.getIsPlacedByStaff())
			|| hasText(chosenCopy) || hasText(volumeDesignator)
			|| hostLmsCode == null || patronLocalId == null || bibClusterId == null) {

			return Mono.just(List.of(passed()));
		}

		return Mono.from(patronRequestRepository.findActiveRequestForPatronAndTitle(hostLmsCode, patronLocalId,
				bibClusterId, null, Status.ACTIVE_STATE_CODES))
			.map(existing -> failedUm(DUPLICATE_REQUEST_ATTEMPT,
				"The patron already has active request %s for this title".formatted(existing),
				intMessageService.getMessage(DUPLICATE_REQUEST_ATTEMPT)))
			.defaultIfEmpty(passed())
			// Not being able to read the patron's requests is no evidence of a duplicate
			.onErrorResume(error -> {
				log.warn("Unable to check for a duplicate of a request at {} for cluster {} ({}), passing it",
					hostLmsCode, bibClusterId, error.toString());

				return Mono.just(passed());
			})
			.map(List::of);
	}
}
