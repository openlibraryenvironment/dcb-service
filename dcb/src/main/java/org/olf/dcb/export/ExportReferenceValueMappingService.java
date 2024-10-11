package org.olf.dcb.export;

import java.util.Collection;

import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

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

	public void export(
		Collection<String> contextValues,
		SiteConfiguration siteConfiguration
	) {
		// Process the reference value mappings associated
		Flux.from(referenceValueMappingRepository.findByContexts(contextValues))
			.doOnError(e -> {
				String errorMessage = "Exception while processing reference value mapping for export: " + e.toString();
				log.error(errorMessage, e);
				siteConfiguration.errors.add(errorMessage);
			})
			.map((ReferenceValueMapping referenceValueMapping) -> {
				siteConfiguration.referenceValueMappings.add(referenceValueMapping);
				return(referenceValueMapping);
			})
			.blockLast();
	}
}
