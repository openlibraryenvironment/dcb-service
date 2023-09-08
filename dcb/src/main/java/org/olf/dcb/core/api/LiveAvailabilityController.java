package org.olf.dcb.core.api;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.item.availability.AvailabilityReport.emptyReport;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.AvailabilityResponseView;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.olf.dcb.request.resolution.ClusteredBib;
import org.olf.dcb.request.resolution.SharedIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller
@Tag(name = "Live Availability API")
public class LiveAvailabilityController {
	private static final Logger log = LoggerFactory.getLogger(LiveAvailabilityController.class);

	private final LiveAvailabilityService liveAvailabilityService;
	private final SharedIndexService sharedIndexService;

	public LiveAvailabilityController(LiveAvailabilityService liveAvailabilityService,
		SharedIndexService sharedIndexService) {
		this.liveAvailabilityService = liveAvailabilityService;
		this.sharedIndexService = sharedIndexService;
	}

	@Operation(
		summary = "Live Item Availability",
		description = "Check Live Item Availability for a Bibliographic Record"
	)
	@SingleResult
	@Get(value = "/items/availability", produces = APPLICATION_JSON)
	public Mono<HttpResponse<AvailabilityResponseView>> getLiveAvailability(
		@NotNull @QueryValue("clusteredBibId") final UUID clusteredBibId) {

		log.debug("REST, getLiveAvailability: {}", clusteredBibId);

		return sharedIndexService.findClusteredBib(clusteredBibId)
			.flatMap(this::getAvailabilityReport)
			.map(report -> AvailabilityResponseView.from(report, clusteredBibId))
			.map(HttpResponse::ok);
	}

	private Mono<AvailabilityReport> getAvailabilityReport(ClusteredBib clusteredBib) {
		log.debug("getAvailabilityReport: {}", clusteredBib);

		return Mono.just(clusteredBib)
			// don't call service if bibs empty
			.flatMap(clusteredRecord -> isEmpty(clusteredRecord.getBibs())
				? Mono.just(emptyReport())
				: liveAvailabilityService.getAvailableItems(clusteredRecord));
	}
}
