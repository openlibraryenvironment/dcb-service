package org.olf.dcb.core.api.exceptions;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class MappingCreationException extends HttpClientResponseException {
	public MappingCreationException(String message) {
		super(message, HttpResponse.badRequest());
	}
}
