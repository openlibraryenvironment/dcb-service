package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.storage.HostLmsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ExportHostLmsService {
	private static final Logger log = LoggerFactory.getLogger(ExportHostLmsService.class);
	
	private final HostLmsRepository hostLmsRepository;
	
	public ExportHostLmsService(
		HostLmsRepository hostLmsRepository
	) {
		this.hostLmsRepository = hostLmsRepository;
	}

	public Map<String, Object> export(
		Collection<UUID> ids,
		List<String> contextValues,
		List<String> rulesetNames,
		Map<String, Object> result,
		List<String> errors
	) {
		List<DataHostLms> hosts = new ArrayList<DataHostLms>();
		result.put("hosts", hosts);

		// Process the host lms records they want to export
		if ((ids != null) && !ids.isEmpty()) {
			Flux.from(hostLmsRepository.findByIds(ids))
				.doOnError(e -> {
					String errorMessage = "Exception while processing host lms for export: " + e.toString();
					log.error(errorMessage, e);
					errors.add(errorMessage);
				})
				.flatMap(host -> processDataHostLms(host, hosts, contextValues, rulesetNames, errors))
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
}
