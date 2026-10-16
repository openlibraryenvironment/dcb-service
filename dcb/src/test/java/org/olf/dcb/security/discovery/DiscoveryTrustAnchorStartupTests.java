package org.olf.dcb.security.discovery;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTestContainerContextBuilder;

import io.micronaut.context.env.Environment;

/**
 * A trust anchor DCB cannot build must stop the application starting. If it only failed
 * on first use, a rollout would pass its health check and every patron call after it
 * would fail.
 */
class DiscoveryTrustAnchorStartupTests {

	@Test
	void aDuplicateIssuerStopsTheApplicationStarting() {
		assertStartupFails("""
			[{"service-id": "first", "issuer": "https://shared.example.org",
			  "jwks-uri": "https://first.example.org/jwks.json"},
			 {"service-id": "second", "issuer": "https://shared.example.org",
			  "jwks-uri": "https://second.example.org/jwks.json"}]
			""", "Duplicate discovery service issuer");
	}

	@Test
	void malformedTrustJsonStopsTheApplicationStarting() {
		assertStartupFails("{not json", "trusted-services-json is not a JSON array");
	}

	private static void assertStartupFails(String trustedServicesJson, String expectedMessage) {
		final var context = new DcbTestContainerContextBuilder()
			.environments(Environment.TEST)
			.properties(Map.of("dcb.discovery.trusted-services-json", trustedServicesJson))
			.build();

		try {
			final var failure = assertThrows(Throwable.class, context::start,
				"the application started with an unusable trust anchor");

			assertTrue(causeChainMentions(failure, expectedMessage),
				"startup failed, but not because of the trust anchor: " + failure);
		} finally {
			if (context.isRunning()) {
				context.close();
			}
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
