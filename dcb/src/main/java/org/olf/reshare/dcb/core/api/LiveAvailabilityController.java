package org.olf.reshare.dcb.core.api;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olf.reshare.dcb.item.availability.AvailabilityResponseView;
import org.olf.reshare.dcb.item.availability.Item;
import org.olf.reshare.dcb.item.availability.LiveAvailabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.validation.constraints.NotNull;
import java.util.List;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

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
		@NotNull @QueryValue("systemCode") final String systemCode) {

		log.debug("REST, getLiveAvailability: {}, {}", bibRecordId, systemCode);

		return liveAvailabilityService.getAvailableItems(bibRecordId, systemCode)
			.map(itemList -> mapToResponse(itemList, bibRecordId, systemCode))
			.map(HttpResponse::ok);
	}
	private AvailabilityResponseView mapToResponse(List<Item> itemList, String bibRecordId, String systemCode) {
		log.debug("AvailabilityResponseView: {}, {}, {}", itemList, bibRecordId, systemCode);
		return new AvailabilityResponseView(itemList, bibRecordId, systemCode);
	}
}
