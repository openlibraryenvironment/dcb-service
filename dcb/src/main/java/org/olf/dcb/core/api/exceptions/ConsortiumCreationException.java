package org.olf.dcb.core.api.exceptions;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class ConsortiumCreationException extends HttpClientResponseException {
	// This throws a 400 and a contextual error message to indicate that the user is providing 'bad data'.
	// It is triggered when server-side validation fails on an uploaded mapping file.
	public ConsortiumCreationException(String message) {
		super(message, HttpResponse.badRequest());
	}
}
