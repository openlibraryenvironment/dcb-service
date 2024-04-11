package org.olf.dcb.test.clients;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import java.util.List;

import org.hamcrest.Matcher;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Value;

@Value
@Serdeable
public class ChecksFailure {
	@Nullable List<Check> failedChecks;

	@Value
	@Serdeable
	public static class Check {
		@Nullable String code;
		@Nullable String failureDescription;

		public static Matcher<Check> hasDescription(String expectedDescription) {
			return hasProperty("failureDescription", is(expectedDescription));
		}

		public static Matcher<Check> hasCode(String expectedCode) {
			return hasProperty("code", is(expectedCode));
		}
	}
}
