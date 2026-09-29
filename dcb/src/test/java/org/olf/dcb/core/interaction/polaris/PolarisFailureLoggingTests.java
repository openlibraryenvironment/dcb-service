package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * What a failed Polaris request leaves in the log. Staff authentication sends the staff password
 * in a reversible Basic header, and patron authentication sends the PIN in its body.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PolarisFailureLoggingTests {
	private static final String HOST = "polaris-failure-logging-tests.com";
	private static final String BASE_URL = "https://" + HOST;

	private static final String DOMAIN = "TEST";
	private static final String STAFF_USERNAME = "staff-user";
	private static final String STAFF_PASSWORD = "staff-secret-8472";
	private static final String ACCESS_KEY = "access-secret-5519";
	private static final String PATRON_BARCODE = "barcode-27182";
	private static final String PATRON_PIN = "pin-31415";

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockPolarisFixture mockPolarisFixture;
	private MockServerClient mockServerClient;

	private final Logger polarisLogger = (Logger) LoggerFactory.getLogger("org.olf.dcb.core.interaction.polaris");
	private final ListAppender<ILoggingEvent> captured = new ListAppender<>();
	private Level previousLevel;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		mockPolarisFixture = new MockPolarisFixture(HOST, mockServerClient, testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms("polaris-failure-logging", STAFF_USERNAME, STAFF_PASSWORD,
			BASE_URL, DOMAIN, "access-id", ACCESS_KEY, null, 73, Map.of("token-cache-ttl-seconds", "0"));

		// Every level: the rule is about what reaches any log, not only what reaches ERROR
		previousLevel = polarisLogger.getLevel();
		polarisLogger.setLevel(Level.TRACE);
		captured.list.clear();
		captured.start();
		polarisLogger.addAppender(captured);
	}

	@AfterEach
	void afterEach() {
		polarisLogger.detachAppender(captured);
		polarisLogger.setLevel(previousLevel);
	}

	@Test
	void shouldNotLogTheStaffCredentialWhenStaffAuthenticationFails() {
		mockPolarisFixture.mockAppServicesStaffAuthenticationAlwaysUnauthorised();

		final var response = singleValueFrom(hostLmsFixture.createClient("polaris-failure-logging").ping());

		assertThat(response.getStatus(), is("ERROR"));

		final var logged = loggedText();
		final var basicCredential = Base64.getEncoder().encodeToString(
			(DOMAIN + "\\" + STAFF_USERNAME + ":" + STAFF_PASSWORD).getBytes(StandardCharsets.UTF_8));

		assertThat(logged, hasItem(containsString("Polaris request failed")));
		assertThat(logged, not(hasItem(containsString(basicCredential))));
		assertThat(logged, not(hasItem(containsString(STAFF_PASSWORD))));
		assertThat(logged, not(hasItem(containsString(ACCESS_KEY))));
	}

	@Test
	void shouldNotLogThePatronsPinOrBarcodeWhenPatronAuthenticationFails() {
		mockPolarisFixture.mockPatronAuthenticationServerError();

		Mono.from(hostLmsFixture.createClient("polaris-failure-logging")
				.patronAuth("BASIC/BARCODE+PASSWORD", PATRON_BARCODE, PATRON_PIN))
			.onErrorResume(error -> Mono.empty())
			.block();

		final var logged = loggedText();

		assertThat(logged, hasItem(containsString("Polaris request failed")));
		assertThat(logged, not(hasItem(containsString(PATRON_PIN))));
		assertThat(logged, not(hasItem(containsString(PATRON_BARCODE))));
	}

	@Test
	void shouldRedactTheBarcodeInAPapiPath() {
		assertThat(PolarisLmsClient.redactedPath(
				"/PAPIService/REST/public/v1/1033/100/1/patron/" + PATRON_BARCODE + "/itemsout?x=1"),
			is("/PAPIService/REST/public/v1/1033/100/1/patron/{redacted}/itemsout"));
	}

	private List<String> loggedText() {
		final var text = new ArrayList<String>();

		for (ILoggingEvent event : captured.list) {
			text.add(event.getFormattedMessage());

			for (IThrowableProxy cause = event.getThrowableProxy(); cause != null; cause = cause.getCause()) {
				text.add(String.valueOf(cause.getMessage()));
			}
		}

		return text;
	}
}
