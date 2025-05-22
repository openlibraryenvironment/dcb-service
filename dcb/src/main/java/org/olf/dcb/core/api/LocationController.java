package org.olf.dcb.core.api;

import java.util.UUID;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.security.utils.SecurityService;
import org.olf.dcb.core.api.exceptions.FileUploadValidationException;
import org.olf.dcb.core.api.serde.LocationDTO;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.security.RoleNames;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.utils.DCBConfigurationService;
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
import services.k_int.utils.UUIDUtils;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static io.micronaut.http.MediaType.MULTIPART_FORM_DATA;

@Controller("/locations")
@Validated
@Secured(RoleNames.ADMINISTRATOR)
@Tag(name = "Locations Api")
public class LocationController {

	private static final Logger log = LoggerFactory.getLogger(LocationController.class);

	private HostLmsRepository hostLmsRepository;
	private LocationRepository locationRepository;
	private AgencyRepository agencyRepository;

	private DCBConfigurationService configurationService;

	private final SecurityService securityService;
	public LocationController(LocationRepository locationRepository, AgencyRepository agencyRepository,
			HostLmsRepository hostLmsRepository, DCBConfigurationService configurationService, SecurityService securityService) {
		this.locationRepository = locationRepository;
		this.agencyRepository = agencyRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.configurationService = configurationService;
		this.securityService = securityService;

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

	@Error(FileUploadValidationException.class)
	public HttpResponse<String> handleValidationException(FileUploadValidationException ex) {
		// Return a 400 Bad Request response with the validation error message
		return HttpResponse.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
	}

	@Secured({RoleNames.CONSORTIUM_ADMIN, RoleNames.ADMINISTRATOR, RoleNames.LIBRARY_ADMIN})
	@Post(value = "/upload", consumes = MULTIPART_FORM_DATA, produces = APPLICATION_JSON)
	public Mono<DCBConfigurationService.UploadedConfigImport> importLocations(CompletedFileUpload file, String code, String type, String reason, @Nullable String changeCategory, @Nullable String changeReferenceUrl) {
		String username = (String) securityService.getAuthentication().get().getAttributes().get("preferred_username");
		return configurationService.importConfiguration(type, "Location import", code, file, reason, changeCategory, changeReferenceUrl, username);
	}


	// TODO: Convert return Location to LocationDTO
	@Post("/")
	public Mono<Location> postLocation(@Body LocationDTO location) {

		log.debug("postLocation {}", location.toString());

		UUID locationId = location.id();
		// Should we always set the id, since it should follow a set format ??
		if (UUIDUtils.isEmpty(locationId)) {
			locationId = UUIDUtils.generateLocationId(location.agencyCode(), location.code());
		}

		UUID agencyId = location.agency();
		if ((agencyId == null) && (location.agencyCode() != null)) {
			agencyId = UUIDUtils.generateAgencyId(location.agencyCode());
		}
		
		// Convert AgencyDTO into DataAgency with correctly linked HostLMS
		Location l = Location.builder()
			.id(locationId)
			.code(location.code())
			.name(location.name())
			.type(location.type())
			.isPickup(location.isPickup())
			.isShelving(location.isShelving())
			.longitude(location.longitude())
			.latitude(location.latitude())
			.deliveryStops(location.deliveryStops())
			.printLabel(location.printLabel())
			.localId(location.localId())
			.build();

		return enrichAgency(l, agencyId)
      .flatMap(loc -> enrichHostLms(l, location.hostLms()))
      .flatMap(loc -> Mono.from(locationRepository.existsById(l.getId())))
			.flatMap(exists -> Mono.fromDirect(exists ? locationRepository.update(l) : locationRepository.save(l)));
	}

  private Mono<Location> enrichAgency(Location l, UUID agencyId) {
    return Mono.from(agencyRepository.findById(agencyId))
      .map( agency -> l.setAgency(agency) )
      .switchIfEmpty(Mono.defer(() -> {
        log.error("Unable to locate agency using supplied UUID {}", agencyId);
        return Mono.empty();
      }))
      .thenReturn(l);
  }

  private Mono<Location> enrichHostLms(Location l, UUID hostLmsId) {
    if ( hostLmsId != null ) {
      return Mono.from(hostLmsRepository.findById(hostLmsId))
        .map(hostSystem -> l.setHostSystem(hostSystem) )
        .thenReturn(l);
    }
    else {
      log.error("No Host LMS supplied for location");
    }

    return Mono.just(l);
  }

}
