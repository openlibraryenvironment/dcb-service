package org.olf.dcb.core.interaction;

import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class UnexpectedResponseException extends RuntimeException {
	public UnexpectedResponseException(MutableHttpRequest<?> request,
		HttpClientResponseException responseError) {

		super("Unexpected HTTP response from: %s %s"
			.formatted(request.getMethodName(), request.getPath()));
	}
}
