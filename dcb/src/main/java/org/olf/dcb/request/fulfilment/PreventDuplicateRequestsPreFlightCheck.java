package org.olf.dcb.request.fulfilment;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

@Singleton
@Requires(property = "dcb.requests.preflight-checks.duplicate-requests.enabled", defaultValue = "true", notEquals = "false")
public class PreventDuplicateRequestsPreFlightCheck implements PreflightCheck {
	public static final String DUPLICATE_REQUEST_ATTEMPT = "DUPLICATE_REQUEST_ATTEMPT";
	private final PatronRequestRepository patronRequestRepository;
	private final Integer requestWindowInSeconds;

	public PreventDuplicateRequestsPreFlightCheck(
		@Value("${dcb.requests.preflight-checks.duplicate-requests.request-window:900}") Integer requestWindow,
		PatronRequestRepository patronRequestRepository)
	{
		this.requestWindowInSeconds = requestWindow;
		this.patronRequestRepository = patronRequestRepository;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final String pickupLocationCode = getValue(command, PlacePatronRequestCommand::getPickupLocationCode);
		final String hostLmsCode = getValue(command, PlacePatronRequestCommand::getRequestorLocalSystemCode);
		final String patronLocalId = getValue(command, PlacePatronRequestCommand::getRequestorLocalId);
		final UUID bibClusterId = getValue(command, PlacePatronRequestCommand::getCitation).getBibClusterId();
		final Instant requestWindowMatcher = Instant.now().plusSeconds(requestWindowInSeconds);

		return checkRequestFor(hostLmsCode, patronLocalId, bibClusterId, requestWindowMatcher)
			.map( result(pickupLocationCode, hostLmsCode, patronLocalId, bibClusterId) )
			.map(List::of);
	}

	private Function<List<PatronRequest>, CheckResult> result(
		String pickupLocationCode, String hostLmsCode, String patronLocalId, UUID bibClusterId)
	{
		return list -> list.isEmpty() ? passed() : checkFailed(pickupLocationCode, hostLmsCode, patronLocalId, bibClusterId);
	}

	private Mono<List<PatronRequest>> checkRequestFor(
		String hostLmsCode, String patronLocalId, UUID bibClusterId, Instant requestWindowMatcher)
	{
		return fetchMatchedPatronRequestsFor(hostLmsCode, bibClusterId)
			.filter(patronRequest -> isMatching(patronLocalId, requestWindowMatcher, patronRequest))
			.collectList();
	}

	private Boolean isMatching(
		String patronLocalId, Instant requestWindowMatcher, PatronRequest patronRequest)
	{
		return patronRequest.getRequestingIdentity().getLocalId().equals(patronLocalId) &&
			patronRequest.getDateCreated().isBefore(requestWindowMatcher);
	}

	private CheckResult checkFailed(
		String pickupLocationCode, String hostLmsCode, String patronLocalId, UUID bibClusterId)
	{
		return failed(DUPLICATE_REQUEST_ATTEMPT,
			"A request already exists for Patron " + patronLocalId
				+ " at " + hostLmsCode
				+ " against " + bibClusterId
				+ " to be picked up at " + pickupLocationCode
				+ " within the time window of " + requestWindowInSeconds + " seconds.");
	}
	private Flux<PatronRequest> fetchMatchedPatronRequestsFor(String hostLmsCode, UUID bibClusterId) {
		return Flux.from(patronRequestRepository
			.findAllByPatronHostlmsCodeAndBibClusterIdOrderByDateCreatedDesc(hostLmsCode, bibClusterId));
	}
}
