package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failedUm;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import io.micronaut.context.annotation.Value;

import java.util.List;

import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.IntMessageService;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.AgencyRepository;


/**
 * Observe any global limits for this patron. Initially just check the gobal max active reqest counter.
 */
@Slf4j
@Singleton
@Requires(property = "dcb.requests.preflight-checks.global-limits.enabled", defaultValue = "true", notEquals = "false")
public class GlobalLimitsPreflightCheck implements PreflightCheck {

	private final Long globalActiveRequestLimit;
	private final PatronRequestRepository patronRequestRepository;
  private final IntMessageService intMessageService;
	private final AgencyRepository agencyRepository;

	public GlobalLimitsPreflightCheck(
		@Value("${dcb.globals.activeRequestLimit:25}") Long globalActiveRequestLimit,
		PatronRequestRepository patronRequestRepository,
    IntMessageService intMessageService,
		AgencyRepository agencyRepository) {

		this.globalActiveRequestLimit = globalActiveRequestLimit;
		this.patronRequestRepository = patronRequestRepository;
    this.intMessageService = intMessageService;
    this.agencyRepository = agencyRepository;
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

		String patronAgency = command.getRequestorAgencyCode();

		log.info("Checking that patron has < global setting for max active requests {} - patronAgency:{}",command,patronAgency);

		return Mono.from(patronRequestRepository.getActiveRequestCountForPatron(command.getRequestorLocalSystemCode(),command.getRequestorLocalId()))
			.flatMap( count -> {
				if ( globalActiveRequestLimit.intValue() == 0 )
					return Mono.just(passed("global request limit disabled"));

				if ( count.intValue() < globalActiveRequestLimit.intValue() ) {
					// return Mono.just(passed("Current active requests "+count+" < "+globalActiveRequestLimit));
					return verifyAgencyLimit(patronAgency, count.intValue());
				}

				return Mono.just(failedUm("EXCEEDS_GLOBAL_LIMIT", 
					"Patron has more active requests than the system allows (%d)".formatted(globalActiveRequestLimit),
					intMessageService.getMessage("EXCEEDS_GLOBAL_LIMIT")
				));
			})
			.doOnError(e -> log.error("Unexpected error checking global limits",e))
			.onErrorResume( e -> Mono.just(passed("Passed with error "+e.getMessage() ) ) );
	}

	public Mono<CheckResult> verifyAgencyLimit(String agency, int count) {

		if ( ( agency == null ) || ( agency.trim().length() == 0 ) )
			return Mono.just(passed("Bypass agency max loans test"));

	
		log.info("Checking agency limits for {} {}",agency,count);
		return Mono.from(agencyRepository.findOneByCode(agency))
			.flatMap( agencyObj -> {
				if ( ( agencyObj.getMaxConsortialLoans() == null ) ||
					   ( count <= agencyObj.getMaxConsortialLoans().intValue() ) ) {
					return Mono.just(passed("Global and local limits OK"));
				}
				else {
					return Mono.just(failedUm("EXCEEDS_AGENCY_LIMIT",
	          "Patron has more active requests than the Agency (%s) allows (%d)".formatted(agency, count),
		        intMessageService.getMessage("EXCEEDS_AGENCY_LIMIT")));
				}
			})
			.onErrorResume(e -> Mono.just(failedUm("EXCEEDS_AGENCY_LIMIT",
          "Patron has more active requests than the Agency (%s) allows (%d)".formatted(agency, count),
          intMessageService.getMessage("EXCEEDS_AGNECY_LIMIT"))));

	}
}
