package org.olf.dcb.core.interaction;

import static org.olf.dcb.core.interaction.HttpProtocolToLogMessageMapper.toLogOutput;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class UnexpectedResponseException extends RuntimeException {
	public UnexpectedResponseException(MutableHttpRequest<?> request,
		HttpClientResponseException responseError) {

		this(responseError, getValue(request, HttpRequest::getMethod),
			getValue(request, HttpRequest::getPath));
	}

	public UnexpectedResponseException(HttpClientResponseException responseError,
		HttpMethod requestMethod, String requestPath) {

		super("Unexpected HTTP response from: %s %s\nResponse: %s".formatted(
			getValue(requestMethod, HttpMethod::name), requestPath,
			toLogOutput(getValue(responseError, HttpClientResponseException::getResponse))));
	}
}
