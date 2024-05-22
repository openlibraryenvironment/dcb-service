package org.olf.dcb.core.api;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrDefault;

import java.util.UUID;

import org.olf.dcb.core.api.serde.AgencyDTO;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;

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

@Controller("/agencies")
@Validated
@Secured(ADMINISTRATOR)
@Tag(name = "Admin API")
@Slf4j
public class AgenciesController {
	private final AgencyRepository agencyRepository;
	private final HostLmsRepository hostLmsRepository;

	public AgenciesController(AgencyRepository agencyRepository,
		HostLmsRepository hostLmsRepository) {

		this.agencyRepository = agencyRepository;
		this.hostLmsRepository = hostLmsRepository;
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
		if (pageable == null) {
			pageable = Pageable.from(0, 100);
		}

		log.debug("agencies::list");

		@Valid Pageable finalPageable = pageable;
		return Flux.from(agencyRepository.queryAll())
			// work around as fetching agency will not fetch hostLms or hostLmsId
			.flatMap(this::addHostLms)
			.map(AgencyDTO::mapToAgencyDTO)
			.collectList()
			.map(agencyDTOList -> Page.of(agencyDTOList, finalPageable, agencyDTOList.size()));
	}

	@Get("/{id}")
	public Mono<AgencyDTO> show(UUID id) {
		return Mono.from(agencyRepository.findById(id)).flatMap(this::addHostLms).map(AgencyDTO::mapToAgencyDTO);
	}

	@Post("/")
	public Mono<AgencyDTO> postAgency(@Body AgencyDTO agencyToSave) {
		log.debug("REST, save or update agency: {}", agencyToSave);

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
			.map(AgencyDTO::mapToAgencyDTO);
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
			.isSupplyingAgency(getValueOrDefault(agency, AgencyDTO::getIsSupplyingAgency, true))
			.isBorrowingAgency(getValueOrDefault(agency, AgencyDTO::getIsBorrowingAgency, true))
			.build();
	}

	private Mono<DataAgency> addHostLms(DataAgency dataAgency) {
		log.debug("addHostLms: {}", dataAgency);
		return Mono.from(agencyRepository.findHostLmsIdById(dataAgency.getId()))
			.flatMap(hostLmsId -> Mono.from(hostLmsRepository.findById(hostLmsId)))
			.map(dataAgency::setHostLms);
	}

	private Mono<DataAgency> saveOrUpdate(DataAgency agency) {
		log.debug("saveOrUpdate: {}", agency);
		return Mono.from(agencyRepository.existsById(agency.getId()))
			.flatMap(exists -> {
				log.debug("Agency exists: {}", exists);

				return Mono.from(exists
					? agencyRepository.update(agency)
					: agencyRepository.save(agency));
			});
	}
}
