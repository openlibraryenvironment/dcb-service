package org.olf.dcb.core.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.olf.dcb.core.api.serde.LocationDTO;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.LocationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;
import io.micronaut.core.annotation.Nullable;

@Controller("/locations")
@Tag(name = "Locations")
@Secured({ "ADMIN" })
public class LocationController {

        private static final Logger log = LoggerFactory.getLogger(LocationController.class);

        private LocationRepository locationRepository;
        private AgencyRepository agencyRepository;

        /*
	private static Location decorateWithUUID(Location l) {

		l.setId(UUIDUtils.nameUUIDFromNamespaceAndString(LOCATION_NS, l.getCode().toLowerCase()));
		return l;
	}

	private final List<Location> LOCATIONS_TEMP;


	private final static UUID LOCATION_NS = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
		Location.class.getSimpleName());
        */


	public LocationController(LocationRepository locationRepository,
                                  AgencyRepository agencyRepository) {
		this.locationRepository = locationRepository;
		this.agencyRepository = agencyRepository;
	}

        @Secured(SecurityRule.IS_ANONYMOUS)
        @Operation(
                summary = "Browse Locations",
                description = "Paginate through the list of known locations",
                parameters = {
                        @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                        @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100"),
                        @Parameter(in = ParameterIn.QUERY, name = "type", description = "Location typefileter", schema = @Schema(type = "string"), example = "PICKUP")}
        )
        @Get("/{?pageable*}")
        public Mono<Page<Location>> list(@Nullable @Parameter String type, @Parameter(hidden = true) @Valid Pageable pageable) {
                if (pageable == null) {
                        pageable = Pageable.from(0, 100)
                                .order("name");
                }

                if ( ( type != null ) && ( type.length() > 0 ) ) {
                  return Mono.from(locationRepository.queryAllByType(type,pageable));
                }

                return Mono.from(locationRepository.queryAll(pageable));
        }

        @Get("/{id}")
        public Mono<Location> show(UUID id) {
                return Mono.from(locationRepository.findById(id));
        }


        /*
	@Operation(
		summary = "Fetch all locations"
	)
	@Get("/")
	Mono<Page<Location>> list() {
		return Mono.just(
			Page.of(
				LOCATIONS_TEMP,
				Pageable.from(1, LOCATIONS_TEMP.size()),
				LOCATIONS_TEMP.size()));
	}
        */

        // TODO: Convert return Location to LocationDTO
        @Post("/")
        public Mono<Location> postLocation(@Body LocationDTO location) {

                log.debug("postLocation {}",location.toString());

                // Look up any agency if given on the incoming DTO
                DataAgency agency = location.agency() != null ? Mono.from(agencyRepository.findById(location.agency())).block() : null;

                log.debug("Creating location with agency {}",agency);

                // Convert AgencyDTO into DataAgency with correctly linked HostLMS
                Location l = Location.builder()
                                    .id(location.id())
                                    .code(location.code())
                                    .name(location.name())
                                    .type(location.type())
                                    .agency(agency)
                                    .isPickup(location.isPickup())
                                    .longitude(location.longitude())
                                    .latitude(location.latitude())
                                    .build();

                return Mono.from(locationRepository.existsById(l.getId()))
                       .flatMap(exists -> Mono.fromDirect(exists ? locationRepository.update(l) : locationRepository.save(l)));
        }

}
