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
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.item.availability.AvailabilityResponseView;
import org.olf.reshare.dcb.item.availability.LiveAvailabilityService;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;
import org.olf.reshare.dcb.request.resolution.SharedIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.micronaut.http.MediaType.APPLICATION_JSON;

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

	@SingleResult
	@Get(value = "/items/availability", produces = APPLICATION_JSON)
	public Mono<HttpResponse<AvailabilityResponseView>> getLiveAvailability(
		@NotNull @QueryValue("clusteredBibId") final UUID clusteredBibId) {

		log.debug("REST, getLiveAvailability: {}", clusteredBibId);

		return sharedIndexService.findClusteredBib(clusteredBibId)
			.flatMap(this::getAvailableItemsOrEmptyList)
			.map(items -> AvailabilityResponseView.from(items, clusteredBibId))
			.map(HttpResponse::ok);
	}

	private Mono<List<Item>> getAvailableItemsOrEmptyList(ClusteredBib clusteredBib) {

		log.debug("getAvailableItemsOrEmptyList: {}", clusteredBib);

		return Mono.just(clusteredBib)
			// don't call service if bibs empty
			.flatMap(clusteredRecord -> clusteredRecord.getBibs().isEmpty() ?
				Mono.just(Collections.emptyList()) : liveAvailabilityService.getAvailableItems(clusteredRecord));
	}
}
