package org.olf.dcb.core.interaction;

import java.util.Map;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

import org.zalando.problem.ThrowableProblem;

/**
 * A supplier refused a request because of its own configuration, which its staff can change, and
 * would refuse the same request again until they do. The title names the library and the cause.
 */
public class SupplierRefusedRequestProblem extends AbstractHttpResponseProblem {
	private final String hostLmsCode;
	private final String reason;

	public SupplierRefusedRequestProblem(String hostLmsCode, String reason, String detail,
		HttpClientResponseException responseException, HttpRequest<?> request) {

		super(reason, detail, responseException, request);

		this.hostLmsCode = hostLmsCode;
		this.reason = reason;
	}

	/** A refusal the adapter already recognised, whose own title and parameters stay readable here. */
	public SupplierRefusedRequestProblem(String hostLmsCode, String reason, Throwable refusal) {
		super(reason, detailOf(refusal), refusal, null, parametersOf(refusal));

		this.hostLmsCode = hostLmsCode;
		this.reason = reason;
	}

	private static String detailOf(Throwable refusal) {
		return refusal instanceof ThrowableProblem problem ? problem.getTitle() : refusal.getMessage();
	}

	private static Map<String, Object> parametersOf(Throwable refusal) {
		return refusal instanceof ThrowableProblem problem ? problem.getParameters() : Map.of();
	}

	public String getHostLmsCode() {
		return hostLmsCode;
	}

	public String getReason() {
		return reason;
	}
}
