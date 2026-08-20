package org.olf.dcb.core.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

/**
 * The kill switch on the Insights surface. Asserted rather than documented because
 * {@code @Requires} goes stale silently - a typo in the property name leaves it reading as
 * though it works, and nothing else in the suite would fail.
 *
 * <p><b>The property is on the CLASS, not the test method.</b> Bean requirements are evaluated
 * when the context starts, so a method-level property is read back correctly and changes
 * nothing: the assertion then passes against a context built with Insights enabled. That is a
 * test that cannot fail, which is worse than no test.
 */
@DcbTest
@Property(name = "dcb.insights.enabled", value = "false")
class InsightsDisabledTests {

	@Inject
	private ApplicationContext context;

	@Test
	void theSurfaceDisappearsWhenTurnedOff() {
		// The precondition, so a property that never reached the context reports itself rather
		// than looking like a broken @Requires.
		assertEquals("false", context.getProperty("dcb.insights.enabled", String.class)
			.orElse("<absent>"), "the test property did not reach the context");

		// Not "returns 403" and not "returns an empty body" - the bean is never created, so the
		// routes are never registered and there is nothing behind them left to reach.
		assertFalse(context.containsBean(InsightsController.class),
			"dcb.insights.enabled=false must remove the whole surface, not merely hide it");
	}
}
