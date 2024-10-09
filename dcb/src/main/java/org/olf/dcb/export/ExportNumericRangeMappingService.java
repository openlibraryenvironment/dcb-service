package org.olf.dcb.export;

import java.util.Collection;

import org.olf.dcb.core.model.NumericRangeMapping;
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
public class ExportNumericRangeMappingService {
	private static final Logger log = LoggerFactory.getLogger(ExportNumericRangeMappingService.class);
	
	private final NumericRangeMappingRepository numericRangeMappingRepository;
	
	public ExportNumericRangeMappingService(
		NumericRangeMappingRepository numericRangeMappingRepository
	) {
		this.numericRangeMappingRepository = numericRangeMappingRepository;
	}

	public void export(
		Collection<String> contextValues,
		SiteConfiguration siteConfiguration
	) {
		// Process the numeric range mappings associated with the exported host lms
		Flux.from(numericRangeMappingRepository.findByContexts(contextValues))
			.doOnError(e -> {
				String errorMessage = "Exception while processing numeric range mapping for export: " + e.toString();
				log.error(errorMessage, e);
				siteConfiguration.errors.add(errorMessage);
			})
			.flatMap(numericRangeMapping -> processDataNumericRangeMapping(numericRangeMapping, siteConfiguration))
			.blockLast();
	}

	private Mono<NumericRangeMapping> processDataNumericRangeMapping(
			NumericRangeMapping numericRangeMapping,
			SiteConfiguration siteConfiguration
	) {
		siteConfiguration.numericRangeMappings.add(numericRangeMapping);
		return(Mono.just(numericRangeMapping));
	}
}
