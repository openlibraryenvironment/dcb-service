package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.StringUtils.hasText;
import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.VolumeDesignation;
import org.olf.dcb.security.StaffClients;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Refuses a request a patron places for a title, and volume, they already have an active request
 * for: in flight or on loan, and still tracked, and placed within the active window when one is
 * set. Staff are never refused, so a request this refuses can always be placed by a library. A
 * request for a chosen copy is never refused either: a patron who picks a different copy of a
 * title they already have on request means to.
 */
@Slf4j
@Singleton
@Requires(property = "dcb.requests.preflight-checks.duplicate-requests.enabled", defaultValue = "false", notEquals = "false")
public class PreventDuplicateRequestsPreFlightCheck implements PreflightCheck {
	public static final String DUPLICATE_REQUEST_ATTEMPT = "DUPLICATE_REQUEST_ATTEMPT";

	private final PatronRequestRepository patronRequestRepository;
	private final IntMessageService intMessageService;
	private final StaffClients staffClients;
	@Nullable private final Duration activeWindow;
	@Nullable private final String removedRequestWindow;

	public PreventDuplicateRequestsPreFlightCheck(PatronRequestRepository patronRequestRepository,
		IntMessageService intMessageService, StaffClients staffClients,
		@Value("${dcb.requests.preflight-checks.duplicate-requests.active-window:}") @Nullable Duration activeWindow,
		@Value("${dcb.requests.preflight-checks.duplicate-requests.request-window:}") @Nullable String removedRequestWindow) {

		if (activeWindow != null && (activeWindow.isZero() || activeWindow.isNegative())) {
			throw new IllegalArgumentException(
				"dcb.requests.preflight-checks.duplicate-requests.active-window must be positive, or unset to "
					+ "refuse for as long as the earlier request is active: " + activeWindow);
		}

		this.patronRequestRepository = patronRequestRepository;
		this.intMessageService = intMessageService;
		this.staffClients = staffClients;
		this.activeWindow = activeWindow;
		this.removedRequestWindow = removedRequestWindow;
	}

	@PostConstruct
	void announce() {
		if (hasText(removedRequestWindow)) {
			log.warn("dcb.requests.preflight-checks.duplicate-requests.request-window is no longer read; "
				+ "set active-window to limit the check to recent requests");
		}

		if (!staffClients.isConfigured()) {
			log.warn("Duplicate request check is OFF: dcb.requests.staff-client-ids is empty, so staff "
				+ "could not place a request it refused");
		}
		else {
			log.info("Duplicate request check is ON ({}); requests placed through clients {} are staff's",
				activeWindow == null
					? "while the earlier request is active"
					: "active requests placed within " + activeWindow,
				staffClients.clientIds());
		}
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var hostLmsCode = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalSystemCode);
		final var patronLocalId = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalId);
		final var citation = getValueOrNull(command, PlacePatronRequestCommand::getCitation);
		final var bibClusterId = getValueOrNull(citation, PlacePatronRequestCommand.Citation::getBibClusterId);
		final var requestedVolume = VolumeDesignation.normalise(
			getValueOrNull(citation, PlacePatronRequestCommand.Citation::getVolumeDesignator));
		final var chosenCopy = getValueOrNull(command, PlacePatronRequestCommand::getItem,
			PlacePatronRequestCommand.Item::getLocalId);

		if (!staffClients.isConfigured() || Boolean.TRUE.equals(command.getIsPlacedByStaff())
			|| hasText(chosenCopy) || hostLmsCode == null || patronLocalId == null || bibClusterId == null) {

			return Mono.just(List.of(passed()));
		}

		final var placedSince = activeWindow == null ? null : Instant.now().minus(activeWindow);

		return Flux.from(patronRequestRepository.findActiveRequestsForPatronAndTitle(hostLmsCode, patronLocalId,
				bibClusterId, Status.ACTIVE_STATE_CODES, placedSince))
			.filter(existing -> Objects.equals(requestedVolume,
				VolumeDesignation.normalise(existing.requestedVolumeDesignation())))
			.next()
			.map(existing -> failedUm(DUPLICATE_REQUEST_ATTEMPT,
				"The patron already has active request %s for this title%s".formatted(existing.id(),
					requestedVolume == null ? "" : " and volume"),
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
