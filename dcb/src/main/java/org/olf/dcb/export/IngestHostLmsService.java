package org.olf.dcb.export;

import java.util.List;
import java.util.Map;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.storage.NumericRangeMappingRepository;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
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

	private final AgencyRepository agencyRepository;
	private final HostLmsRepository hostLmsRepository;
	private final LocationRepository locationRepository;
	NumericRangeMappingRepository numericRangeMappingRepository;
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	
	public IngestHostLmsService(
			AgencyRepository agencyRepository,
		HostLmsRepository hostLmsRepository,
		LocationRepository locationRepository,
		NumericRangeMappingRepository numericRangeMappingRepository,
		ReferenceValueMappingRepository referenceValueMappingRepository
	) {
		this.agencyRepository = agencyRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.locationRepository = locationRepository;
		this.numericRangeMappingRepository = numericRangeMappingRepository;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
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
		DataHostLms dataHostLms,
		ProcessingResult processingResult 
	) {
		return(Mono.from(hostLmsRepository.existsById(dataHostLms.getId()))
			.flatMap((Boolean exists) -> {
				if (exists) {
					// Clear down any reference data values for the contexts
					Map<String, Object> clientConfig = dataHostLms.getClientConfig();
					if (clientConfig != null) {
						@SuppressWarnings("unchecked")
						List<String> contextHierarchy = (List<String>)clientConfig.get("contextHierarchy");
						if ((contextHierarchy != null) && !contextHierarchy.isEmpty()) {
							String thisContext = contextHierarchy.get(contextHierarchy.size() - 1);
							
							// Delete the existing numeric and reference value mappings
							Mono.when(
								Mono.from(referenceValueMappingRepository.deleteByContext(thisContext)),
								Mono.from(numericRangeMappingRepository.deleteByContext(thisContext))
							);
						}
					}

					// Delete the pickup locations
					Mono.when(
						Mono.from(locationRepository.deleteByHostLmsId(dataHostLms.getId()))
					);

					// Delete the agencies, this needs to occur after the pickup locations have been deleted 
					Mono.when(
						Mono.from(agencyRepository.deleteByHostLmsId(dataHostLms.getId()))
					);

					// Now we can update the host lms
					return(Mono.fromDirect(hostLmsRepository.update(dataHostLms)));
				} else {
					// Just create the host lms
					return(Mono.fromDirect(hostLmsRepository.save(dataHostLms)));
				}
			})
			.doOnSuccess(a -> {
				processingResult.success(dataHostLms.getId().toString(), dataHostLms.getName());
			})
			.doOnError(e -> {
				processingResult.failed(dataHostLms.getId().toString(), dataHostLms.getName(), e.toString());
			})
			.then(Mono.just(dataHostLms))
		);
	}
}
