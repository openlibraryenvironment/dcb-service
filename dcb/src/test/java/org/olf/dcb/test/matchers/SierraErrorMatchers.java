package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import services.k_int.interaction.sierra.SierraError;

public class SierraErrorMatchers {
	public static Matcher<SierraError> isServerError() {
		return allOf(
			hasProperty("name", is("Internal server error")),
			hasProperty("description", is("Invalid configuration")),
			hasProperty("code", is(109)),
			hasProperty("specificCode", is(0)));
	}

	public static Matcher<SierraError> isBadJsonError() {
		return allOf(
			hasProperty("name", is("Bad JSON/XML Syntax")),
			hasProperty("description", is("Please check that the JSON fields/values are of the expected JSON data types")),
			hasProperty("code", is(130)),
			hasProperty("specificCode", is(0)));
	}

	public static SierraError getResponseBody(HttpClientResponseException exception) {
		return exception.getResponse()
			.getBody(SierraError.class)
			.orElse(null);
	}
}
