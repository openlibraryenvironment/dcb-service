package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.HostLmsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class IngestHostLmsService {
	private static final Logger log = LoggerFactory.getLogger(IngestHostLmsService.class);
	
	private final HostLmsRepository hostLmsRepository;
	
	public IngestHostLmsService(
		HostLmsRepository hostLmsRepository
	) {
		this.hostLmsRepository = hostLmsRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the host lms records they want to import
		List<DataHostLms> lmsHosts = siteConfiguration.lmsHosts;
		if ((lmsHosts != null) && !lmsHosts.isEmpty()) {
			Flux.fromIterable(lmsHosts)
				.doOnError(e -> {
					String errorMessage = "Exception while processing host lms for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(host -> processDataHostLms(host, ingestResult.lmsHosts))
				.blockLast();
		}
	}

	private Mono<DataHostLms> processDataHostLms(
		DataHostLms dataHost,
		ProcessingResult processingResult 
	) {
		return(Mono.from(hostLmsRepository.existsById(dataHost.getId()))
			.flatMap(exists -> Mono.fromDirect(exists ? hostLmsRepository.update(dataHost) : hostLmsRepository.save(dataHost)))
			.doOnSuccess(a -> {
				processingResult.success(dataHost.getId().toString(), dataHost.getName());
			})
			.doOnError(e -> {
				processingResult.failed(dataHost.getId().toString(), dataHost.getName(), e.toString());
			})
			.then(Mono.just(dataHost))
		);
	}
}
