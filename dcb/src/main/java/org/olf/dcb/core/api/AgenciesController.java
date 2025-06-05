package org.olf.dcb.core.api;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import io.micronaut.http.annotation.*;
import io.micronaut.serde.annotation.Serdeable;
import org.olf.dcb.core.api.serde.AgencyDTO;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.FunctionalSettingType;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.FunctionalSettingRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LibraryRepository;
import org.olf.dcb.storage.LocationRepository;

import com.github.javaparser.utils.Log;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import services.k_int.utils.UUIDUtils;

@Controller("/agencies")
@Validated
@Secured(ADMINISTRATOR)
@Tag(name = "Admin API")
@Slf4j
public class AgenciesController {
	private final AgencyRepository agencyRepository;
	private final FunctionalSettingRepository functionalSettingRepository;
	private final HostLmsRepository hostLmsRepository;
	private final LibraryRepository libraryRepository;
	private final LocationRepository locationRepository;

	public AgenciesController(
		AgencyRepository agencyRepository,
		FunctionalSettingRepository functionalSettingRepository,
		HostLmsRepository hostLmsRepository,
		LibraryRepository libraryRepository,
		LocationRepository locationRepository
	) {

		this.agencyRepository = agencyRepository;
		this.functionalSettingRepository = functionalSettingRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.libraryRepository = libraryRepository;
		this.locationRepository = locationRepository;
	}

	@Operation(
		summary = "Browse Agencies",
		description = "Paginate through the list of known agencies",
		parameters = {
			@Parameter(in = ParameterIn.QUERY, name = "number", description = "The page number", schema = @Schema(type = "integer", format = "int32"), example = "1"),
			@Parameter(in = ParameterIn.QUERY, name = "size", description = "The page size", schema = @Schema(type = "integer", format = "int32"), example = "100")}
	)
	@Get("/{?pageable*}")
	public Mono<Page<AgencyDTO>> list(@Parameter(hidden = true) @Valid Pageable pageable) {
		log.debug("agencies::list");

		final Pageable finalPageable = pageable == null ? Pageable.from(0, 100) : pageable;
		return Flux.from(agencyRepository.queryAll())
			// work around as fetching agency will not fetch hostLms or hostLmsId
			.flatMap(this::addHostLms)
			.flatMap(this::addLibraryLabels)
			.map(mapToAgencyDTO())
			.collectList()
			.map(agencyDTOList -> Page.of(agencyDTOList, finalPageable, (long)agencyDTOList.size()));
	}

	@Get("/{id}")
	public Mono<AgencyDTO> show(UUID id) {
		return Mono.from(agencyRepository.findById(id))
			.flatMap(this::addHostLms)
			.flatMap(this::addLibraryLabels)
			.map(mapToAgencyDTO());
	}

	@Post("/")
	public Mono<AgencyDTO> postAgency(@Body AgencyDTO agencyToSave) {
		log.debug("REST, save or update agency: {}", agencyToSave);

		// Should we always set the id, since it should follow a set format ??
		if (UUIDUtils.isEmpty(agencyToSave.getId())) {
			agencyToSave.setId(UUIDUtils.generateAgencyId(agencyToSave.getCode()));
		}
		
		return Mono.from(hostLmsRepository.findByCode(agencyToSave.getHostLMSCode()))
			.flatMap(hostLms -> {
				if (hostLms == null) {
					log.error("Unable to locate Host LMS with code {}", agencyToSave.getHostLMSCode());
					return Mono.empty();
				}

				return Mono.just(mapToAgency(agencyToSave, hostLms));
			})
			.doOnNext(a -> log.debug("save agency {}", a))
			.flatMap(this::saveOrUpdate)
			.flatMap(this::addHostLms)
			.flatMap(this::addLibraryLabels)
			.map(mapToAgencyDTO());
	}

	@Get("/{id}/pickupLocations")
	public Mono<List<AgencyPickupLocations>> pickupLocations(@PathVariable UUID id) {
		return isPickupAnywhereEnabled(id)
			.flatMap(isEnabled -> isEnabled
				? getAllAgencyPickupLocations()
				: getSingleAgencyPickupLocations(id));
	}

	private Mono<Boolean> isPickupAnywhereEnabled(UUID agencyId) {
		return Mono.from(functionalSettingRepository
				.isSettingEnabledForAgency(agencyId, FunctionalSettingType.PICKUP_ANYWHERE.toString()))
			.defaultIfEmpty(false);
	}

	private Mono<List<AgencyPickupLocations>> getAllAgencyPickupLocations() {
		return Flux.from(agencyRepository.queryAll())
			.flatMap(agency -> buildAgencyPickupLocations(agency, PickupFilter.ANYWHERE_ENABLED))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collectList();
	}

	private Mono<List<AgencyPickupLocations>> getSingleAgencyPickupLocations(UUID agencyId) {
		return Mono.from(agencyRepository.findById(agencyId))
			.flatMap(agency -> buildAgencyPickupLocations(agency, PickupFilter.STANDARD))
			.map(this::wrapInListOrEmpty);
	}

	private Mono<Optional<AgencyPickupLocations>> buildAgencyPickupLocations(
		DataAgency agency,
		PickupFilter filterType) {

		return getFilteredPickupLocations(agency.getId(), filterType)
			.map(this::mapLocationToDto)
			.collectList()
			.map(locations -> createAgencyPickupLocationsIfNotEmpty(agency, locations));
	}

	private Flux<Location> getFilteredPickupLocations(UUID agencyId, PickupFilter filterType) {
		return Flux.from(locationRepository.getPickupLocations(agencyId))
			.filter(Location::getIsPickup)
			.filter(location -> applyPickupFilter(location, filterType));
	}

	private boolean applyPickupFilter(Location location, PickupFilter filterType) {
		return switch (filterType) {
			case ANYWHERE_ENABLED -> location.getIsEnabledForPickupAnywhere();
			case STANDARD -> true;
		};
	}

	private Optional<AgencyPickupLocations> createAgencyPickupLocationsIfNotEmpty(
		DataAgency agency,
		List<PickupLocationDto> locations) {

		if (locations.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new AgencyPickupLocations(
			agency.getId(),
			agency.getName(),
			locations
		));
	}

	private List<AgencyPickupLocations> wrapInListOrEmpty(Optional<AgencyPickupLocations> optional) {
		return optional.map(List::of).orElse(List.of());
	}

	private PickupLocationDto mapLocationToDto(Location location) {
		return new PickupLocationDto(location.getId(), location.getName());
	}

	// Enum to make filtering logic clearer
	private enum PickupFilter {
		ANYWHERE_ENABLED,  // Only locations enabled for pickup anywhere
		STANDARD          // All pickup locations
	}

	@Serdeable
	public record AgencyPickupLocations(
		UUID id,
		String name,
		List<PickupLocationDto> locations
	) {}

	@Serdeable
	public record PickupLocationDto(
		UUID id,
		String name
	) {}
	
	private static DataAgency mapToAgency(AgencyDTO agency, DataHostLms lms) {
		return DataAgency.builder()
			.id(agency.getId())
			.code(agency.getCode())
			.name(agency.getName())
			.hostLms(lms)
			.authProfile(agency.getAuthProfile())
			.idpUrl(agency.getIdpUrl())
			.longitude(agency.getLongitude())
			.latitude(agency.getLatitude())
			.isSupplyingAgency(agency.getIsSupplyingAgency())
			.isBorrowingAgency(agency.getIsBorrowingAgency())
			.build();
	}

	private Mono<DataAgency> addHostLms(DataAgency dataAgency) {
		log.debug("addHostLms: {}", dataAgency);
		return Mono.from(agencyRepository.findHostLmsIdById(dataAgency.getId()))
			.flatMap(hostLmsId -> Mono.from(hostLmsRepository.findById(hostLmsId)))
			.map(dataAgency::setHostLms);
	}

	private Mono<Tuple2<DataAgency, Library>> addLibraryLabels(DataAgency dataAgency) {
		return Mono.just(dataAgency)
			.zipWith( Mono.from(libraryRepository.findOneByAgencyCode(dataAgency.getCode())) )
			.doOnNext(logWarningForMissingLibraryLabels(dataAgency.getName()))
			.switchIfEmpty(Mono.defer(() -> {
				log.info("No Library was found for Agency[{}] when trying to addLibraryLabels()", dataAgency.getName());
				return Mono.just(Tuples.of(dataAgency, Library.builder().build()));
			}));
	}

	private static Consumer<Tuple2<DataAgency, Library>> logWarningForMissingLibraryLabels(String agencyName) {
		return tuple -> {
			final var library = tuple.getT2();

			if (library.getPrincipalLabel() == null) {
				log.warn("Missing principal label for Library {}, Agency {}", library.getFullName(), agencyName);
			}

			if (library.getSecretLabel() == null) {
				log.warn("Missing secret label for Library {}, Agency {}", library.getFullName(), agencyName);
			}
		};
	}

	private static Function<Tuple2<DataAgency, Library>, AgencyDTO> mapToAgencyDTO() {
		return function(AgencyDTO::mapToAgencyDTO);
	}

	private Mono<DataAgency> saveOrUpdate(DataAgency agency) {
		log.debug("saveOrUpdate: {}", agency);
		return Mono.from(agencyRepository.existsById(agency.getId()))
			.flatMap(exists -> {
				log.debug("Agency exists: {}", exists);

				return exists
					? update(agency)
					: save(agency);
			});
	}

	private Mono<? extends DataAgency> save(DataAgency agency) {
		return Mono.from(agencyRepository.save(agency));
	}

	private Mono<? extends DataAgency> update(DataAgency agencyToUpdate) {
		return Mono.just(agencyToUpdate)
				.zipWhen(this::findByCode, this::enrichWithExistingFields)
				.flatMap(enrichedAgency -> Mono.from(agencyRepository.update(enrichedAgency)));
	}

	private Mono<DataAgency> findByCode(DataAgency agency) {
		return Mono.from(agencyRepository.findById(agency.getId()));
	}

	/**
	 * Enrich the incoming definition with fields from the existing agency from the DB
	 * when they are omitted by the client.
	 * Only implements participation fields for the moment
	 *
	 * @param updatedAgency Agency received from the client
	 * @param existingAgency agency fetched from the database
	 * @return agency received from the client with participation fields from the DB if not provided
	 */
	private DataAgency enrichWithExistingFields(DataAgency updatedAgency, DataAgency existingAgency) {
		if (getValueOrNull(updatedAgency, DataAgency::getIsSupplyingAgency) == null) {
			updatedAgency.setIsSupplyingAgency(existingAgency.getIsSupplyingAgency());
		}

		if (getValueOrNull(updatedAgency, DataAgency::getIsBorrowingAgency) == null) {
			updatedAgency.setIsBorrowingAgency(existingAgency.getIsBorrowingAgency());
		}

		return updatedAgency;
	}
}
