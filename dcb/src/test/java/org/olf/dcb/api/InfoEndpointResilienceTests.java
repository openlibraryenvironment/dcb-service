package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.interaction.koha.KohaHostLmsClient;
import org.olf.dcb.core.interaction.koha.KohaOaiPmhIngestSource;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;

/**
 * A Host LMS that cannot produce an ingest source must not take {@code /info} down with it.
 *
 * <p>Every other fixture builds a well-formed Host LMS, so nothing else here can see this.
 */
@DcbTest
@TestInstance(PER_CLASS)
class InfoEndpointResilienceTests {
	private static final String BROKEN_HOST_LMS_CODE = "BROKEN_KOHA_INFO_TESTS";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private HostLmsFixture hostLmsFixture;

	/**
	 * Declares KohaOaiPmhIngestSource but omits base-url, so constructing the source throws.
	 * The REST settings are present: this is a harvest misconfiguration, not an empty record.
	 */
	@BeforeAll
	void beforeAll() {
		hostLmsFixture.createHostLms(randomUUID(), BROKEN_HOST_LMS_CODE,
			KohaHostLmsClient.class, Optional.of(KohaOaiPmhIngestSource.class),
			Map.of(
				"api-url", "https://broken-koha.example.com",
				"client_id", "info-tests-client-id",
				"client_secret", "info-tests-client-secret",
				"sharing-library-code", "DCB-SHARING",
				"virtual-item-library-code", "DCB-VIRTUAL"));
	}

	/** Other classes read the Host LMS table; a deliberately broken row must not outlive this one. */
	@AfterAll
	void afterAll() {
		hostLmsFixture.deleteAll();
	}

	@Test
	void infoStillRespondsWhenAHostLmsCannotProduceAnIngestSource() {
		final var status = client.toBlocking()
			.exchange(HttpRequest.GET("/info"))
			.getStatus();

		assertThat(status.getCode(), is(200));
	}

	/**
	 * The surviving sources still have to be reported: containing the failure is only correct
	 * if it skips the broken row rather than truncating the response at it.
	 */
	@Test
	void infoStillCarriesItsOwnKeysAlongsideABrokenHostLms() {
		final var info = client.toBlocking().retrieve(
			HttpRequest.GET("/info"), Argument.mapOf(String.class, Object.class));

		assertThat("dcb section is absent, so the info sources did not complete",
			info.containsKey("dcb"), is(true));
	}
}
