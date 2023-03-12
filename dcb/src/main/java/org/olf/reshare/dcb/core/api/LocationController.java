package org.olf.reshare.dcb.core.api;

import static org.olf.reshare.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
import javax.validation.Valid;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.olf.reshare.dcb.core.model.Location;

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
import services.k_int.utils.UUIDUtils;
import org.olf.reshare.dcb.core.api.types.LocationDTO;
import org.olf.reshare.dcb.storage.LocationRepository;


@Controller("/locations")
@Tag(name = "Locations")
@Secured(SecurityRule.IS_ANONYMOUS)
public class LocationController {

        private LocationRepository locationRepository;

        /*
	private static Location decorateWithUUID(Location l) {

		l.setId(UUIDUtils.nameUUIDFromNamespaceAndString(LOCATION_NS, l.getCode().toLowerCase()));
		return l;
	}

	private final List<Location> LOCATIONS_TEMP;


	private final static UUID LOCATION_NS = UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB,
		Location.class.getSimpleName());
        */

	public LocationController(LocationRepository locationRepository) {
		this.locationRepository = locationRepository;
		/*
		LOCATIONS_TEMP = Stream.of(new String[][]{
				{"apl", "Altoona (IA) Public"}, {"klb", "ATSU"}, {"avcir", "Avila University, Library Desk"},
				{"bplg6", "Bettendorf Public Library"}, {"c1b", "Conception Abbey Library"},
				{"ch", "Children's Library,CALS-Children's Library"}, {"db", "Dee Brown"}, {"fl", "Fletcher"},
				{"lr", "Main Library"}, {"ma", "Maumelle"}, {"mb", "Millie Brooks"}, {"mm", "McMath"},
				{"nx", "Nixon"}, {"ok", "Rooker"}, {"pe", "Milam"}, {"sh", "Sanders"}, {"te", "Terry"},
				{"th", "Thompson"}, {"wm", "Williams"}
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
		*/
	}

        @Operation(
                summary = "Browse Locations",
                description = "Paginate through the list of known locations",
                parameters = {
                        @Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
                        @Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
        )
        @Get("/{?pageable*}")
        public Mono<Page<Location>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
                if (pageable == null) {
                        pageable = Pageable.from(0, 100);
                }

                return Mono.from(locationRepository.findAll(pageable));
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

                // Convert AgencyDTO into DataAgency with correctly linked HostLMS
                Location l = new Location(location.id(),
                                          location.code(),
                                          location.name());

                return Mono.from(locationRepository.existsById(l.getId()))
                       .flatMap(exists -> Mono.fromDirect(exists ? locationRepository.update(l) : locationRepository.save(l)));
        }

}
