package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

@DcbTest
@Property(name = "dcb.requests.preflight-checks.pickup-location.enabled", value = "false")
class DisablingPreflightCheckTests {
	@Inject
	private Collection<PreflightCheck> preflightChecks;

	@Test
	void pickupLocationPreflightChecksShouldBeDisabled() {
		assertThat("Should not be in collection", preflightChecks,
			not(hasItem(instanceOf(PickupLocationPreflightCheck.class))));
	}
}
