package org.olf.dcb.core.interaction.folio;

import org.olf.dcb.core.interaction.CannotPlaceRequestProblem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

/**
 * A FOLIO tenant's request policy refused the request type mod-dcb asked mod-circulation for.
 * Every transaction role can meet this, and only the placing method knows whose staff can act on
 * it, so it is raised as a plain placement failure and the supplier placement turns it into a
 * supplier refusal.
 */
final class FolioRequestPolicyRefusedProblem extends CannotPlaceRequestProblem {
	private final String requestType;
	private final String patronGroup;
	private final String refusal;
	private final transient HttpClientResponseException responseException;
	private final transient HttpRequest<?> request;

	FolioRequestPolicyRefusedProblem(String hostLmsCode, String requestType, String patronGroup,
		String refusal, HttpClientResponseException responseException, HttpRequest<?> request) {

		super(hostLmsCode, reason(hostLmsCode, requestType, patronGroup), responseException, request);

		this.requestType = requestType;
		this.patronGroup = patronGroup;
		this.refusal = refusal;
		this.responseException = responseException;
		this.request = request;
	}

	static String reason(String hostLmsCode, String requestType, String patronGroup) {
		return "%s's request policy does not allow a %s request for patron group \"%s\" on this item"
			.formatted(hostLmsCode, requestType, patronGroup);
	}

	String getRequestType() {
		return requestType;
	}

	String getPatronGroup() {
		return patronGroup;
	}

	/** mod-dcb's own error text, which embeds mod-circulation's refusal. */
	String getRefusal() {
		return refusal;
	}

	HttpClientResponseException getResponseException() {
		return responseException;
	}

	HttpRequest<?> getRequest() {
		return request;
	}
}
