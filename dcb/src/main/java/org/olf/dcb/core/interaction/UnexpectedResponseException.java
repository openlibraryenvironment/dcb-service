package org.olf.dcb.core.interaction;

import static org.olf.dcb.core.interaction.HttpProtocolToLogMessageMapper.toLogOutput;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class UnexpectedResponseException extends RuntimeException {
	public UnexpectedResponseException(MutableHttpRequest<?> request,
		HttpClientResponseException responseError) {

		super("Unexpected HTTP response from: %s %s\nResponse: %s".formatted(
		 	getValue(request, HttpRequest::getMethodName),
			getValue(request, HttpRequest::getPath),
			toLogOutput(responseError.getResponse())));
	}
}
