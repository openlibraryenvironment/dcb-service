package org.olf.dcb.security.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.discovery.PatronResolveRateLimiter;
import org.olf.dcb.test.DcbTestContainerContextBuilder;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

/**
 * With discovery on, a discovery setting DCB cannot use must stop the application starting. If it
 * only failed on first use, a rollout would pass its health check and every patron call after it
 * would fail. With discovery off there is no discovery bean, so nothing about these settings is
 * read, and nothing about them stops startup.
 */
class DiscoveryTrustAnchorStartupTests {

	private static final String TRUSTED = DiscoveryTrustedServices.PROPERTY;

	@Test
	void withDiscoveryOffThereIsNoDiscoveryBeanAndUnusableSettingsDoNotStopStartup() {
		final var context = contextWith(Map.of(
			"dcb.discovery.enabled", "false",
			"dcb.discovery.audience", " ",
			TRUSTED, "{not json"));

		try {
			context.start();

			for (final var bean : new Class<?>[] { DiscoveryConfig.class, DiscoveryTrustedServiceStore.class,
				PatronAssertionVerifier.class, PatronResolveRateLimiter.class }) {

				assertFalse(context.containsBean(bean), bean.getSimpleName() + " exists with discovery off");
			}
		} finally {
			close(context);
		}
	}

	@Test
	void withDiscoveryOnTheDocumentedDefaultsApply() {
		final var context = contextWith(Map.of("dcb.discovery.enabled", "true"));

		try {
			context.start();

			final var config = context.getBean(DiscoveryConfig.class);

			assertEquals("dcb", config.getAudience());
			assertEquals(Duration.ofMinutes(2), config.getMaxAssertionLifetime());
			assertEquals(25, config.getMaxLoans());
			assertEquals(600, config.getPatronResolvesPerMinute());
			assertTrue(context.containsBean(PatronAssertionVerifier.class));
		} finally {
			close(context);
		}
	}

	@Test
	void aBlankAudienceStopsTheApplicationStarting() {
		assertStartupFails(Map.of("dcb.discovery.audience", " "), "getAudience - must not be blank");
	}

	@Test
	void aYamlListStopsTheApplicationStartingInsteadOfBeingIgnored() {
		assertStartupFails(Map.of(
			DiscoveryTrustedServiceStore.YAML_LIST + "[0].service-id", "yaml-service",
			DiscoveryTrustedServiceStore.YAML_LIST + "[0].issuer", "https://yaml.example.org"),
			"is not read");
	}

	@Test
	void aDuplicateIssuerStopsTheApplicationStarting() {
		assertStartupFails(Map.of(TRUSTED, """
			[{"service-id": "first", "issuer": "https://shared.example.org",
			  "jwks-uri": "https://first.example.org/jwks.json"},
			 {"service-id": "second", "issuer": "https://shared.example.org",
			  "jwks-uri": "https://second.example.org/jwks.json"}]
			"""), "Duplicate discovery service issuer");
	}

	@Test
	void malformedTrustJsonStopsTheApplicationStarting() {
		assertStartupFails(Map.of(TRUSTED, "{not json"), "trusted-services-json is not a JSON array");
	}

	private static void assertStartupFails(Map<String, Object> settings, String expectedMessage) {
		final var withDiscoveryOn = new HashMap<String, Object>(settings);
		withDiscoveryOn.put("dcb.discovery.enabled", "true");

		final var context = contextWith(withDiscoveryOn);

		try {
			final var failure = assertThrows(Throwable.class, context::start,
				"the application started with an unusable discovery setting");

			assertTrue(causeChainMentions(failure, expectedMessage),
				"startup failed, but not because of the discovery setting: " + failure);
		} finally {
			close(context);
		}
	}

	private static ApplicationContext contextWith(Map<String, Object> settings) {
		return new DcbTestContainerContextBuilder()
			.environments(Environment.TEST)
			.properties(settings)
			.build();
	}

	private static void close(ApplicationContext context) {
		if (context.isRunning()) {
			context.close();
		}
	}

	private static boolean causeChainMentions(Throwable failure, String text) {
		for (var cause = failure; cause != null; cause = cause.getCause()) {
			if (cause.getMessage() != null && cause.getMessage().contains(text)) {
				return true;
			}
		}

		return false;
	}
}
