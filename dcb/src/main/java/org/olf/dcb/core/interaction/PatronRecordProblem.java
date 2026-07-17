package org.olf.dcb.core.interaction;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

import java.util.Map;

public class PatronRecordProblem extends AbstractHttpResponseProblem {

	public PatronRecordProblem(
		String hostLmsCode, HttpRequest<?> request,
		HttpClientResponseException httpClientResponseException,
		Map<String, Object> additionalData) {

		super(hostLmsCode + buildTitle(), buildDetail(), httpClientResponseException, request, additionalData);
	}

	private static String buildTitle() {
		return " XCirc Error: There is a problem with the patron's library record";
	}

	private static String buildDetail() {
		return "XCirc rejected the request because of the patron record, not the item. "
			+ "Compare currentHoldCount against MAX HOLDS for the patron's P TYPE, then "
			+ "check the patron's expiry date, manual and automatic blocks, fines against "
			+ "the block threshold, and whether the patron's P TYPE and PAT AGENCY "
			+ "combination is permitted to place holds in the Patron Blocks table. "
			+ "Note that MAX HOLDS can only be overridden by staff in the Sierra Desktop "
			+ "Application (permission 92) - the API provides no override.";
	}
}
