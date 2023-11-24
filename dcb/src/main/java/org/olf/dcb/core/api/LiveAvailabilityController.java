package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.UUID;

import org.olf.dcb.item.availability.AvailabilityResponseView;
import org.olf.dcb.item.availability.LiveAvailabilityService;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Validated
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller
@Tag(name = "Live Availability API")
public class LiveAvailabilityController {
	private final LiveAvailabilityService liveAvailabilityService;

	public LiveAvailabilityController(LiveAvailabilityService liveAvailabilityService) {
		this.liveAvailabilityService = liveAvailabilityService;
	}

	@Operation(
		summary = "Live Item Availability",
		description = "Check Live Item Availability for a Bibliographic Record"
	)
	@SingleResult
	@Get(value = "/items/availability", produces = APPLICATION_JSON)
	public Mono<HttpResponse<AvailabilityResponseView>> getLiveAvailability(
		@NotNull @QueryValue("clusteredBibId") final UUID clusteredBibId) {

		log.info("REST, getLiveAvailability: {}", clusteredBibId);

		return liveAvailabilityService.getAvailableItems(clusteredBibId)
			.map(report -> AvailabilityResponseView.from(report, clusteredBibId))
			.map(HttpResponse::ok);
	}
}
