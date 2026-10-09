package org.olf.dcb.core.interaction;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;

import org.zalando.problem.ThrowableProblem;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.NoHostException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;

/** Why a ping failed, in the terms an operator acts on: their credential, their system, or the route to it. */
public final class PingFailure {
	public static final String REFUSED = "REFUSED";
	public static final String FAILING = "FAILING";
	public static final String UNREACHABLE = "UNREACHABLE";
	public static final String MISCONFIGURED = "MISCONFIGURED";
	public static final String UNKNOWN = "UNKNOWN";

	private PingFailure() {
	}

	public static String classify(Throwable error) {
		for (var current = error; current != null; current = next(current)) {
			final var status = statusOf(current);

			if (status != null) {
				return status == 401 || status == 403 ? REFUSED : FAILING;
			}

			if (isUnreachableType(current) || markedUnreachable(current)) {
				return UNREACHABLE;
			}
		}

		return UNKNOWN;
	}

	/** Whether the failure never reached the other system: no route, no answer in time, or no TLS session. */
	public static boolean isUnreachable(Throwable error) {
		for (var current = error; current != null; current = next(current)) {
			if (isUnreachableType(current)) {
				return true;
			}
		}

		return false;
	}

	private static Integer statusOf(Throwable error) {
		if (error instanceof HttpClientResponseException responseException) {
			return responseException.getStatus().getCode();
		}

		if (error instanceof ThrowableProblem problem) {
			final var parameters = problem.getParameters();

			if (parameters.get("responseStatusCode") instanceof Integer status) {
				return status;
			}

			if (parameters.get("causeResponseStatusCode") instanceof Integer status) {
				return status;
			}
		}

		return null;
	}

	private static boolean isUnreachableType(Throwable error) {
		return error instanceof ConnectException
			|| error instanceof NoRouteToHostException
			|| error instanceof UnknownHostException
			|| error instanceof NoHostException
			|| error instanceof SSLException
			|| error instanceof ReadTimeoutException
			|| error instanceof TimeoutException;
	}

	// A problem raised before any response keeps no cause, only this marker
	private static boolean markedUnreachable(Throwable error) {
		return error instanceof ThrowableProblem problem
			&& Boolean.TRUE.equals(problem.getParameters().get("causeUnreachable"));
	}

	private static Throwable next(Throwable error) {
		final var cause = error.getCause();

		return cause == error ? null : cause;
	}
}
