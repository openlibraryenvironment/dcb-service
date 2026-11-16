package org.olf.dcb.core.interaction;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

/**
 * A supplier refused a request for a reason its own staff can fix, and would refuse the same
 * request again until they do. The title is the reason, written for library staff.
 */
public class SupplierRefusedRequestProblem extends AbstractHttpResponseProblem {
	private final String howToFix;

	public SupplierRefusedRequestProblem(String reason, String howToFix, String detail,
		HttpClientResponseException responseException, HttpRequest<?> request) {

		super(reason, detail, responseException, request);

		this.howToFix = howToFix;
	}

	public String getReason() {
		return getTitle();
	}

	public String getHowToFix() {
		return howToFix;
	}
}
