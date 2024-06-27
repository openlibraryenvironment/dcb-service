package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrDefault;
import static org.zalando.problem.Status.INTERNAL_SERVER_ERROR;

import java.net.URI;
import java.util.Map;

import org.zalando.problem.AbstractThrowableProblem;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AbstractHttpResponseProblem extends AbstractThrowableProblem {
	protected AbstractHttpResponseProblem(String title, String detail,
		HttpClientResponseException responseException, HttpRequest<?> request) {

		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			title, INTERNAL_SERVER_ERROR, detail, null, null,
			determineParameters(responseException, request));
	}

	private static Map<String, Object> determineParameters(
		HttpClientResponseException responseException, HttpRequest<?> request) {

		final var parameters = Map.of(
			"responseStatusCode", getValue(responseException,
				HttpClientResponseException::getStatus, HttpStatus::getCode, "Unknown"),
			"responseBody", interpretResponseBody(responseException),
			"requestMethod", getValueOrDefault(request, HttpRequest::getMethodName, "Unknown"),
			"requestUrl", getValue(request, HttpRequest::getUri, URI::toString, "Unknown"),
			"requestBody", interpretRequestBody(request),
			"httpVersion", getValue(request, HttpRequest::getHttpVersion, HttpVersion::name, "Unknown")
		);

		parameters.forEach((key, value) -> log.error("{}: {}", key, value));
		return parameters;
	}

	private static Object interpretResponseBody(HttpClientResponseException responseException) {
		final var response = getValue(responseException, HttpClientResponseException::getResponse);

		if (response == null) {
			return noBodyMessage();
		}

		final var optionalJsonBody = response.getBody(Argument.of(Map.class));

		if (optionalJsonBody.isPresent()) {
			return optionalJsonBody.get();
		} else {
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
