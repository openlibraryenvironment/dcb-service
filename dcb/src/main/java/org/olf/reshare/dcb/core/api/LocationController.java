package org.olf.reshare.dcb.core.api;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.reshare.dcb.core.model.BibRecord;
import org.olf.reshare.dcb.core.model.Location;
import org.olf.reshare.dcb.ingest.IngestSource;
import org.reactivestreams.Publisher;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;

@Controller("/locations")
@Tag(name = "Locations")
@Secured(SecurityRule.IS_ANONYMOUS)
public class LocationController {

	private static Location decorateWithUUID(Location l) {

		l.setId(UUIDUtils.nameUUIDFromNamespaceAndString(LOCATION_NS, l.getCode().toLowerCase()));
		return l;
	}

	private final List<Location> LOCATIONS_TEMP;

	private final static UUID LOCATION_NS = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
			Location.class.getSimpleName());

	LocationController() {
		LOCATIONS_TEMP = Stream.of(new String[][] {
				{ "apl", "Altoona (IA) Public" }, { "klb", "ATSU" }, { "avcir", "Avila University, Library Desk" },
				{ "bplg6", "Bettendorf Public Library" }, { "c1b", "Conception Abbey Library" },
				{ "ch", "Children's Library,CALS-Children's Library" }, { "db", "Dee Brown" }, { "fl", "Fletcher" },
				{ "lr", "Main Library" }, { "ma", "Maumelle" }, { "mb", "Millie Brooks" }, { "mm", "McMath" },
				{ "nx", "Nixon" }, { "ok", "Rooker" }, { "pe", "Milam" }, { "sh", "Sanders" }, { "te", "Terry" },
				{ "th", "Thompson" }, { "wm", "Williams" }
		})
			.filter(data -> data.length > 0)
		  .map(data -> {
					final Location l = new Location();
					l.setCode(data[0]);
					l.setName(data[1]);
					return l;
			})
			.map(LocationController::decorateWithUUID)
			.collect(Collectors.toUnmodifiableList());
	}

	
	@SingleResult
	@Operation(
		summary = "Fetch all locations"
	)
  @Get("/")
	Publisher<Page<Location>> list() {
		return Mono.just(
				Page.of(
						LOCATIONS_TEMP,
						Pageable.from(1, LOCATIONS_TEMP.size()),
						LOCATIONS_TEMP.size()));
	}
}
