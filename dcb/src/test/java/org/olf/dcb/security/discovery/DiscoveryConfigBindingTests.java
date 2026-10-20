package org.olf.dcb.security.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.convert.ConversionService;

/**
 * Pins the ways an operator is allowed to supply dcb.discovery.
 *
 * The docs promise an environment-variable-only onboarding route for deployments with
 * no config file to mount. A trust anchor that silently binds NOTHING fails closed and
 * looks exactly like a deployment nobody configured, so the promise gets a test.
 */
class DiscoveryConfigBindingTests {

	private static PropertySourcePropertyResolver resolverFor(Map<String, Object> environmentVariables) {
		final var resolver = new PropertySourcePropertyResolver(ConversionService.SHARED);

		resolver.addPropertySource(PropertySource.of("test-env", environmentVariables,
			PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE,
			PropertySource.Origin.of("test-env")));

		return resolver;
	}

	@Test
	void theScalarsBindFromEnvironmentVariables() {
		final var resolver = resolverFor(Map.of(
			"DCB_DISCOVERY_ENABLED", "true",
			"DCB_DISCOVERY_AUDIENCE", "some-dcb",
			"DCB_DISCOVERY_MAX_ASSERTION_LIFETIME", "PT90S",
			"DCB_DISCOVERY_TRUSTED_SERVICES_JSON", "[]"));

		assertEquals("true", resolver.getProperty("dcb.discovery.enabled", String.class).orElse(null));
		assertEquals("some-dcb", resolver.getProperty("dcb.discovery.audience", String.class).orElse(null));
		assertEquals("PT90S",
			resolver.getProperty("dcb.discovery.max-assertion-lifetime", String.class).orElse(null));
		assertEquals("[]", resolver.getProperty(DiscoveryTrustedServices.PROPERTY, String.class).orElse(null));
	}

	/**
	 * Why the list is one JSON value: Micronaut maps an environment variable onto every dot/hyphen
	 * permutation of its name, but an index stays a DOT segment, which list binding does not read.
	 */
	@Test
	void anIndexedListDoesNotBindFromEnvironmentVariables() {
		final var resolver = resolverFor(Map.of(
			"DCB_DISCOVERY_TRUSTED_SERVICES_0_SERVICE_ID", "first-service",
			"DCB_DISCOVERY_TRUSTED_SERVICES_0_ISSUER", "https://first.example.org"));

		assertNull(resolver.getProperty(DiscoveryTrustedServiceStore.YAML_LIST + "[0].service-id", String.class)
			.orElse(null), "indexed environment variables now bind: trusted-services-json may be redundant");

		assertEquals("first-service", resolver.getProperty(DiscoveryTrustedServiceStore.YAML_LIST + ".0.service-id",
			String.class).orElse(null));
	}

	@Test
	void theWholeListIsOneJsonValue() {
		final var services = DiscoveryTrustedServices.parse("""
			[
			  {"service-id": "first-service",
			   "issuer": "https://first.example.org",
			   "jwks-uri": "https://first.example.org/jwks.json",
			   "client-id": "first-client"},
			  {"service-id": "second-service",
			   "issuer": "https://second.example.org",
			   "jwks-uri": "https://second.example.org/jwks.json"}
			]
			""");

		assertEquals(2, services.size());
		assertEquals("first-service", services.get(0).serviceId());
		assertEquals("https://first.example.org", services.get(0).issuer());
		assertEquals("https://first.example.org/jwks.json", services.get(0).jwksUri().toString());
		assertEquals("first-client", services.get(0).effectiveClientId());
		assertEquals("second-service", services.get(1).effectiveClientId(),
			"an entry with no client-id calls with the client named by its service-id");
	}

	/** Inline JWKS is how an egress-less deployment onboards. */
	@Test
	void anInlineJwksIsKept() {
		final var service = DiscoveryTrustedServices.parse("""
			[{"service-id": "offline-service", "issuer": "https://offline.example.org",
			  "jwks": {"keys": [{"kty": "RSA", "kid": "offline-1"}]}}]
			""").get(0);

		assertEquals("offline-service", service.serviceId());
		assertNull(service.jwksUri());
		assertTrue(service.jwks().containsKey("keys"), "the inline JWKS was dropped");
	}

	@Test
	void anAbsentListIsNoServices() {
		assertEquals(List.of(), DiscoveryTrustedServices.parse(null));
	}

	@Test
	void aBlankListIsRefused() {
		final var failure = assertThrows(IllegalArgumentException.class, () -> DiscoveryTrustedServices.parse("   "));

		assertTrue(failure.getMessage().contains("set but blank"), failure.getMessage());
	}

	@Test
	void aBlankFieldIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> DiscoveryTrustedServices.parse("""
			[{"service-id": "x", "issuer": "https://x.example.org", "jwks-uri": "https://x.example.org/jwks.json",
			  "client-id": " "}]
			"""));
	}

	@Test
	void malformedJsonIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> DiscoveryTrustedServices.parse("{not json"));
		assertThrows(IllegalArgumentException.class,
			() -> DiscoveryTrustedServices.parse("[\"a bare string, not an object\"]"));
	}
}
