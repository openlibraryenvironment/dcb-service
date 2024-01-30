package org.olf.dcb.core.interaction;

import static org.olf.dcb.core.interaction.HttpProtocolToLogMessageMapper.toLogOutput;

import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class UnexpectedResponseException extends RuntimeException {
	public UnexpectedResponseException(MutableHttpRequest<?> request,
		HttpClientResponseException responseError) {

		super("Unexpected HTTP response from: %s %s\nResponse: %s".formatted(
			request.getMethodName(),
			request.getPath(),
			toLogOutput(responseError.getResponse())));
	}
}
