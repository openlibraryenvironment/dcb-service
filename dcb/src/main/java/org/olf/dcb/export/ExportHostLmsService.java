package org.olf.dcb.export;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.storage.HostLmsRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Singleton
public class ExportHostLmsService {
	
	private final HostLmsRepository hostLmsRepository;
	
	public ExportHostLmsService(
		HostLmsRepository hostLmsRepository
	) {
		this.hostLmsRepository = hostLmsRepository;
	}

	public void export(
		Collection<UUID> ids,
		List<String> contextValues,
		List<String> rulesetNames,
		SiteConfiguration siteConfiguration
	) {
		// Process the host lms records they want to export
		if ((ids != null) && !ids.isEmpty()) {
			Flux.from(hostLmsRepository.findByIds(ids))
				.doOnError(e -> {
					String errorMessage = "Exception while processing host lms for export: " + e.toString();
					log.error(errorMessage, e);
					siteConfiguration.errors.add(errorMessage);
				})
				.map((DataHostLms lmsHost) -> {
					// Add the host to our array
					siteConfiguration.lmsHosts.add(lmsHost);
					Map<String, Object> clientConfig = lmsHost.getClientConfig();
					if (clientConfig == null) {
						siteConfiguration.errors.add("No client config specified for lms host: " + lmsHost.getName());
					} else {
						@SuppressWarnings("unchecked")
						List<String> contextHierarchy = (List<String>)clientConfig.get("contextHierarchy");
						if ((contextHierarchy == null)  || contextHierarchy.isEmpty()) {
							siteConfiguration.errors.add("No contextHierarchy for lms host: " + lmsHost.getName());
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
					if (lmsHost.itemSuppressionRulesetName != null) {
						rulesetNames.add(lmsHost.itemSuppressionRulesetName);
					}
					if (lmsHost.suppressionRulesetName != null) {
						rulesetNames.add(lmsHost.suppressionRulesetName);
					}
					return(lmsHost);
				})
				.blockLast();
		}
	}
}
