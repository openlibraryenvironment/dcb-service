package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.NumericRangeMapping;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.rules.ObjectRuleset;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.storage.NumericRangeMappingRepository;
import org.olf.dcb.storage.ObjectRulesetRepository;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ExportService {
	private static final Logger log = LoggerFactory.getLogger(ExportService.class);
	
	private final AgencyRepository agencyRepository;
	private final HostLmsRepository hostLmsRepository;
	private final LocationRepository locationRepository;
	private final NumericRangeMappingRepository numericRangeMappingRepository;
	private final ObjectRulesetRepository objectRulesetRepository;
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	
	public ExportService(
		AgencyRepository agencyRepository,
		HostLmsRepository hostLmsRepository,
		LocationRepository locationRepository,
		NumericRangeMappingRepository numericRangeMappingRepository,
		ObjectRulesetRepository objectRulesetRepository,
		ReferenceValueMappingRepository referenceValueMappingRepository
	) {
		this.agencyRepository = agencyRepository;
		this.hostLmsRepository = hostLmsRepository;
		this.locationRepository = locationRepository;
		this.numericRangeMappingRepository = numericRangeMappingRepository;
		this.objectRulesetRepository = objectRulesetRepository;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
	}

	public Map<String, Object> export(Collection<UUID> ids, Map<String, Object> result) {
		List<DataHostLms> hosts = new ArrayList<DataHostLms>();
		result.put("hosts", hosts);

		// Grab the errors so we do not need to cast it all the time
		@SuppressWarnings("unchecked")
		List<String> errors = (List<String>)result.get("errors");

		// The values we need to lookup the reference mappings with
		List<String> contextValues = new ArrayList<String>();
		List<String> rulesetNames = new ArrayList<String>();
		
		// Process the host lms records they want to export
		Flux.from(hostLmsRepository.findByIds(ids))
			.doOnError(e -> {
				String errorMessage = "Exception while processing host lms for export: " + e.toString();
				log.error(errorMessage, e);
				errors.add(errorMessage);
			})
			.flatMap(host -> processDataHostLms(host, hosts, contextValues, rulesetNames, errors))
			.blockLast();
		
		// Process the agencies associated with the exported host lms
		List<DataAgency> agencies = new ArrayList<DataAgency>();
		result.put("agencies", agencies);
		Flux.from(agencyRepository.findByHostLmsIds(ids))
			.doOnError(e -> {
				String errorMessage = "Exception while processing agency for export: " + e.toString();
				log.error(errorMessage, e);
				errors.add(errorMessage);
			})
			.flatMap(agency -> processDataAgency(agency, agencies, errors))
			.blockLast();
		
		// Process the reference value mappings associated with the exported host lms
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
		
		// Process the locations associated with the exported host lms
		List<Location> locations = new ArrayList<Location>();
		result.put("locations", locations);
		Flux.from(locationRepository.findByHostLmsIds(ids))
			.doOnError(e -> {
				String errorMessage = "Exception while processing locations for export: " + e.toString();
				log.error(errorMessage, e);
				errors.add(errorMessage);
			})
			.flatMap(location -> processDataLocation(location, locations, errors))
			.blockLast();
		
		// Finally process the rulesets if we have any
		if (!rulesetNames.isEmpty()) {
			List<ObjectRuleset> objectRulesets = new ArrayList<ObjectRuleset>();
			result.put("objectRulesets", objectRulesets);
			Flux.from(objectRulesetRepository.findByNames(rulesetNames))
				.doOnError(e -> {
					String errorMessage = "Exception while processing object rulesets for export: " + e.toString();
					log.error(errorMessage, e);
					errors.add(errorMessage);
				})
				.flatMap(objectRuleset -> processDataObjectRuleset(objectRuleset, objectRulesets, errors))
				.blockLast();
			
		}
		
		return(result);
	}

	private Mono<DataHostLms> processDataHostLms(
		DataHostLms dataHost,
		List<DataHostLms> dataHosts,
		List<String> contextValues,
		List<String> rulesetNames,
		List<String> errors
	) {
		// Add the host to our array
		dataHosts.add(dataHost);

		Map<String, Object> clientConfig = dataHost.getClientConfig();
		if (clientConfig == null) {
			errors.add("No client config specified for lms host: " + dataHost.getName());
		} else {
			@SuppressWarnings("unchecked")
			List<String> contextHierarchy = (List<String>)clientConfig.get("contextHierarchy");
			if ((contextHierarchy == null)  || contextHierarchy.isEmpty()) {
				errors.add("No contextHierarchy for lms host: " + dataHost.getName());
			} else {
				// Loop through each of the context values for the host and add them to our array if they do not already exist
				for (String context : contextHierarchy) {
					if (!contextValues.contains(context)) {
						// It dosn't currently exist so add it
						contextValues.add(context);
					}
				}
			}
		}
		
		// Now for the suppression rules
		if (dataHost.itemSuppressionRulesetName != null) {
			rulesetNames.add(dataHost.itemSuppressionRulesetName);
		}
		if (dataHost.suppressionRulesetName != null) {
			rulesetNames.add(dataHost.suppressionRulesetName);
		}
		
		return(Mono.just(dataHost));
	}

	private Mono<DataAgency> processDataAgency(
			DataAgency agency,
			List<DataAgency> agencies,
			List<String> errors
	) {
		agencies.add(agency);
		return(Mono.just(agency));
	}

	private Mono<ReferenceValueMapping> processDataReferenceValueMapping(
			ReferenceValueMapping referenceValueMapping,
			List<ReferenceValueMapping> referenceValueMappings,
			List<String> errors
	) {
		referenceValueMappings.add(referenceValueMapping);
		return(Mono.just(referenceValueMapping));
	}

	private Mono<NumericRangeMapping> processDataNumericRangeMapping(
			NumericRangeMapping numericRangeMapping,
			List<NumericRangeMapping> numericRangeMappings,
			List<String> errors
	) {
		numericRangeMappings.add(numericRangeMapping);
		return(Mono.just(numericRangeMapping));
	}

	private Mono<Location> processDataLocation(
			Location location,
			List<Location> locations,
			List<String> errors
	) {
		locations.add(location);
		return(Mono.just(location));
	}

	private Mono<ObjectRuleset> processDataObjectRuleset(
			ObjectRuleset objectRuleset,
			List<ObjectRuleset> objectRulesets,
			List<String> errors
	) {
		objectRulesets.add(objectRuleset);
		return(Mono.just(objectRuleset));
	}
}
