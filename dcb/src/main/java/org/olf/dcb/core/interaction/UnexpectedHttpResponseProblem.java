package org.olf.dcb.core.interaction;

import static io.micronaut.core.util.StringUtils.isEmpty;
import static io.micronaut.core.util.StringUtils.isNotEmpty;

import org.olf.dcb.utils.PropertyAccessUtils;
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

	private static String determineTitle(String hostLmsCode, HttpRequest<?> request) {
		if (isEmpty(hostLmsCode) && request == null) {
			return "Unexpected response received for unknown request or Host LMS";
		}

		if (isNotEmpty(hostLmsCode)) {
			return "Unexpected response from Host LMS: \"%s\"".formatted(hostLmsCode);
		} else {
			return "Unexpected response from: %s %s".formatted(
				PropertyAccessUtils.getValueOrNull(request, HttpRequest::getMethodName),
				PropertyAccessUtils.getValueOrNull(request, HttpRequest::getPath));
		}
	}
}
