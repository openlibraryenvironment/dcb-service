package org.olf.dcb.core.interaction;

import java.util.Map;

import org.olf.dcb.core.remediation.RemediableProblem;
import org.olf.dcb.core.remediation.RemediationCode;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

import org.zalando.problem.ThrowableProblem;

/**
 * A supplier refused a request for a reason its own staff can fix, and would refuse the same
 * request again until they do. What to do about it is in the remediation catalogue.
 */
public class SupplierRefusedRequestProblem extends AbstractHttpResponseProblem implements RemediableProblem {
	private final RemediationCode remediationCode;
	private final Map<String, String> remediationValues;

	public SupplierRefusedRequestProblem(String hostLmsCode, RemediationCode remediationCode,
		Map<String, String> remediationValues, String detail,
		HttpClientResponseException responseException, HttpRequest<?> request) {

		super("Host LMS \"%s\" refused the request: %s".formatted(hostLmsCode, remediationCode),
			detail, responseException, request);

		this.remediationCode = remediationCode;
		this.remediationValues = Map.copyOf(remediationValues);
	}

	/** A refusal the adapter already recognised, whose own title and parameters stay readable here. */
	public SupplierRefusedRequestProblem(String hostLmsCode, RemediationCode remediationCode,
		Map<String, String> remediationValues, Throwable refusal) {

		super("Host LMS \"%s\" refused the request: %s".formatted(hostLmsCode, remediationCode),
			detailOf(refusal), refusal, null, parametersOf(refusal));

		this.remediationCode = remediationCode;
		this.remediationValues = Map.copyOf(remediationValues);
	}

	private static String detailOf(Throwable refusal) {
		return refusal instanceof ThrowableProblem problem ? problem.getTitle() : refusal.getMessage();
	}

	private static Map<String, Object> parametersOf(Throwable refusal) {
		return refusal instanceof ThrowableProblem problem ? problem.getParameters() : Map.of();
	}

	@Override
	public RemediationCode remediationCode() {
		return remediationCode;
	}

	@Override
	public Map<String, String> remediationValues() {
		return remediationValues;
	}
}
