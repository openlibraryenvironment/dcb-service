package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.JsonBody.json;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.SierraTestUtils.MockSierraV6Host;
import services.k_int.interaction.sierra.patrons.PatronValidation;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * What a Sierra patron sign-in leaves in the log. The PIN, the barcode and the name a patron
 * signs in with are credentials, as is the API token, at every level.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraCredentialLoggingTests {
	private static final String HOST_LMS_CODE = "sierra-credential-logging";
	private static final String BASE_URL = "https://sierra-credential-logging-tests.com";
	private static final String KEY = "sierra-key";
	private static final String SECRET = "sierra-secret-6021";
	private static final String TOKEN = "sierra-token-9917";

	private static final String PATRON_BARCODE = "barcode-16180";
	private static final String PATRON_PIN = "pin-27182";
	private static final String PATRON_NAME = "Lovelace";
	private static final int LOCAL_PATRON_TYPE = 15;

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private MockServerClient mockServerClient;
	private MockSierraV6Host mockSierra;
	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	private final List<Logger> loggers = List.of(
		(Logger) LoggerFactory.getLogger("org.olf.dcb.core.interaction.sierra"),
		(Logger) LoggerFactory.getLogger("services.k_int.interaction.sierra"));
	private final List<Level> previousLevels = new ArrayList<>();
	private final ListAppender<ILoggingEvent> captured = new ListAppender<>();

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient, null);
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();

		mockSierra = SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 3600);

		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");

		referenceValueMappingFixture.deleteAll();
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,
			LOCAL_PATRON_TYPE, LOCAL_PATRON_TYPE, "DCB", "dcb-type");

		// Every level: the rule is about what reaches any log, not only what reaches ERROR
		previousLevels.clear();
		captured.list.clear();
		captured.start();

		for (Logger logger : loggers) {
			previousLevels.add(logger.getLevel());
			logger.setLevel(Level.TRACE);
			logger.addAppender(captured);
		}
	}

	@AfterEach
	void afterEach() {
		for (int index = 0; index < loggers.size(); index++) {
			loggers.get(index).detachAppender(captured);
			loggers.get(index).setLevel(previousLevels.get(index));
		}
	}

	@Test
	void shouldNotLogThePinBarcodeOrTokenWhenAPatronSignsIn() {
		patronExists();
		validationRespondsWith(200);

		final var patron = patronAuth("BASIC/BARCODE+PIN", PATRON_PIN);

		assertThat(patron, is(notNullValue()));
		assertNothingSecretLogged(PATRON_PIN);
	}

	@Test
	void shouldNotLogThePinOrBarcodeWhenSierraRefusesThePin() {
		patronExists();
		validationRespondsWith(400);

		final var patron = patronAuth("BASIC/BARCODE+PIN", PATRON_PIN);

		assertThat(patron, is(nullValue()));
		assertNothingSecretLogged(PATRON_PIN);
	}

	@Test
	void shouldNotLogTheNameOrBarcodeWhenAPatronSignsInByName() {
		patronExists();

		final var patron = patronAuth("BASIC/BARCODE+NAME", PATRON_NAME);

		assertThat(patron, is(notNullValue()));
		assertNothingSecretLogged(PATRON_NAME);
	}

	@Test
	void shouldNotLogTheBarcodeWhenNoPatronIsFound() {
		sierraPatronsAPIFixture.patronNotFoundResponse("b", PATRON_BARCODE);

		final var patron = patronAuth("BASIC/BARCODE+NAME", PATRON_NAME);

		assertThat(patron, is(nullValue()));
		assertNothingSecretLogged(PATRON_NAME);
	}

	private void patronExists() {
		sierraPatronsAPIFixture.patronFoundResponse("b", PATRON_BARCODE,
			SierraPatronRecord.builder()
				.id(1000002)
				.patronType(LOCAL_PATRON_TYPE)
				.homeLibraryCode("home-library")
				.barcodes(List.of(PATRON_BARCODE))
				.names(List.of(PATRON_NAME + ", Ada"))
				.build());
	}

	private void validationRespondsWith(int statusCode) {
		mockSierra.whenRequest(req -> req
				.withMethod("POST")
				.withPath("/iii/sierra-api/v6/patrons/validate")
				.withBody(json(PatronValidation.builder()
					.barcode(PATRON_BARCODE)
					.pin(PATRON_PIN)
					.build())))
			.respond(HttpResponse.response().withStatusCode(statusCode));
	}

	private Object patronAuth(String authProfile, String secret) {
		return Mono.from(hostLmsFixture.createClient(HOST_LMS_CODE)
				.patronAuth(authProfile, PATRON_BARCODE, secret))
			.onErrorResume(error -> Mono.empty())
			.block();
	}

	private void assertNothingSecretLogged(String secret) {
		final var logged = loggedText();

		assertThat(logged, not(hasItem(containsString(secret))));
		assertThat(logged, not(hasItem(containsString(PATRON_BARCODE))));
		assertThat(logged, not(hasItem(containsString(TOKEN))));
		assertThat(logged, not(hasItem(containsString(SECRET))));
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
