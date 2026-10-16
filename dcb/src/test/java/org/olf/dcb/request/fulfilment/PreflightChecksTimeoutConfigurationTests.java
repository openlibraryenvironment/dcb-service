package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.PropertySource;

/**
 * The preflight bound is set by an environment variable where there is no config file to mount, and
 * falls back to 55 seconds without one. Bound the way {@link PreflightChecksTimeout} binds it: the
 * environment's property as a Duration and as text.
 */
class PreflightChecksTimeoutConfigurationTests {
	@Test
	void isFiftyFiveSecondsWhenTheVariableIsNotSet() {
		assertThat(timeoutWith(Map.of()).value(), is(Duration.ofSeconds(55)));
	}

	@Test
	void isSetByTheEnvironmentVariable() {
		assertThat(timeoutWith(Map.of("DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT", "PT90S")).value(),
			is(Duration.ofSeconds(90)));
	}

	@Test
	void aBlankOrUnreadableVariableRefusesToStart() {
		for (final var unusable : new String[] { "", "  ", "forty-five" }) {
			final var failure = assertThrows(IllegalStateException.class,
				() -> timeoutWith(Map.of("DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT", unusable)));

			assertThat(failure.getMessage(), containsString("is not a duration"));
		}
	}

	@Test
	void aTimeoutThatIsNotPositiveRefusesToStart() {
		assertThrows(IllegalStateException.class,
			() -> timeoutWith(Map.of("DCB_REQUESTS_PREFLIGHT_CHECKS_TIMEOUT", "PT0S")));
	}

	@Test
	void isCheckedAtStartupNotOnTheFirstPlacement() {
		assertThat(PreflightChecksTimeout.class.getAnnotation(Context.class), notNullValue());
	}

	private static PreflightChecksTimeout timeoutWith(Map<String, Object> environmentVariables) {
		try (var context = ApplicationContext.builder()
			.environmentPropertySource(false)
			.propertySources(PropertySource.of("test-env", environmentVariables,
				PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE, PropertySource.Origin.of("test-env")))
			.build()) {

			final var environment = context.getEnvironment().start();

			return new PreflightChecksTimeout(
				environment.getProperty(PreflightChecksTimeout.PROPERTY, Duration.class).orElse(null),
				environment.getProperty(PreflightChecksTimeout.PROPERTY, String.class).orElse(null));
		}
	}
}
