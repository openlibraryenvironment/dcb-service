package org.olf.dcb.request.resolution;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.function.Function;

@Slf4j
record ResolutionStep(
	String name,
	Function<Resolution, Mono<Resolution>> operation,
	Function<Resolution, Boolean> condition
) {
	// Simplified constructor for unconditional steps
	public ResolutionStep(String name, Function<Resolution, Mono<Resolution>> operation) {
		this(name, operation, resolution -> true);
	}

	// Simplified constructor for boolean condition steps
	public ResolutionStep(String name, Function<Resolution, Mono<Resolution>> operation, boolean staticCondition) {
		this(name, operation, resolution -> staticCondition);
	}

	// Apply the operation if the condition is true
	static Mono<Resolution> applyOperationOnCondition(ResolutionStep step, Resolution resolution) {
		if (step.condition().apply(resolution)) {
			log.debug("Executing resolution step: {}", step.name());

			return step.operation().apply(resolution);
		}

		return Mono.just(resolution);
	}
}
