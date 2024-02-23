package org.olf.dcb.core.api;

import java.util.UUID;

import org.olf.dcb.core.api.serde.LocationDTO;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@Controller("/locations")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Locations Api")
public class LocationController {

	private static final Logger log = LoggerFactory.getLogger(LocationController.class);

	private HostLmsRepository hostLmsRepository;
	private LocationRepository locationRepository;
	private AgencyRepository agencyRepository;

	public LocationController(LocationRepository locationRepository, AgencyRepository agencyRepository,
			HostLmsRepository hostLmsRepository) {
		this.locationRepository = locationRepository;
		this.agencyRepository = agencyRepository;
		this.hostLmsRepository = hostLmsRepository;
	}

	@Secured(SecurityRule.IS_ANONYMOUS)
	@Operation(summary = "Browse Locations", description = "Paginate through the list of known locations", parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100"),
			@Parameter(in = ParameterIn.QUERY, name = "type", description = "Location typefileter", schema = @Schema(type = "string"), example = "PICKUP") })
	@Get("/{?pageable*}")
	public Mono<Page<Location>> list(@Nullable @Parameter String type,
			@Parameter(hidden = true) @Valid Pageable pageable) {
		if (pageable == null) {
			pageable = Pageable.from(0, 100).order("name");
		}

		if ((type != null) && (type.length() > 0)) {
			return Mono.from(locationRepository.queryAllByType(type, pageable));
		}

		return Mono.from(locationRepository.queryAll(pageable));
	}

	@Get("/{id}")
	public Mono<Location> show(UUID id) {
		return Mono.from(locationRepository.findById(id));
	}

	// TODO: Convert return Location to LocationDTO
	@Post("/")
	public Mono<Location> postLocation(@Body LocationDTO location) {

		log.debug("postLocation {}", location.toString());

		// Look up any agency if given on the incoming DTO
		DataAgency agency = location.agency() != null ? Mono.from(agencyRepository.findById(location.agency())).block()
				: null;

		DataHostLms hostSystem = location.hostLms() != null
				? Mono.from(hostLmsRepository.findById(location.hostLms())).block()
				: null;

		// We allow HUB locations which are not attached to an agency
		if ((location.type().equals("HUB")) || (location.agency() != null) && (agency != null)) {
			// If we weren't given an host system, see if we can infer one from the agency.
			if ((hostSystem == null) && (agency != null))
				hostSystem = agency.getHostLms();

			// Convert AgencyDTO into DataAgency with correctly linked HostLMS
			Location l = Location.builder().id(location.id()).code(location.code()).name(location.name())
					.type(location.type()).agency(agency).hostSystem(hostSystem).isPickup(location.isPickup())
					.longitude(location.longitude()).latitude(location.latitude()).deliveryStops(location.deliveryStops())
					.printLabel(location.printLabel()).build();

			return Mono.from(locationRepository.existsById(l.getId()))
					.flatMap(exists -> Mono.fromDirect(exists ? locationRepository.update(l) : locationRepository.save(l)));
		} else {
			log.warn("Client upload a location {} with an unknown agency UUID", location);
			return Mono.empty();
		}
	}

}
