package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ExportAgencyService {
	private static final Logger log = LoggerFactory.getLogger(ExportAgencyService.class);
	
	private final AgencyRepository agencyRepository;
	
	public ExportAgencyService(
		AgencyRepository agencyRepository
	) {
		this.agencyRepository = agencyRepository;
	}

	public Map<String, Object> export(
		Collection<UUID> hostLmsIds,
		List<String> agencyCodes,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the agencies associated with the host lms ids
		List<DataAgency> agencies = new ArrayList<DataAgency>();
		result.put("agencies", agencies);
		Flux.from(agencyRepository.findByHostLmsIds(hostLmsIds))
			.doOnError(e -> {
				String errorMessage = "Exception while processing agency for export: " + e.toString();
				log.error(errorMessage, e);
				errors.add(errorMessage);
			})
			.flatMap(agency -> processDataAgency(agency, agencies, agencyCodes, errors))
			.blockLast();
		
		return(result);
	}

	private Mono<DataAgency> processDataAgency(
			DataAgency agency,
			List<DataAgency> agencies,
			List<String> agencyCodes,
			List<String> errors
	) {
		agencies.add(agency);
		agencyCodes.add(agency.getCode());
		return(Mono.just(agency));
	}
}
