package org.olf.dcb.core.api;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.HttpResponse.serverError;
import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import java.time.Duration;
import java.util.UUID;

import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.item.availability.AvailabilityResponseView;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.olf.dcb.request.resolution.CannotFindClusterRecordException;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Controller
@Validated
@Secured(IS_ANONYMOUS)
@Tag(name = "Live Availability API")
public class LiveAvailabilityController {
	private final LiveAvailabilityService liveAvailabilityService;
	
	@Value("${dcb.live-availability.timeout:PT7S}")
	protected Duration timeout;

	public LiveAvailabilityController(LiveAvailabilityService liveAvailabilityService) {
		this.liveAvailabilityService = liveAvailabilityService;
	}

	@Operation(
		summary = "Live Item Availability",
		description = "Check Live Item Availability for a Bibliographic Record"
	)
	@SingleResult
	@Get(value = "/items/availability", produces = APPLICATION_JSON)
	public Mono<AvailabilityResponseView> getLiveAvailability(
		@NotNull @QueryValue("clusteredBibId") final UUID clusteredBibId,
		@Nullable @QueryValue("filters") final String filters) {
		log.info("REST, getLiveAvailability: {} {}", clusteredBibId, filters);

		return liveAvailabilityService.checkAvailability(clusteredBibId, timeout, ( filters != null ) ? filters : "all")
			.map(report -> AvailabilityResponseView.from(report, clusteredBibId));
	}

	@Error
	public HttpResponse<String> onCheckFailure(CannotFindClusterRecordException exception) {
		return badRequest(exception.getMessage());
	}

	@Error
	public HttpResponse<String> onUnknownHostLms(UnknownHostLmsException exception) {
		return serverError(exception.getMessage());
	}

}
