package org.olf.dcb.core.interaction;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
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

		return Problem.builder()
			.withTitle(determineTitle(hostLmsCode, request))
			.with("responseStatusCode", getValue(responseException.getStatus(), HttpStatus::getCode))
			.with("responseBody", interpretResponseBody(responseException))
			.with("requestMethod", getValue(request, HttpRequest::getMethodName))
			.with("requestBody", interpretRequestBody(request))
			.build();
	}

	private static Object interpretResponseBody(HttpClientResponseException responseException) {
		final var response = getValue(responseException, HttpClientResponseException::getResponse);

		final var optionalJsonBody = response.getBody(Argument.of(Map.class));

		if (optionalJsonBody.isPresent()) {
			return optionalJsonBody.get();
		}
		else {
			return response.getBody(Argument.of(String.class)).orElse(noBodyMessage());
		}
	}

	private static String noBodyMessage() {
		return "No body";
	}

	private static Object interpretRequestBody(HttpRequest<?> request) {
		if (request == null) {
			return noBodyMessage();
		}

		return request.getBody(Argument.of(String.class)).orElse(noBodyMessage());
	}

	private static String determineTitle(String hostLmsCode, HttpRequest<?> request) {
		if (isEmpty(hostLmsCode) && request == null) {
			return "Unexpected response received for unknown request or Host LMS";
		}

		if (isNotEmpty(hostLmsCode)) {
			return "Unexpected response from Host LMS: \"%s\"".formatted(hostLmsCode);
		} else {
			return "Unexpected response from: %s %s".formatted(
				getValue(request, HttpRequest::getMethodName),
				getValue(request, HttpRequest::getPath));
		}
	}
}
