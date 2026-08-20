package org.olf.dcb.core.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;

/**
 * Insights is on unless a deployment turns it off. The other half of the switch is
 * {@link InsightsDisabledTests}, which needs its own context and therefore its own class.
 */
@DcbTest
class InsightsToggleTests {

	@Inject
	private ApplicationContext context;

	@Test
	void theSurfaceIsPresentByDefault() {
		assertTrue(context.containsBean(InsightsController.class),
			"Insights should be on unless a deployment turns it off");
	}
}
