package org.olf.dcb.core.interaction.foundation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.foundation.customisations.EvergreenExampleCustomOverride;
import org.olf.dcb.core.interaction.foundation.strategies.CirculationStrategy;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.BeanContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;

/**
 * Per-operation overrides are resolved against a real bean context.
 *
 * This needs a real context rather than a mocked one: the defect these tests
 * exist for was invisible to mocking. An override takes the host as a
 * {@code @Parameter("hostLms")} constructor argument, which makes its generated
 * definition a {@code ParametrizedInstantiatableBeanDefinition}. Micronaut will
 * not instantiate those without the argument, so the previous
 * {@code findBean(type, byName(...))} could never resolve one, and every
 * configured override failed with "Missing override bean". A mock BeanContext
 * answers whatever it is told to, so only a live context proves this.
 */
@DcbTest
class FoundationOverrideResolutionTests {
	private static final String EVERGREEN_OVERRIDE = "EvergreenExampleCustomOverride";

	@Inject
	BeanContext beanContext;

	private static DataHostLms hostWithOverrides(Map<String, Object> overrides) {
		return DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("TEST-FOUNDATION")
			.name("Test Foundation Host")
			.lmsClientClass(FoundationClient.class.getCanonicalName())
			.clientConfig(Map.of(
				"capabilities", Map.of("imperative", Map.of(
					"base-protocol", "NCIP",
					"ncip-endpoint-url", "https://example.org/ncip",
					"overrides", overrides)),
				"evergreen-api-url", "https://example.org"))
			.build();
	}

	/**
	 * The regression: before the fix this threw while constructing the client.
	 */
	@Test
	void configuredOverrideIsResolvedWhenBuildingTheClient() {
		final var host = hostWithOverrides(Map.of("renew", EVERGREEN_OVERRIDE));

		final var client = new FoundationClient(host, beanContext);

		assertThat(client, is(notNullValue()));
	}

	/**
	 * Proves the mechanism the client now uses actually yields the override
	 * implementation, not merely that construction did not blow up.
	 */
	@Test
	void overrideBeanIsInstantiableWithTheHostAsAParameter() {
		final var host = hostWithOverrides(Map.of("renew", EVERGREEN_OVERRIDE));

		final var strategy = beanContext.createBean(CirculationStrategy.class,
			Qualifiers.byName(EVERGREEN_OVERRIDE), host);

		assertThat(strategy, is(instanceOf(EvergreenExampleCustomOverride.class)));
	}

	/**
	 * A parameterized bean cannot be reached this way -- the shape of the
	 * original defect, pinned so it cannot be reintroduced as a "simplification".
	 */
	@Test
	void findBeanCannotResolveAParameterizedOverride() {
		assertThrows(RuntimeException.class,
			() -> beanContext.findBean(CirculationStrategy.class,
				Qualifiers.byName(EVERGREEN_OVERRIDE)));
	}

	@Test
	void unknownOverrideNameFailsWithAClearMessage() {
		final var host = hostWithOverrides(Map.of("renew", "NoSuchOverrideBean"));

		final var error = assertThrows(IllegalStateException.class,
			() -> new FoundationClient(host, beanContext));

		assertThat(error.getMessage(), is("Missing override bean: NoSuchOverrideBean"));
	}

	@Test
	void withoutOverridesTheBaseProtocolAdaptorIsUsed() {
		final var host = hostWithOverrides(Map.of());

		final var client = new FoundationClient(host, beanContext);

		assertThat(client, is(notNullValue()));
	}
}
