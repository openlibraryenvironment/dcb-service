package org.olf.reshare.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.item.availability.AvailabilityResponseView;
import org.olf.reshare.dcb.item.availability.Item;
import org.olf.reshare.dcb.item.availability.LiveAvailabilityService;
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

	public LiveAvailabilityController(LiveAvailabilityService liveAvailabilityService) {
		this.liveAvailabilityService = liveAvailabilityService;
	}

	@SingleResult
	@Get(value = "/items/availability", produces = APPLICATION_JSON)
	public Mono<HttpResponse<AvailabilityResponseView>> getLiveAvailability(
		@NotNull @QueryValue("bibRecordId") final String bibRecordId,
		@NotNull @QueryValue("hostLmsCode") final String hostLmsCode) {

		log.debug("REST, getLiveAvailability: {}, {}", bibRecordId, hostLmsCode);

		return liveAvailabilityService.getAvailableItems(bibRecordId, hostLmsCode)
			.map(itemList -> mapToResponse(itemList, bibRecordId, hostLmsCode))
			.map(HttpResponse::ok);
	}

	private AvailabilityResponseView mapToResponse(List<Item> itemList,
		String bibRecordId, String hostLmsCode) {

		final var view = new AvailabilityResponseView(itemList, bibRecordId, hostLmsCode);

		log.debug("mapToResponse: {}", view);

		return view;
	}
}
