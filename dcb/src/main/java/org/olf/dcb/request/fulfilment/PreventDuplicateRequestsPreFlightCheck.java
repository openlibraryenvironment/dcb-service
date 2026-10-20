package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.StringUtils.hasText;
import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.VolumeDesignation;
import org.olf.dcb.security.StaffClients;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Refuses a request a patron places for a title, and volume, they already have an active request
 * for: in flight or on loan, and still tracked, and placed within the active window when one is
 * set. Staff are never refused, so a request this refuses can always be placed by a library. A
 * request for a chosen copy is refused only when the patron already has that same copy coming: a
 * patron who picks a different copy of a title they already have on request means to. On a
 * multi-volume title a request without a volume is never refused, since the title is not one thing.
 */
@Slf4j
// Created at startup when enabled, so a blank window stops the application, not the first placement
@Context
@Requires(property = "dcb.requests.preflight-checks.duplicate-requests.enabled", defaultValue = "false", notEquals = "false")
public class PreventDuplicateRequestsPreFlightCheck implements PreflightCheck {
	public static final String DUPLICATE_REQUEST_ATTEMPT = "DUPLICATE_REQUEST_ATTEMPT";

	// Until the borrower hold is placed, a request carries its chosen copy in its own item fields
	private static final List<String> BEFORE_BORROWER_PLACEMENT = Stream.of(Status.SUBMITTED_TO_DCB,
		Status.PATRON_VERIFIED, Status.RESOLVED, Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY, Status.CONFIRMED)
		.map(Enum::name)
		.toList();

	private static final String ACTIVE_WINDOW = "dcb.requests.preflight-checks.duplicate-requests.active-window";
	private static final String REMOVED_REQUEST_WINDOW = "dcb.requests.preflight-checks.duplicate-requests.request-window";

	private final PatronRequestRepository patronRequestRepository;
	private final BibRepository bibRepository;
	private final IntMessageService intMessageService;
	private final StaffClients staffClients;
	@Nullable private final Duration activeWindow;
	@Nullable private final String removedRequestWindow;

	public PreventDuplicateRequestsPreFlightCheck(PatronRequestRepository patronRequestRepository,
		BibRepository bibRepository, IntMessageService intMessageService, StaffClients staffClients,
		@Property(name = ACTIVE_WINDOW) @Nullable Duration activeWindow,
		// The same key as text: a blank value converts to no Duration, so the Duration alone reads as unset
		@Property(name = ACTIVE_WINDOW) @Nullable String activeWindowSetting,
		@Property(name = REMOVED_REQUEST_WINDOW) @Nullable String removedRequestWindow) {

		if (activeWindowSetting != null && activeWindowSetting.isBlank()) {
			throw new IllegalArgumentException(ACTIVE_WINDOW + " is set but blank. Remove it to refuse for as long "
				+ "as the earlier request is active, or set a positive duration such as 24h");
		}

		if (activeWindow != null && (activeWindow.isZero() || activeWindow.isNegative())) {
			throw new IllegalArgumentException(
				ACTIVE_WINDOW + " must be positive, or unset to "
					+ "refuse for as long as the earlier request is active: " + activeWindow);
		}

		this.patronRequestRepository = patronRequestRepository;
		this.bibRepository = bibRepository;
		this.intMessageService = intMessageService;
		this.staffClients = staffClients;
		this.activeWindow = activeWindow;
		this.removedRequestWindow = removedRequestWindow;
	}

	@PostConstruct
	void announce() {
		if (removedRequestWindow != null) {
			log.warn(REMOVED_REQUEST_WINDOW + " is no longer read; "
				+ "set active-window to limit the check to recent requests");
		}

		log.info("Duplicate request check is ON ({}); requests placed through clients {} are staff's",
			activeWindow == null
				? "while the earlier request is active"
				: "active requests placed within " + activeWindow,
			staffClients.clientIds());
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var hostLmsCode = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalSystemCode);
		final var patronLocalId = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalId);
		final var citation = getValueOrNull(command, PlacePatronRequestCommand::getCitation);
		final var bibClusterId = getValueOrNull(citation, PlacePatronRequestCommand.Citation::getBibClusterId);
		final var requestedVolume = VolumeDesignation.normalise(
			getValueOrNull(citation, PlacePatronRequestCommand.Citation::getVolumeDesignator));
		final var chosenCopy = getValueOrNull(command, PlacePatronRequestCommand::getItem);

		if (Boolean.TRUE.equals(command.getIsPlacedByStaff())
			|| hostLmsCode == null || patronLocalId == null) {

			return Mono.just(List.of(passed()));
		}

		final var placedSince = activeWindow == null ? null : Instant.now().minus(activeWindow);

		// The same rule as placement: a copy missing any of these is placed as a request for the title
		if (chosenCopy != null && hasText(chosenCopy.getLocalId()) && hasText(chosenCopy.getLocalSystemCode())
			&& hasText(chosenCopy.getAgencyCode())) {

			return refuseOnFirst(Mono.from(patronRequestRepository.findActiveRequestForPatronAndCopy(hostLmsCode,
					patronLocalId, chosenCopy.getLocalSystemCode(), chosenCopy.getLocalId(), Status.ACTIVE_STATE_CODES,
					BEFORE_BORROWER_PLACEMENT, placedSince)),
				"this copy", hostLmsCode);
		}

		if (bibClusterId == null) {
			return Mono.just(List.of(passed()));
		}

		return refuseOnFirst(Flux.from(patronRequestRepository.findActiveRequestsForPatronAndTitle(hostLmsCode,
					patronLocalId, bibClusterId, Status.ACTIVE_STATE_CODES, placedSince))
				.filter(existing -> Objects.equals(requestedVolume,
					VolumeDesignation.normalise(existing.requestedVolumeDesignation())))
				.map(PatronRequestRepository.ActiveTitleRequest::id)
				.next()
				// A multi-volume title is not one thing, so a request for it without a volume is no
				// duplicate of another: only the same copy or the same named volume is
				.filterWhen(existing -> requestedVolume != null
					? Mono.just(true)
					: isSingleVolume(bibClusterId)),
			requestedVolume == null ? "this title" : "this title and volume", hostLmsCode);
	}

	private Mono<Boolean> isSingleVolume(UUID bibClusterId) {
		return Flux.from(bibRepository.findPhysicalDescriptionsForCluster(bibClusterId))
			.any(VolumeDesignation::describesSeveralVolumes)
			.map(severalVolumes -> !severalVolumes);
	}

	private Mono<List<CheckResult>> refuseOnFirst(Mono<UUID> existingRequest, String what, String hostLmsCode) {
		return existingRequest
			.map(existing -> failedUm(DUPLICATE_REQUEST_ATTEMPT,
				"The patron already has active request %s for %s".formatted(existing, what),
				intMessageService.getMessage(DUPLICATE_REQUEST_ATTEMPT)))
			.defaultIfEmpty(passed())
			// Not being able to read the patron's requests is no evidence of a duplicate
			.onErrorResume(error -> {
				log.warn("Unable to check for a duplicate of a request for {} at {} ({}), passing it",
					what, hostLmsCode, error.toString());

				return Mono.just(passed());
			})
			.map(List::of);
	}
}
