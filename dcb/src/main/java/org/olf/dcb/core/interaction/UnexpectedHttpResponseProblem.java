package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.Map;

import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class UnexpectedHttpResponseProblem {
	public static <T> ThrowableProblem unexpectedResponseProblem(
		HttpClientResponseException responseException, HttpRequest<T> request, String hostLmsCode) {

		final var jsonResponseBody = InterpretBody(responseException);

		return Problem.builder()
			.withTitle("Unexpected response from Host LMS: \"%s\"".formatted(hostLmsCode))
			.with("responseStatusCode", getValue(responseException.getStatus(), HttpStatus::getCode))
			.with("responseBody", jsonResponseBody)
			.with("requestMethod", getValue(request, HttpRequest::getMethodName))
			.build();
	}

	private static Object InterpretBody(HttpClientResponseException responseException) {
		final var response = getValue(responseException, HttpClientResponseException::getResponse);

		final var optionalJsonBody = response.getBody(Argument.of(Map.class));

		if (optionalJsonBody.isPresent()) {
			return optionalJsonBody.get();
		}
		else {
			return response.getBody(Argument.of(String.class)).orElse("No body");
		}
	}
}
