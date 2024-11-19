package org.olf.dcb.export;

import java.util.Collection;

import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.rules.ObjectRuleset;
import org.olf.dcb.storage.ObjectRulesetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Singleton
public class ExportObjectRulesetService {
	private static final Logger log = LoggerFactory.getLogger(ExportObjectRulesetService.class);
	
	private final ObjectRulesetRepository objectRulesetRepository;
	
	public ExportObjectRulesetService(
		ObjectRulesetRepository objectRulesetRepository
	) {
		this.objectRulesetRepository = objectRulesetRepository;
	}

	public void export(
		Collection<String> rulesetNames,
		SiteConfiguration siteConfiguration
	) {
		// Process the rulesets
		if (!rulesetNames.isEmpty()) {
			Flux.from(objectRulesetRepository.findByNames(rulesetNames))
				.doOnError(e -> {
					String errorMessage = "Exception while processing object rulesets for export: " + e.toString();
					log.error(errorMessage, e);
					siteConfiguration.errors.add(errorMessage);
				})
				.map((ObjectRuleset objectRuleset) -> {
					siteConfiguration.objectRulesets.add(objectRuleset);
					return(objectRuleset);
				})
				.blockLast();
		}
	}
}
