package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.ReadTimeoutException;

class HostLmsSierraApiClientFailureTests {
	@Test
	void identifiesReadTimeoutsForRetryReporting() {
		final var failure = HostLmsSierraApiClient.requestFailureProblem(
			ReadTimeoutException.TIMEOUT_EXCEPTION,
			HttpRequest.POST("/iii/sierra-api/v6/token", ""), "JEFFERSON_COUNTY");

		assertThat(failure, instanceOf(SierraReadTimeoutProblem.class));
		final var timeout = (SierraReadTimeoutProblem) failure;
		assertThat(timeout.getHostLmsCode(), is("JEFFERSON_COUNTY"));
		assertThat(timeout.getRequestMethod(), is("POST"));
		assertThat(timeout.getRequestPath(), is("/iii/sierra-api/v6/token"));
	}

	@Test
	void keepsOtherRequestFailuresOnTheExistingErrorPath() {
		final var failure = HostLmsSierraApiClient.requestFailureProblem(
			new IllegalStateException("connection closed"),
			HttpRequest.GET("/iii/sierra-api/v6/items"), "JEFFERSON_COUNTY");

		assertThat(failure, instanceOf(UnexpectedHttpResponseProblem.class));
	}
}
