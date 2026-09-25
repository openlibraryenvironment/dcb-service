package org.olf.dcb.core.interaction.sierra;

import org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.ReadTimeoutException;

/**
 * A Sierra request that received no response before the configured read timeout.
 *
 * The marker lets retry reporting distinguish a transient remote outage from an
 * application fault without changing the failure returned to the caller.
 */
public class SierraReadTimeoutProblem extends UnexpectedHttpResponseProblem {
	private final String hostLmsCode;
	private final String requestMethod;
	private final String requestPath;

	public SierraReadTimeoutProblem(ReadTimeoutException timeout, HttpRequest<?> request,
		String hostLmsCode) {

		super(timeout, request, hostLmsCode);
		this.hostLmsCode = hostLmsCode;
		this.requestMethod = request.getMethodName();
		this.requestPath = request.getPath();
	}

	public String getHostLmsCode() {
		return hostLmsCode;
	}

	public String getRequestMethod() {
		return requestMethod;
	}

	public String getRequestPath() {
		return requestPath;
	}
}
