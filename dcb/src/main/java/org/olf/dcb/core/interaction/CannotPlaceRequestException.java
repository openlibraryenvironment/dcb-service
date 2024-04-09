package org.olf.dcb.core.interaction;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class CannotPlaceRequestException extends AbstractHttpResponseProblem {
	public CannotPlaceRequestException(String message,
		HttpClientResponseException responseException, HttpRequest<?> request) {

		super(message, responseException, request);
	}
}
