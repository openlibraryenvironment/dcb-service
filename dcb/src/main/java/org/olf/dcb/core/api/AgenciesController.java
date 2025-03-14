package org.olf.dcb.core.api;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
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
	public Mono<List<HashMap<String, Object>>> pickupLocations(UUID id) {
		List<HashMap<String, Object>> pickupLocations = new ArrayList<HashMap<String, Object>>();
		return(Mono.from(functionalSettingRepository.isSettingEnabledForAgency(id, FunctionalSettingType.PICKUP_ANYWHERE.toString()))
			.defaultIfEmpty(false)
			.flatMap((Boolean isEnabled) -> {
				return(isEnabled ? allPickupLocations(pickupLocations)
								  : pickupLocationsForAgency(id, pickupLocations)
				);
			})
		);
	}

	private Mono<List<HashMap<String, Object>>> allPickupLocations(
		List<HashMap<String, Object>> pickupLocations	
	) {
		return(Flux.from(agencyRepository.queryAll())
			.collectList()
			.flatMap((List<DataAgency> agencies) -> {
				for (DataAgency agency : agencies) {
			    	addAgencyPickupLocations(agency, pickupLocations).subscribe();
				}
		    	return(Mono.just(pickupLocations));
			})
		);
		
	}
	
	private Mono<List<HashMap<String, Object>>> pickupLocationsForAgency(
		UUID agencyId,
		List<HashMap<String, Object>> pickupLocations	
	) {
		return(Mono.from(agencyRepository.findById(agencyId))
			.flatMap((DataAgency agency) -> {
		    	return(addAgencyPickupLocations(agency, pickupLocations));
		    })
		);
	}
	
	private Mono<List<HashMap<String, Object>>> addAgencyPickupLocations(
		DataAgency agency,
		List<HashMap<String, Object>> pickupLocations
	) {
		return(Flux.from(locationRepository.getPickupLocations(agency.getId()))
	   		.collectList()
	   		.flatMap((List<Location> locs) -> {
	   			List<HashMap<String, Object>> locations = new ArrayList<HashMap<String, Object>>();
	   			for (Location loc : locs) {
	   				HashMap<String, Object> pickupLocation = new HashMap<String, Object>();
	   				locations.add(pickupLocation);
	   				pickupLocation.put("id", loc.getId());
	   				pickupLocation.put("name", loc.getName());
	   			}
	   			
	   			// Now if we found some pickup locations add this agency to pickup locations
	   			if (!locations.isEmpty()) {
	   				HashMap<String, Object> agencyPickupLocations = new HashMap<String, Object>();
	   				pickupLocations.add(agencyPickupLocations);
	   				agencyPickupLocations.put("id", agency.getId());
	   				agencyPickupLocations.put("name", agency.getName());
	   				agencyPickupLocations.put("locations", locations);
	   			}
	   			return(Mono.just(pickupLocations));
	   		})
	   	);
	}
	
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
