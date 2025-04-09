package org.olf.dcb.devtools.interaction.dummy;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class LmsResponseResolver {

	private final List<LmsResponseStrategy> strategies;

	public LmsResponseResolver(List<LmsResponseStrategy> strategies) {
		this.strategies = strategies;
	}

	public Optional<DummyResponse> resolve(String state, String role, String responseType) {
		List<LmsResponseStrategy> matchingResponses = strategies.stream()
			.filter(s -> s.supports(state, role, responseType))
			.collect(Collectors.toList());

		if (matchingResponses.size() > 1) {
			log.warn("Multiple strategies found for state={} role={} responseType={}", state, role, responseType);
		}

		if (matchingResponses.isEmpty()) {
			log.warn("No strategy found for state={} role={} responseType={}", state, role, responseType);
		}

		return matchingResponses.stream()
			.findFirst()
			.map(s -> s.getResponse(state, role, responseType));
	}

}
