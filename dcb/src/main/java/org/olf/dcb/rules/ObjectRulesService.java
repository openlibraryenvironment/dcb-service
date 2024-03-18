package org.olf.dcb.rules;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.olf.dcb.storage.ObjectRulesetRepository;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Singleton
@Slf4j
public class ObjectRulesService {
	private final Map<String, ObjectRuleset> configProvidedRules;
	private final ObjectRulesetRepository dataProvidedRules;

	public ObjectRulesService(BeanContext beans,
			ObjectRulesetRepository dataProvidedRules) {
		this.dataProvidedRules = dataProvidedRules;

		configProvidedRules = beans.getBeansOfType(ObjectRuleset.class).stream()
				.collect(Collectors.toUnmodifiableMap(ObjectRuleset::getName, Function.identity()));
		
		log.debug("Found rulesets from immutable config [{}]", configProvidedRules);
	}

	public Mono<ObjectRuleset> findByName(@NonNull @NotNull String name) {
		return Mono.from(dataProvidedRules.findByName(name))
				.switchIfEmpty(Mono.justOrEmpty(configProvidedRules.get(name)))
				.doOnSuccess(rs -> log.trace("Ruleset with name [{}] {}", name, rs != null ? "found" : "not found"));
	}
}
