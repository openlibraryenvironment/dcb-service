package org.olf.dcb.core.interaction;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import org.zalando.problem.ThrowableProblem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class UnexpectedHttpResponseProblem extends AbstractHttpResponseProblem {
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

	private UnexpectedHttpResponseProblem(Throwable throwable,
		HttpRequest<?> request, String hostLmsCode) {

		super(determineTitle(hostLmsCode, request), null, throwable, request);
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
