package org.olf.dcb.tracking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.convert.ConversionService;

/**
 * 56 days does not suit every consortium, so the threshold is settable on a deployment that
 * mounts no config file. That route is Micronaut's environment variable mapping, which is a
 * framework behaviour rather than ours - so it is pinned here instead of assumed.
 */
class TooLongPropertyBindingTests {
	private static final String PROPERTY = "dcb.tracking.too-long";

	@Test
	void theThresholdBindsFromAnEnvironmentVariable() {
		assertThat(resolve(Map.of("DCB_TRACKING_TOO_LONG", "7d")), is(Duration.ofDays(7)));
	}

	/** The literal carried by both application.yml and the @Value default. */
	@Test
	void theShippedDefaultIsADurationMicronautUnderstands() {
		assertThat(resolve(Map.of("DCB_TRACKING_TOO_LONG", "56d")), is(Duration.ofDays(56)));
	}

	private static Duration resolve(Map<String, Object> environmentVariables) {
		final var resolver = new PropertySourcePropertyResolver(ConversionService.SHARED);

		resolver.addPropertySource(PropertySource.of("test-env", environmentVariables,
			PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE,
			PropertySource.Origin.of("test-env")));

		return resolver.getProperty(PROPERTY, Duration.class).orElse(null);
	}
}
