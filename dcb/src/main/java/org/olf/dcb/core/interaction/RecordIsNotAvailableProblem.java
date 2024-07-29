package org.olf.dcb.core.interaction;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

import java.util.Map;

public class RecordIsNotAvailableProblem extends AbstractHttpResponseProblem {

	public RecordIsNotAvailableProblem(
		String hostLmsCode, HttpRequest<?> request,
		HttpClientResponseException httpClientResponseException,
		Map<String, Object> additionalData) {

		super(hostLmsCode + buildTitle(), null, httpClientResponseException, request, additionalData);
	}

	private static String buildTitle() {
		return " XCirc Error: This record is not available";
	}
}
