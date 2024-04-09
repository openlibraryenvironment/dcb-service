package org.olf.dcb.core.interaction;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class CannotPlaceRequestProblem extends AbstractHttpResponseProblem {
	public CannotPlaceRequestProblem(String title,
		HttpClientResponseException responseException, HttpRequest<?> request) {

		super(title, responseException, request);
	}
}
