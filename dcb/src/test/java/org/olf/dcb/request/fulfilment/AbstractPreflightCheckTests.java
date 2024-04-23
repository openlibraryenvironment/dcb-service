package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;

public abstract class AbstractPreflightCheckTests {
	protected static PlacePatronRequestCommand placeRequestCommand(
		String pickupLocationCode, String pickupLocationContext, String requestorHostLmsCode) {

		return PlacePatronRequestCommand.builder()
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder()
				.context(pickupLocationContext)
				.code(pickupLocationCode)
				.build())
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(requestorHostLmsCode)
				.build())
			.build();
	}

	protected static Matcher<CheckResult> failedCheck(
		String expectedCode, String expectedDescription) {

		return allOf(
			hasProperty("passed", is(false)),
			hasProperty("failureCode", is(expectedCode)),
			hasProperty("failureDescription", is(expectedDescription))
		);
	}

	protected static Matcher<CheckResult> failedCheck(String expectedCode) {
		return allOf(
			hasProperty("passed", is(false)),
			hasProperty("failureCode", is(expectedCode))
		);
	}

	protected static Matcher<CheckResult> passedCheck() {
		return hasProperty("passed", is(true));
	}
}
