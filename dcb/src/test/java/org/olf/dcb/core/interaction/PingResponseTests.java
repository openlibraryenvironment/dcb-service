package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;

class PingResponseTests {
	@Test
	void shouldCallAnUnauthorisedOrForbiddenAnswerRefused() {
		assertThat(PingFailure.classify(responseException(HttpStatus.UNAUTHORIZED)), is(PingFailure.REFUSED));
		assertThat(PingFailure.classify(responseException(HttpStatus.FORBIDDEN)), is(PingFailure.REFUSED));
	}

	@Test
	void shouldCallAServerErrorFailing() {
		assertThat(PingFailure.classify(responseException(HttpStatus.INTERNAL_SERVER_ERROR)),
			is(PingFailure.FAILING));
	}

	@Test
	void shouldFindTheStatusInsideAWrappedFailure() {
		final var wrapped = new IllegalStateException("did not accept the staff login",
			responseException(HttpStatus.UNAUTHORIZED));

		assertThat(PingFailure.classify(wrapped), is(PingFailure.REFUSED));
	}

	@Test
	void shouldCallNoConnectionOrNoAnswerUnreachable() {
		assertThat(PingFailure.classify(new HttpClientException("Connect Error",
			new ConnectException("Connection refused"))), is(PingFailure.UNREACHABLE));
		assertThat(PingFailure.classify(ReadTimeoutException.TIMEOUT_EXCEPTION), is(PingFailure.UNREACHABLE));
		assertThat(PingFailure.classify(new TimeoutException()), is(PingFailure.UNREACHABLE));
	}

	@Test
	void shouldReadAStatusOrUnreachableMarkerFromAProblem() {
		final var unreachable = UnexpectedHttpResponseProblem.unexpectedResponseProblem(
			new ConnectException("Connection refused"), null, "HOST");
		final var nested = UnexpectedHttpResponseProblem.unexpectedResponseProblem(
			UnexpectedHttpResponseProblem.unexpectedResponseProblem(
				responseException(HttpStatus.SERVICE_UNAVAILABLE), null, "HOST"), null, "HOST");

		assertThat(PingFailure.classify(unreachable), is(PingFailure.UNREACHABLE));
		assertThat(PingFailure.classify(nested), is(PingFailure.FAILING));
	}

	@Test
	void shouldNotGuessAtSomethingItDoesNotRecognise() {
		assertThat(PingFailure.classify(new IllegalArgumentException("odd")), is(PingFailure.UNKNOWN));
	}

	@Test
	void shouldLeadTheSummaryWithTheStatusAndNameTheFailure() {
		assertThat(PingResponse.ok("H", "V1", Duration.ZERO).summary(), is("Status: OK (V1)"));
		assertThat(PingResponse.error("H", "V1", "no", PingFailure.REFUSED, Duration.ZERO, Map.of()).summary(),
			is("Status: ERROR (V1) - REFUSED: no"));
		assertThat(PingResponse.notImplemented("H", "why").summary(), is("Status: Not implemented - why"));
	}

	@Test
	void shouldSayWhyAnAdapterWithoutAPingHasNone() {
		final HostLmsClient adapterWithoutPing = org.mockito.Mockito.mock(HostLmsClient.class,
			org.mockito.Mockito.CALLS_REAL_METHODS);
		org.mockito.Mockito.doReturn("ANY").when(adapterWithoutPing).getHostLmsCode();

		final var response = adapterWithoutPing.ping().block();

		assertThat(response.getStatus(), is(PingResponse.NOT_IMPLEMENTED));
		assertThat(response.getAdditional(), is("This adapter has no connectivity check"));
	}

	private static HttpClientResponseException responseException(HttpStatus status) {
		return new HttpClientResponseException(status.getReason(), HttpResponse.status(status));
	}
}
