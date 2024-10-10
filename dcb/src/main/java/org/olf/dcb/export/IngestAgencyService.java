package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
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
public class IngestAgencyService {
	private static final Logger log = LoggerFactory.getLogger(IngestAgencyService.class);
	
	private final AgencyRepository agencyRepository;
	
	public IngestAgencyService(
			AgencyRepository agencyRepository
	) {
		this.agencyRepository = agencyRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the objectRuleset records they want to import
		List<DataAgency> agencies = siteConfiguration.agencies;
		if ((agencies != null) && !agencies.isEmpty()) {
			Flux.fromIterable(agencies)
				.doOnError(e -> {
					String errorMessage = "Exception while processing agencies for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(agency -> processDataAgency(agency, ingestResult.agencies))
				.blockLast();
		}
	}

	private Mono<DataAgency> processDataAgency(
		DataAgency agency,
		ProcessingResult processingResult 
	) {
		return(Mono.from(agencyRepository.existsById(agency.getId()))
				.flatMap(exists -> Mono.fromDirect(exists ? agencyRepository.update(agency) : agencyRepository.save(agency)))
			.doOnSuccess(a -> {
				processingResult.success(agency.getId().toString(), agency.getName());
			})
			.doOnError(e -> {
				processingResult.failed(agency.getId().toString(), agency.getName(), e.toString());
			})
			.then(Mono.just(agency))
		);
	}
}
