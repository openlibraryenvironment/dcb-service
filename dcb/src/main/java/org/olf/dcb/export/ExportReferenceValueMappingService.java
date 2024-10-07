package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ExportReferenceValueMappingService {
	private static final Logger log = LoggerFactory.getLogger(ExportReferenceValueMappingService.class);
	
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	
	public ExportReferenceValueMappingService(
		ReferenceValueMappingRepository referenceValueMappingRepository
	) {
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	public Map<String, Object> export(
		Collection<String> contextValues,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the reference value mappings associated
		List<ReferenceValueMapping> referenceValueMappings = new ArrayList<ReferenceValueMapping>();
		result.put("referenceValueMappings", referenceValueMappings);
		Flux.from(referenceValueMappingRepository.findByContexts(contextValues))
			.doOnError(e -> {
				String errorMessage = "Exception while processing reference value mapping for export: " + e.toString();
				log.error(errorMessage, e);
				errors.add(errorMessage);
			})
			.flatMap(referenceValueMapping -> processDataReferenceValueMapping(referenceValueMapping, referenceValueMappings, errors))
			.blockLast();
		
		return(result);
	}

	private Mono<ReferenceValueMapping> processDataReferenceValueMapping(
			ReferenceValueMapping referenceValueMapping,
			List<ReferenceValueMapping> referenceValueMappings,
			List<String> errors
	) {
		referenceValueMappings.add(referenceValueMapping);
		return(Mono.just(referenceValueMapping));
	}
}
