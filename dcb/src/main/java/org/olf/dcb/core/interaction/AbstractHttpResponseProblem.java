package org.olf.dcb.core.interaction;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.utils.PropertyAccessUtils;
import org.zalando.problem.AbstractThrowableProblem;

import java.net.URI;
import java.util.Map;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static org.zalando.problem.Status.INTERNAL_SERVER_ERROR;

@Slf4j
public class AbstractHttpResponseProblem extends AbstractThrowableProblem {
	protected AbstractHttpResponseProblem(String title, String detail,
		HttpClientResponseException responseException, HttpRequest<?> request) {

		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			title, INTERNAL_SERVER_ERROR, detail, null, null,
			determineParameters(responseException, request, null));
	}

	protected AbstractHttpResponseProblem(String title, String detail,
		HttpClientResponseException httpClientResponseException, HttpRequest<?> request,
		Map<String, Object> additionalData) {

		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			title, INTERNAL_SERVER_ERROR, detail, null, null,
			determineParameters(httpClientResponseException, request, additionalData));
	}

	// Used as a fallback when we couldn't cast as a HttpClientResponseException
	protected AbstractHttpResponseProblem(String title, String detail,
		Throwable throwable, HttpRequest<?> request) {

		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			title, INTERNAL_SERVER_ERROR, detail, null, null,
			determineParameters(throwable, request));
	}

	private static Map<String, Object> determineParameters(
		HttpClientResponseException responseException,
		HttpRequest<?> request,
		Map<String, Object> additionalData) {

		return Map.of(
			"responseStatusCode", getValue(responseException,
				HttpClientResponseException::getStatus, HttpStatus::getCode, "Unknown"),
			"responseBody", interpretResponseBody(responseException),
			"requestMethod", PropertyAccessUtils.getValue(request, HttpRequest::getMethodName, "Unknown"),
			"requestUrl", getValue(request, HttpRequest::getUri, URI::toString, "Unknown"),
			"requestBody", interpretRequestBody(request),
			"httpVersion", getValue(request, HttpRequest::getHttpVersion, HttpVersion::name, "Unknown"),
			"additionalData", additionalData != null ? additionalData : "No additionalData"
		);
	}

	private static Map<String, Object> determineParameters(
		Throwable throwable, HttpRequest<?> request) {

		return Map.of(
			"errorMessage", getValue(throwable, Throwable::getMessage, "Unknown"),
			"errorLocalizedMessage", getValue(throwable, Throwable::getLocalizedMessage, "Unknown"),
			"cause", getValue(throwable, Throwable::getCause, "Unknown"),
			"requestMethod", PropertyAccessUtils.getValue(request, HttpRequest::getMethodName, "Unknown"),
			"requestUrl", getValue(request, HttpRequest::getUri, URI::toString, "Unknown"),
			"requestBody", interpretRequestBody(request),
			"httpVersion", getValue(request, HttpRequest::getHttpVersion, HttpVersion::name, "Unknown")
		);
	}

	private static Object interpretResponseBody(HttpClientResponseException responseException) {
		final var response = getValueOrNull(responseException, HttpClientResponseException::getResponse);

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
