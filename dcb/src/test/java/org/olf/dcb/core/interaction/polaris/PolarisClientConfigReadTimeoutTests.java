package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.Delay.milliseconds;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.test.mockserver.MockServerMicronautTest;

// Proves -- does not assume -- that PolarisClientConfig's read-timeout override (120s) is
// actually applied by the @Client(configuration = PolarisClientConfig.class) client. This is
// the established extends-DefaultHttpClientConfiguration pattern the FOLIO fix now mirrors.
// The global read timeout is forced to 1s and the response delayed 2s, with a contrast so the
// result can't be a false pass:
//   - a DEFAULT-configured client TIMES OUT (confirming the 2s delay really exceeds the 1s global);
//   - the Polaris-configured client STILL COMPLETES (confirming its 120s override wins).
@Property(name = "micronaut.http.client.read-timeout", value = "1s")
@MockServerMicronautTest
class PolarisClientConfigReadTimeoutTests {

	@Inject
	@Client(configuration = PolarisClientConfig.class)
	HttpClient polarisClient;

	@Inject
	@Client("https://polaris-slow-host")
	HttpClient defaultClient;

	@Test
	void appliesPolarisReadTimeoutNotTheGlobalDefault(MockServerClient mockServerClient) {
		mockServerClient
			.when(request()
				.withMethod("GET")
				.withPath("/slow")
				.withHeader("host", "polaris-slow-host"))
			.respond(response()
				.withStatusCode(200)
				.withBody("ok")
				.withDelay(milliseconds(2000)));

		// Contrast: the default client (1s read timeout) times out on the 2s response, so we
		// know the delay genuinely exceeds the global -- the Polaris pass below can't be a fluke.
		assertThrows(HttpClientException.class, () -> Mono.from(
			defaultClient.retrieve(HttpRequest.GET("/slow"), String.class)).block());

		// Proof: the Polaris client's 120s override lets it wait out the same 2s delay.
		final String body = Mono.from(polarisClient.retrieve(
			HttpRequest.GET("https://polaris-slow-host/slow"), String.class)).block();

		assertThat(body, is("ok"));
	}
}
