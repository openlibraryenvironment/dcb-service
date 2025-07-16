package org.olf.dcb.request.fulfilment;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.utils.PropertyAccessUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

@Singleton
@Requires(property = "dcb.requests.preflight-checks.duplicate-requests.enabled", defaultValue = "false", notEquals = "false")
public class PreventDuplicateRequestsPreFlightCheck implements PreflightCheck {
	public static final String DUPLICATE_REQUEST_ATTEMPT = "DUPLICATE_REQUEST_ATTEMPT";
	private final PatronRequestRepository patronRequestRepository;
	private final Integer requestWindowInSeconds;
  private final IntMessageService intMessageService;



	public PreventDuplicateRequestsPreFlightCheck(
		@Value("${dcb.requests.preflight-checks.duplicate-requests.request-window:900}") Integer requestWindow,
		PatronRequestRepository patronRequestRepository,
    IntMessageService intMessageService) {
		this.requestWindowInSeconds = requestWindow;
		this.patronRequestRepository = patronRequestRepository;
    this.intMessageService = intMessageService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final Instant thisRequestInstant = Instant.now();
		final String thisPickupLocationCode = getValueOrNull(command, PlacePatronRequestCommand::getPickupLocationCode);
		final String thisHostLmsCode = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalSystemCode);
		final String thisPatronLocalId = getValueOrNull(command, PlacePatronRequestCommand::getRequestorLocalId);
		final UUID thisBibClusterId = getValueOrNull(command, PlacePatronRequestCommand::getCitation).getBibClusterId();

		return checkRequestFor(thisHostLmsCode, thisPatronLocalId, thisBibClusterId, thisRequestInstant)
			.map( result(thisPickupLocationCode, thisHostLmsCode, thisPatronLocalId, thisBibClusterId) )
			.map(List::of);
	}

	private Function<List<PatronRequest>, CheckResult> result(
		String thisPickupLocationCode, String thisHostLmsCode, String thisPatronLocalId, UUID thisBibClusterId)
	{
		return list -> list.isEmpty() ?
			passed() : checkFailed(thisPickupLocationCode, thisHostLmsCode, thisPatronLocalId, thisBibClusterId);
	}

	private Mono<List<PatronRequest>> checkRequestFor(
		String thisHostLmsCode, String thisPatronLocalId, UUID thisBibClusterId, Instant thisRequestInstant)
	{
		return fetchMatchedPatronRequestsFor(thisHostLmsCode, thisBibClusterId)
			.filter(lastMatchedPatronRequest -> isMatching(thisPatronLocalId, thisRequestInstant, lastMatchedPatronRequest))
			.collectList();
	}

	private Boolean isMatching(
		String thisPatronLocalId, Instant thisRequestInstant, PatronRequest lastMatchedPatronRequest)
	{
		return lastMatchedPatronRequest.getRequestingIdentity().getLocalId().equals(thisPatronLocalId) &&
			// Check that the Instant of this request
			// is within the request window of the last matching request
			// which means we caught a preventable request
			thisRequestInstant.isBefore(lastMatchedPatronRequest.getDateCreated().plusSeconds(requestWindowInSeconds));
	}

	private CheckResult checkFailed(
		String pickupLocationCode, String hostLmsCode, String patronLocalId, UUID bibClusterId)
	{
		return failedUm(DUPLICATE_REQUEST_ATTEMPT,
			"A request already exists for Patron " + patronLocalId
				+ " at " + hostLmsCode
				+ " against " + bibClusterId
				+ " to be picked up at " + pickupLocationCode
				+ " within the time window of " + requestWindowInSeconds + " seconds.",
			intMessageService.getMessage(DUPLICATE_REQUEST_ATTEMPT)
			);
	}
	private Flux<PatronRequest> fetchMatchedPatronRequestsFor(String hostLmsCode, UUID bibClusterId) {
		return Flux.from(patronRequestRepository
			.findAllByPatronHostlmsCodeAndBibClusterIdOrderByDateCreatedDesc(hostLmsCode, bibClusterId));
	}
}
