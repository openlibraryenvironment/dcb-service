package org.olf.dcb.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.olf.dcb.rules.ObjectRuleset;
import org.olf.dcb.storage.ObjectRulesetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

	public Map<String, Object> export(
		Collection<String> rulesetNames,
		Map<String, Object> result,
		List<String> errors
	) {
		// Process the rulesets
		List<ObjectRuleset> objectRulesets = new ArrayList<ObjectRuleset>();
		result.put("objectRulesets", objectRulesets);
		if (!rulesetNames.isEmpty()) {
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

	private Mono<ObjectRuleset> processDataObjectRuleset(
			ObjectRuleset objectRuleset,
			List<ObjectRuleset> objectRulesets,
			List<String> errors
	) {
		objectRulesets.add(objectRuleset);
		return(Mono.just(objectRuleset));
	}
}
