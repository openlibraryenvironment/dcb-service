package org.olf.dcb.core.interaction;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.zalando.problem.ThrowableProblem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;

public class UnexpectedHttpResponseProblem extends AbstractHttpResponseProblem {
	// A request that timed out may still have been carried out. This problem keeps no cause, so a
	// timeout survives the wrapping only as this parameter.
	public static final String OUTCOME = "outcome";
	public static final String UNKNOWN_TIMEOUT = "UNKNOWN_TIMEOUT";

	public static <T> ThrowableProblem unexpectedResponseProblem(
		HttpClientResponseException responseException, HttpRequest<T> request, String hostLmsCode) {

		return new UnexpectedHttpResponseProblem(responseException, request, hostLmsCode);
	}

	private UnexpectedHttpResponseProblem(HttpClientResponseException responseException,
		HttpRequest<?> request, String hostLmsCode) {

		super(determineTitle(hostLmsCode, request), null, responseException, request);
	}

	public static <T> ThrowableProblem unexpectedResponseProblem(
		Throwable throwable, HttpRequest<T> request, String hostLmsCode) {

		return new UnexpectedHttpResponseProblem(throwable, request, hostLmsCode);
	}

	protected UnexpectedHttpResponseProblem(Throwable throwable,
		HttpRequest<?> request, String hostLmsCode) {

		super(determineTitle(hostLmsCode, request), null, throwable, request,
			isTimeout(throwable) ? Map.of(OUTCOME, UNKNOWN_TIMEOUT) : Map.of());
	}

	/**
	 * Whether the remote system may have acted on a request that failed with this error: a timeout
	 * anywhere in its cause chain, or a problem recording one.
	 */
	public static boolean hasUnknownOutcome(Throwable error) {
		return anyInCauseChain(error, cause -> isTimeoutItself(cause)
			|| (cause instanceof ThrowableProblem problem
				&& problem.getParameters() != null
				&& UNKNOWN_TIMEOUT.equals(problem.getParameters().get(OUTCOME))));
	}

	private static boolean isTimeout(Throwable error) {
		return anyInCauseChain(error, UnexpectedHttpResponseProblem::isTimeoutItself);
	}

	private static boolean isTimeoutItself(Throwable error) {
		return error instanceof ReadTimeoutException || error instanceof TimeoutException;
	}

	private static boolean anyInCauseChain(Throwable error, Predicate<Throwable> test) {
		final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

		for (var current = error; current != null && seen.add(current); current = current.getCause()) {
			if (test.test(current)) return true;
		}

		return false;
	}

	private static String determineTitle(String hostLmsCode, HttpRequest<?> request) {
		if (isEmpty(hostLmsCode) && request == null) {
			return "Unexpected response received for unknown request or Host LMS";
		}

		if (isNotEmpty(hostLmsCode)) {
			return "Unexpected response from Host LMS: \"%s\"".formatted(hostLmsCode);
		} else {
			return "Unexpected response from: %s %s".formatted(
				getValueOrNull(request, HttpRequest::getMethodName),
				getValueOrNull(request, HttpRequest::getPath));
		}
	}

	@Override
	public String toString() {
		return String.format("%s\nParameters:%s ", getTitle(), getParameters());
	}
}
