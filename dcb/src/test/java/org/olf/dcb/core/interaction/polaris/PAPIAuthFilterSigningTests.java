package org.olf.dcb.core.interaction.polaris;

import static java.lang.Boolean.FALSE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.interaction.polaris.PolarisConstants.HMAC_SHA1_ALGORITHM;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.polaris.PAPIAuthFilter.PatronAuthToken;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronCredentials;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Each PAPI request is signed with its own secret. Polaris is mocked: the patron authenticator
 * answers each barcode with a secret of its own, and every signature is recomputed here.
 */
class PAPIAuthFilterSigningTests {
	private static final String ACCESS_ID = "dcb";
	private static final String ACCESS_KEY = "access-key";
	private static final String PATRON_PATH = "/PAPIService/REST/public/v1/1033/100/1/patron/%s";

	private PAPIAuthFilter filter;

	@BeforeEach
	void beforeEach() {
		final var client = mock(PolarisLmsClient.class);
		when(client.defaultResolve(any())).thenAnswer(call -> URI.create("https://polaris.example" + call.getArgument(0)));
		when(client.exchangeWithoutAuthRetry(any(), eq(PatronAuthToken.class), any()))
			.thenAnswer(call -> {
				final MutableHttpRequest<?> request = call.getArgument(0);
				final var barcode = request.getBody(PatronCredentials.class).orElseThrow().getBarcode();

				// Answers arrive out of order, on other threads, as they do from a real server
				return Mono.delay(Duration.ofMillis(barcode.hashCode() & 7), Schedulers.parallel())
					.thenReturn(HttpResponse.ok(PatronAuthToken.builder()
						.papiErrorCode(0)
						.accessToken("token-" + barcode)
						.accessSecret(secretFor(barcode))
						.build()));
			});

		final var config = PolarisConfig.builder()
			.accessId(ACCESS_ID)
			.accessKey(ACCESS_KEY)
			.build();

		filter = new PAPIAuthFilter(client, config, mock(PolarisTokenCache.class));
	}

	@Test
	void aRequestWithoutAPatronIsNotSignedWithTheSecretOfThePatronBeforeIt() {
		signAsPatron("patron-a");

		final var signed = filter.ensurePatronAuth(HttpRequest.GET(PATRON_PATH.formatted("patron-b")),
			PatronCredentials.builder().build(), FALSE).block();

		assertThat(signed.getHeaders().get(HttpHeaders.AUTHORIZATION), is(expectedSignature(signed, "")));
	}

	@Test
	void requestsSignedAtOnceAreEachSignedWithTheirOwnPatronsSecret() {
		final var barcodes = Flux.range(0, 200).map(index -> "patron-" + index).collectList().block();

		final List<String> mismatches = Flux.fromIterable(barcodes)
			.flatMap(barcode -> patronRequest(barcode)
				.filter(signed -> !signed.getHeaders().get(HttpHeaders.AUTHORIZATION)
					.equals(expectedSignature(signed, secretFor(barcode))))
				.map(signed -> barcode), barcodes.size())
			.collectList()
			.block();

		assertThat(mismatches, hasSize(0));
	}

	private MutableHttpRequest<?> signAsPatron(String barcode) {
		return patronRequest(barcode).block();
	}

	private Mono<MutableHttpRequest<?>> patronRequest(String barcode) {
		return filter.ensurePatronAuth(HttpRequest.GET(PATRON_PATH.formatted(barcode)),
			PatronCredentials.builder().barcode(barcode).password("pin").build(), FALSE);
	}

	private static String secretFor(String barcode) {
		return "secret-" + barcode;
	}

	private static String expectedSignature(MutableHttpRequest<?> request, String secret) {
		try {
			final var mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
			mac.init(new SecretKeySpec(ACCESS_KEY.getBytes(StandardCharsets.UTF_8), HMAC_SHA1_ALGORITHM));

			final var data = request.getMethod().name() + request.getUri() + request.getHeaders().get("PolarisDate") + secret;

			return "PWS " + ACCESS_ID + ":"
				+ Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
