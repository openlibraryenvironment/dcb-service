package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.ReferenceValueMappingRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class IngestReferenceValueMappingService {
	
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	
	public IngestReferenceValueMappingService(
			ReferenceValueMappingRepository referenceValueMappingRepository
	) {
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the location records they want to import
		List<ReferenceValueMapping> referenceValueMappings = siteConfiguration.referenceValueMappings;
		if ((referenceValueMappings != null) && !referenceValueMappings.isEmpty()) {
			Flux.fromIterable(referenceValueMappings)
				.doOnError(e -> {
					String errorMessage = "Exception while processing reference value mappings for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(referenceValueMapping -> processDataReferenceValueMapping(referenceValueMapping, ingestResult.referenceValueMappings))
				.blockLast();
		}
	}

	private Mono<ReferenceValueMapping> processDataReferenceValueMapping(
		ReferenceValueMapping referenceValueMapping,
		ProcessingResult processingResult 
	) {
		return(Mono.from(referenceValueMappingRepository.existsById(referenceValueMapping.getId())).flatMap(exists -> Mono
			.fromDirect(exists ? referenceValueMappingRepository.update(referenceValueMapping) : referenceValueMappingRepository.save(referenceValueMapping)))
			.doOnSuccess(a -> {
				processingResult.success(referenceValueMapping.getId().toString(), referenceValueMapping.getFromContext() + ":" + referenceValueMapping.getFromCategory() + ":" + referenceValueMapping.getFromValue());
			})
			.doOnError(e -> {
				processingResult.failed(referenceValueMapping.getId().toString(), referenceValueMapping.getFromContext() + ":" + referenceValueMapping.getFromCategory() + ":" + referenceValueMapping.getFromValue(), e.toString());
			})
			.then(Mono.just(referenceValueMapping))
		);
	}
}
