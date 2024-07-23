package org.olf.dcb.core.interaction;

import io.micronaut.http.HttpRequest;

import java.util.Map;

public class RecordIsNotAvailableProblem extends AbstractHttpResponseProblem {

	public RecordIsNotAvailableProblem(
		String hostLmsCode, HttpRequest<?> request, Throwable cause, Map<String, Object> additionalData) {

		super(hostLmsCode + buildTitle(), null, cause, request, additionalData);
	}

	private static String buildTitle() {
		return " XCirc Error: This record is not available";
	}
}
