package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.Map;

import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class UnexpectedHttpResponseProblem {
	public static ThrowableProblem unexpectedResponseProblem(
		HttpClientResponseException responseException, String hostLmsCode) {

		final var jsonResponseBody = InterpretBody(responseException);

		return Problem.builder()
			.withTitle("Unexpected response from Host LMS: \"%s\"".formatted(hostLmsCode))
			.with("responseStatusCode", getValue(responseException.getStatus(), HttpStatus::getCode))
			.with("responseBody", jsonResponseBody)
			.build();
	}

	private static Object InterpretBody(HttpClientResponseException responseException) {
		return responseException.getResponse().getBody(Argument.of(Map.class)).orElse(Map.of());
	}
}
