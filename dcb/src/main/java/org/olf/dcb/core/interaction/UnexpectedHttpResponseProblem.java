package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import org.zalando.problem.Problem;
import org.zalando.problem.ThrowableProblem;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class UnexpectedHttpResponseProblem {
	public static ThrowableProblem unexpectedResponseProblem(
		HttpClientResponseException responseException, String hostLmsCode) {

		return Problem.builder()
			.withTitle("Unexpected response from Host LMS: \"%s\"".formatted(hostLmsCode))
			.with("responseStatusCode", getValue(responseException.getStatus(), HttpStatus::getCode))
			.build();
	}
}
