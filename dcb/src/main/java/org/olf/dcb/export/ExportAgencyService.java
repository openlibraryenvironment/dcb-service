package org.olf.dcb.export;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.export.model.SiteConfiguration;
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

	public void export(
		Collection<UUID> hostLmsIds,
		List<String> agencyCodes,
		SiteConfiguration siteConfiguration
	) {
		// Process the agencies associated with the host lms ids
		Flux.from(agencyRepository.findByHostLmsIds(hostLmsIds))
			.doOnError(e -> {
				String errorMessage = "Exception while processing agency for export: " + e.toString();
				log.error(errorMessage, e);
				siteConfiguration.errors.add(errorMessage);
			})
			.flatMap(agency -> processDataAgency(agency, siteConfiguration, agencyCodes))
			.blockLast();
	}

	private Mono<DataAgency> processDataAgency(
			DataAgency agency,
			SiteConfiguration siteConfiguration,
			List<String> agencyCodes
	) {
		siteConfiguration.agencies.add(agency);
		agencyCodes.add(agency.getCode());
		return(Mono.just(agency));
	}
}
