package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.olf.dcb.core.model.NumericRangeMapping;
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

	public Map<String, Object> export(
		Collection<String> contextValues,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the numeric range mappings associated with the exported host lms
		List<NumericRangeMapping> numericRangeMappings = new ArrayList<NumericRangeMapping>();
		result.put("numericRangeMappings", numericRangeMappings);
		Flux.from(numericRangeMappingRepository.findByContexts(contextValues))
			.doOnError(e -> {
				String errorMessage = "Exception while processing numeric range mapping for export: " + e.toString();
				log.error(errorMessage, e);
				errors.add(errorMessage);
			})
			.flatMap(numericRangeMapping -> processDataNumericRangeMapping(numericRangeMapping, numericRangeMappings, errors))
			.blockLast();
		
		return(result);
	}

	private Mono<NumericRangeMapping> processDataNumericRangeMapping(
			NumericRangeMapping numericRangeMapping,
			List<NumericRangeMapping> numericRangeMappings,
			List<String> errors
	) {
		numericRangeMappings.add(numericRangeMapping);
		return(Mono.just(numericRangeMapping));
	}
}
