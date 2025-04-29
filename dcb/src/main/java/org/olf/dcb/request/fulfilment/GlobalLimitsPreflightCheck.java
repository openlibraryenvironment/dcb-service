package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import io.micronaut.context.annotation.Value;

import java.util.List;

import org.olf.dcb.core.svc.LocationService;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import org.olf.dcb.storage.PatronRequestRepository;


/**
 * Observe any global limits for this patron. Initially just check the gobal max active reqest counter.
 */
@Slf4j
@Singleton
@Requires(property = "dcb.requests.preflight-checks.global-limits.enabled", defaultValue = "true", notEquals = "false")
public class GlobalLimitsPreflightCheck implements PreflightCheck {

	private final Long globalActiveRequestLimit;
	private final PatronRequestRepository patronRequestRepository;

	public GlobalLimitsPreflightCheck(
		@Value("${dcb.globals.activeRequestLimit:25}") Long globalActiveRequestLimit,
		PatronRequestRepository patronRequestRepository) {

		this.globalActiveRequestLimit = globalActiveRequestLimit;
		this.patronRequestRepository = patronRequestRepository;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var pickupLocationCode = getValueOrNull(command, PlacePatronRequestCommand::getPickupLocationCode);

		return checkGlobalActiveRequestLimit(command)
			.map(List::of);
	}

	/**
	 * Count the number of active patron requests and reject if the patron has more than the global limit
   */
	public Mono<CheckResult> checkGlobalActiveRequestLimit(PlacePatronRequestCommand command) {
		log.info("Checking that patron has < global setting for max active requests {}",command);

		return Mono.from(patronRequestRepository.getCountForHostLms(command.getRequestorLocalSystemCode(),command.getRequestorLocalId()))
			.flatMap( count -> {
				if ( globalActiveRequestLimit.intValue() == 0 )
					return Mono.just(passed("global request limit disabled"));

				if ( count.intValue() < globalActiveRequestLimit.intValue() )
					return Mono.just(passed("Current active requests "+count+" < "+globalActiveRequestLimit));

				return Mono.just(failed("EXCEEDS_GLOBA_LIMIT", "Patron has more active requests than the system allows (%d)".formatted(globalActiveRequestLimit)));
			})
			.doOnError(e -> log.error("Unexpected error checking global limits",e))
			.onErrorResume( e -> Mono.just(passed("Passed with error "+e.getMessage() ) ) );
	}
}
