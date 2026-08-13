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
class DiscoveryServicePropertiesBindingTests {

	private static final String PREFIX = "dcb.discovery.trusted-services";

	private static PropertySourcePropertyResolver resolverFor(Map<String, Object> environmentVariables) {
		final var resolver = new PropertySourcePropertyResolver(ConversionService.SHARED);

		resolver.addPropertySource(PropertySource.of("test-env", environmentVariables,
			PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE));

		return resolver;
	}

	@Test
	void theScalarsBindFromEnvironmentVariables() {
		final var resolver = resolverFor(Map.of(
			"DCB_DISCOVERY_ENABLED", "true",
			"DCB_DISCOVERY_AUDIENCE", "some-dcb",
			"DCB_DISCOVERY_MAX_ASSERTION_LIFETIME", "PT90S"));

		assertEquals("true", resolver.getProperty("dcb.discovery.enabled", String.class).orElse(null));
		assertEquals("some-dcb", resolver.getProperty("dcb.discovery.audience", String.class).orElse(null));
		assertEquals("PT90S",
			resolver.getProperty("dcb.discovery.max-assertion-lifetime", String.class).orElse(null));
	}

	/**
	 * The limitation that makes trusted-services-json necessary, pinned so nobody
	 * "simplifies" the JSON route away on the assumption that indexed environment
	 * variables would do.
	 *
	 * Micronaut maps an environment variable onto every dot/hyphen permutation of its
	 * name, but an index stays a DOT segment. List binding needs the bracket form, so
	 * a list of objects cannot be expressed as environment variables at all.
	 */
	@Test
	void anIndexedListDoesNotBindFromEnvironmentVariables() {
		final var resolver = resolverFor(Map.of(
			"DCB_DISCOVERY_TRUSTED_SERVICES_0_SERVICE_ID", "first-service",
			"DCB_DISCOVERY_TRUSTED_SERVICES_0_ISSUER", "https://first.example.org"));

		assertNull(resolver.getProperty(PREFIX + "[0].service-id", String.class).orElse(null),
			"indexed environment variables now bind: trusted-services-json may be redundant");

		// What it produces instead: the dot form, which list binding does not read.
		assertEquals("first-service",
			resolver.getProperty(PREFIX + ".0.service-id", String.class).orElse(null));
	}

	@Test
	void theWholeListCanBeSuppliedAsJsonInOneEnvironmentVariable() {
		final var properties = new DiscoveryServiceProperties();

		properties.setTrustedServicesJson("""
			[
			  {"service-id": "first-service",
			   "issuer": "https://first.example.org",
			   "jwks-uri": "https://first.example.org/jwks.json"},
			  {"service-id": "second-service",
			   "issuer": "https://second.example.org",
			   "jwks-uri": "https://second.example.org/jwks.json"}
			]
			""");

		final var services = properties.getTrustedServices();

		assertEquals(2, services.size());
		assertEquals("first-service", services.get(0).getServiceId());
		assertEquals("https://first.example.org", services.get(0).getIssuer());
		assertEquals("https://first.example.org/jwks.json", services.get(0).getJwksUri().toString());
		assertEquals("second-service", services.get(1).getServiceId());
	}

	/** Inline JWKS is how an egress-less deployment onboards, so it must survive the JSON route too. */
	@Test
	void anInlineJwksSurvivesTheJsonRoute() {
		final var properties = new DiscoveryServiceProperties();

		properties.setTrustedServicesJson(
			"""
			[{"service-id": "offline-service", "issuer": "https://offline.example.org",
			  "jwks": {"keys": [{"kty": "RSA", "kid": "offline-1"}]}}]
			""");

		final var service = properties.getTrustedServices().get(0);

		assertEquals("offline-service", service.getServiceId());
		assertNull(service.getJwksUri());
		assertTrue(service.getJwks().containsKey("keys"), "the inline JWKS was dropped");
	}

	/**
	 * Both sources feed one list, so DiscoveryTrustedServiceStore's duplicate-issuer
	 * check covers a collision BETWEEN them rather than only within one.
	 */
	@Test
	void theYamlListAndTheJsonListAreBothHonoured() {
		final var properties = new DiscoveryServiceProperties();

		final var fromYaml = new DiscoveryServiceProperties.TrustedService();
		fromYaml.setServiceId("yaml-service");
		fromYaml.setIssuer("https://yaml.example.org");
		fromYaml.setJwks(Map.of("keys", List.of()));

		properties.setTrustedServices(new java.util.ArrayList<>(List.of(fromYaml)));
		properties.setTrustedServicesJson(
			"""
			[{"service-id": "json-service", "issuer": "https://json.example.org",
			  "jwks-uri": "https://json.example.org/jwks.json"}]
			""");

		assertEquals(List.of("yaml-service", "json-service"),
			properties.getTrustedServices().stream()
				.map(DiscoveryServiceProperties.TrustedService::getServiceId)
				.toList());
	}

	/** Absent or empty is "not configured", not an error. Only malformed is fatal. */
	@Test
	void anAbsentJsonListIsNotAnError() {
		final var properties = new DiscoveryServiceProperties();

		properties.setTrustedServicesJson("   ");

		assertEquals(List.of(), properties.getTrustedServices());
	}

	/**
	 * Fail at startup, loudly. A trust anchor that quietly binds nothing is
	 * indistinguishable from a deployment nobody configured.
	 */
	@Test
	void malformedJsonRefusesToStart() {
		final var properties = new DiscoveryServiceProperties();

		assertThrows(IllegalArgumentException.class,
			() -> properties.setTrustedServicesJson("{not json"));

		assertThrows(IllegalArgumentException.class,
			() -> properties.setTrustedServicesJson("[\"a bare string, not an object\"]"));
	}
}
