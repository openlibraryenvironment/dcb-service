package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.NumericRangeMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class IngestNumericRangeMappingService {
	private static final Logger log = LoggerFactory.getLogger(IngestNumericRangeMappingService.class);
	
	private final NumericRangeMappingRepository numericRangeMappingRepository;
	
	public IngestNumericRangeMappingService(
			NumericRangeMappingRepository numericRangeMappingRepository
	) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the location records they want to import
		List<NumericRangeMapping> numericRangeMappings = siteConfiguration.numericRangeMappings;
		if ((numericRangeMappings != null) && !numericRangeMappings.isEmpty()) {
			Flux.fromIterable(numericRangeMappings)
				.doOnError(e -> {
					String errorMessage = "Exception while processing numeric range mappings for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(numericRangeMapping -> processDataNumericRangeMapping(numericRangeMapping, ingestResult.numericRangeMappings))
				.blockLast();
		}
	}

	private Mono<NumericRangeMapping> processDataNumericRangeMapping(
		NumericRangeMapping numericRangeMapping,
		ProcessingResult processingResult 
	) {
		return(Mono.from(numericRangeMappingRepository.existsById(numericRangeMapping.getId())).flatMap(exists -> Mono
			.fromDirect(exists ? numericRangeMappingRepository.update(numericRangeMapping) : numericRangeMappingRepository.save(numericRangeMapping)))
			.doOnSuccess(a -> {
				processingResult.success(numericRangeMapping.getId().toString(), numericRangeMapping.getContext() + ":" + numericRangeMapping.getDomain() + ":" + numericRangeMapping.getLowerBound().toString() + ":" + numericRangeMapping.getUpperBound().toString());
			})
			.doOnError(e -> {
				processingResult.failed(numericRangeMapping.getId().toString(), numericRangeMapping.getContext() + ":" + numericRangeMapping.getDomain() + ":" + numericRangeMapping.getLowerBound().toString() + ":" + numericRangeMapping.getUpperBound().toString(), e.toString());
			})
			.then(Mono.just(numericRangeMapping))
		);
	}
}
