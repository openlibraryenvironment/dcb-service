package org.olf.dcb.request.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.HostLms;

/**
 * PR-2: per-host capability resolution. Pure unit test - no Micronaut context or
 * Testcontainers required. Proves that placement strategy / tracking mode are
 * resolved from each host's own clientConfig, so profiles A-D can coexist.
 */
class LifecycleCapabilityResolverTests {
	private final LifecycleCapabilitiesConfiguration globalConfig =
		new LifecycleCapabilitiesConfiguration();

	private final LifecycleCapabilityResolver resolver =
		new LifecycleCapabilityResolver(globalConfig);

	private static HostLms hostWithConfig(Map<String, Object> clientConfig) {
		final var host = mock(HostLms.class);
		when(host.getCode()).thenReturn("TEST");
		when(host.getClientConfig()).thenReturn(clientConfig);
		return host;
	}

	@Test
	void hostWithoutCapabilitiesDefaultsToImperativeScheduledPoll() {
		final var host = hostWithConfig(Map.of());

		assertEquals(StrategyType.IMPERATIVE,
			resolver.placementStrategy(host, LifecycleRole.BORROWER));
		assertEquals(TrackingMode.SCHEDULED_POLL,
			resolver.trackingMode(host, LifecycleRole.BORROWER));
	}

	@Test
	void nullHostFallsBackToInstanceWideImperativeDefault() {
		assertEquals(StrategyType.IMPERATIVE,
			resolver.placementStrategy(null, LifecycleRole.BORROWER));
	}

	@Test
	void hostSelectsDeclarativePlacementPerRole() {
		final var host = hostWithConfig(Map.of("capabilities", Map.of(
			"borrowing-agency-request",
			Map.of("strategy", "declarative", "protocol", "ncip-v202"))));

		assertEquals(StrategyType.DECLARATIVE,
			resolver.placementStrategy(host, LifecycleRole.BORROWER));
		assertEquals("ncip-v202",
			resolver.placementProtocol(host, LifecycleRole.BORROWER));

		// Supplier role is unset on this host - must fall back to imperative.
		assertEquals(StrategyType.IMPERATIVE,
			resolver.placementStrategy(host, LifecycleRole.SUPPLIER));
	}

	@Test
	void declarativePlacementWithoutProtocolFailsFast() {
		final var host = hostWithConfig(Map.of("capabilities", Map.of(
			"borrowing-agency-request", Map.of("strategy", "declarative"))));

		assertThrows(LifecycleCapabilityConfigurationException.class,
			() -> resolver.placementStrategy(host, LifecycleRole.BORROWER));
	}

	@Test
	void trackingModeAcceptsYamlKebabCase() {
		final var host = hostWithConfig(Map.of("capabilities", Map.of(
			"borrower-tracking",
			Map.of("mode", "event-driven", "protocol", "ncip-v202"))));

		assertEquals(TrackingMode.EVENT_DRIVEN,
			resolver.trackingMode(host, LifecycleRole.BORROWER));
	}

	@Test
	void twoHostsResolveIndependentlyInOneRun() {
		final var imperativeHost = hostWithConfig(Map.of());
		final var declarativeHost = hostWithConfig(Map.of("capabilities", Map.of(
			"borrowing-agency-request",
			Map.of("strategy", "declarative", "protocol", "ncip-v202"))));

		assertEquals(StrategyType.IMPERATIVE,
			resolver.placementStrategy(imperativeHost, LifecycleRole.BORROWER));
		assertEquals(StrategyType.DECLARATIVE,
			resolver.placementStrategy(declarativeHost, LifecycleRole.BORROWER));
	}
}
