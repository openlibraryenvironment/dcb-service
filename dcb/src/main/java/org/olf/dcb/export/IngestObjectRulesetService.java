package org.olf.dcb.export;

import java.util.List;

import org.olf.dcb.export.model.IngestResult;
import org.olf.dcb.export.model.ProcessingResult;
import org.olf.dcb.export.model.SiteConfiguration;
import org.olf.dcb.rules.ObjectRuleset;
import org.olf.dcb.storage.ObjectRulesetRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class IngestObjectRulesetService {
	
	private final ObjectRulesetRepository objectRulesetRepository;
	
	public IngestObjectRulesetService(
			ObjectRulesetRepository objectRulesetRepository
	) {
		this.objectRulesetRepository = objectRulesetRepository;
	}

	public void ingest(
		SiteConfiguration siteConfiguration,
		IngestResult ingestResult 
	) {
		// Process the objectRuleset records they want to import
		List<ObjectRuleset> objectRuleSets = siteConfiguration.objectRulesets;
		if ((objectRuleSets != null) && !objectRuleSets.isEmpty()) {
			Flux.fromIterable(objectRuleSets)
				.doOnError(e -> {
					String errorMessage = "Exception while processing object rule sets for ingest: " + e.toString();
					log.error(errorMessage, e);
					ingestResult.messages.add(errorMessage);
				})
				.flatMap(objectRuleset -> processDataObjectRuleset(objectRuleset, ingestResult.objectRulesets))
				.blockLast();
		}
	}

	private Mono<ObjectRuleset> processDataObjectRuleset(
		ObjectRuleset objectRuleset,
		ProcessingResult processingResult 
	) {
		return(Mono.from(objectRulesetRepository.existsById(objectRuleset.getName()))
			.flatMap(exists -> Mono.fromDirect(exists ? objectRulesetRepository.update(objectRuleset) : objectRulesetRepository.save(objectRuleset)))
			.doOnSuccess(a -> {
				processingResult.success(objectRuleset.getName(), objectRuleset.getName());
			})
			.doOnError(e -> {
				processingResult.failed(objectRuleset.getName(), objectRuleset.getName(), e.toString());
			})
			.then(Mono.just(objectRuleset))
		);
	}
}
