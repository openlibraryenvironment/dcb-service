package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class NcipResponseBuilderTests {
	private final NcipSchemaValidator validator = new NcipSchemaValidator(
		NcipSchemaPath.schemaPath());
	private final NcipResponseBuilder responseBuilder = new NcipResponseBuilder();

	@Test
	void buildsValidItemShippedResponse() {
		final var xml = responseBuilder.itemShippedResponse();

		assertThat(xml, containsString("<ItemShippedResponse"));
		assertDoesNotThrow(() -> validator.validate(xml));
	}

	@Test
	void buildsValidRootProblem() {
		final var xml = responseBuilder.problem("Invalid message");

		assertThat(xml, containsString("<Problem"));
		assertThat(xml, containsString("<ProblemDetail>Invalid message</ProblemDetail>"));
		assertDoesNotThrow(() -> validator.validate(xml));
	}

	@Test
	void buildsValidItemShippedProblem() {
		final var xml = responseBuilder.itemShippedProblem("Cannot map request");

		assertThat(xml, containsString("<ItemShippedResponse"));
		assertThat(xml, containsString("<Problem"));
		assertDoesNotThrow(() -> validator.validate(xml));
	}
}
