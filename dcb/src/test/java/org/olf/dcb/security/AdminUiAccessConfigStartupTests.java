package org.olf.dcb.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTestContainerContextBuilder;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

/**
 * The access bar exists only when its client id is set. A client id that is set but blank stops the
 * application starting, rather than leaving the bar silently off.
 */
class AdminUiAccessConfigStartupTests {

	@Test
	void withNoClientIdThereIsNoConfigAndTheBarIsOff() {
		final var context = contextWith(Map.of());

		try {
			context.start();

			assertFalse(context.containsBean(AdminUiAccessConfig.class));
			assertFalse(context.getBean(AdminUiAccessPolicy.class).isConfigured());
		} finally {
			close(context);
		}
	}

	@Test
	void aClientIdWithNoModeWarns() {
		final var context = contextWith(Map.of(AdminUiAccessConfig.CLIENT_ID, "dcb-admin"));

		try {
			context.start();

			assertEquals(AdminUiAccessPolicy.Mode.WARN, context.getBean(AdminUiAccessConfig.class).getMode());
			assertTrue(context.getBean(AdminUiAccessPolicy.class).isConfigured());
		} finally {
			close(context);
		}
	}

	@Test
	void aBlankClientIdStopsTheApplicationStarting() {
		final var context = contextWith(Map.of(AdminUiAccessConfig.CLIENT_ID, " "));

		try {
			final var failure = assertThrows(Throwable.class, context::start,
				"the application started with a blank admin UI client id");

			assertTrue(causeChainMentions(failure, "getClientId - must not be blank"), "startup failed, but not because of the client id: " + failure);
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
