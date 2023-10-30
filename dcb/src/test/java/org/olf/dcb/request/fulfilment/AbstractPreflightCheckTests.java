package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;

public abstract class AbstractPreflightCheckTests {

	static PlacePatronRequestCommand placeRequestCommand(String pickupLocationCode) {
		return PlacePatronRequestCommand.builder()
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder()
				.code(pickupLocationCode)
				.build())
			.build();
	}

	protected static Matcher<CheckResult> failedCheck(String expectedDescription) {
		return allOf(
			hasProperty("passed", is(false)),
			hasProperty("failureDescription", is(expectedDescription))
		);
	}

	protected static Matcher<CheckResult> passedCheck() {
		return hasProperty("passed", is(true));
	}
}
