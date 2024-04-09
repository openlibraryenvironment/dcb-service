package org.olf.dcb.core.interaction;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class CannotPlaceRequestProblem extends AbstractHttpResponseProblem {
	public CannotPlaceRequestProblem(String hostLmsCode, String detail,
		HttpClientResponseException responseException, HttpRequest<?> request) {

		super("Cannot Place Request in Host LMS \"%s\"".formatted(hostLmsCode),
			detail, responseException, request);
	}
}
