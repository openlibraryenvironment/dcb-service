package org.olf.dcb.core.interaction;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrDefault;
import static org.zalando.problem.Status.INTERNAL_SERVER_ERROR;

import java.net.URI;
import java.util.Map;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class UnexpectedHttpResponseProblem extends AbstractThrowableProblem {
	public static <T> ThrowableProblem unexpectedResponseProblem(
		HttpClientResponseException responseException, HttpRequest<T> request, String hostLmsCode) {

		return new UnexpectedHttpResponseProblem(responseException, request, hostLmsCode);
	}

	private UnexpectedHttpResponseProblem(HttpClientResponseException responseException,
		HttpRequest<?> request, String hostLmsCode) {

		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			determineTitle(hostLmsCode, request), INTERNAL_SERVER_ERROR, null, null, null,
			determineParameters(responseException, request));
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

	private static Map<String, Object> determineParameters(
		HttpClientResponseException responseException, HttpRequest<?> request) {

		return Map.of(
			"responseStatusCode", getValue(responseException.getStatus(), HttpStatus::getCode),
			"responseBody", interpretResponseBody(responseException),
			"requestMethod", getValueOrDefault(request, HttpRequest::getMethodName, "Unknown"),
			"requestUrl", getValue(request, HttpRequest::getUri, URI::toString, "Unknown"),
			"requestBody", interpretRequestBody(request)
		);
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
}
