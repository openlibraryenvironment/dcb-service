package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronAuthResponse;
import org.olf.dcb.test.HostLmsFixture;
import org.slf4j.LoggerFactory;
import org.zalando.problem.ThrowableProblem;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * What a Koha patron sign-in leaves in the log and in the error it raises. The password,
 * the card number, the name and the access token are credentials, at every level, and a
 * refused validation, or a refused virtual patron, must not carry the request body into the
 * problem it reports.
 */
@MockServerMicronautTest
class KohaCredentialLoggingTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-credential-logging";
	private static final String KOHA_HOST = "fake-koha-logging";
	private static final String CARDNUMBER = "card-16180";
	private static final String PASSWORD = "password-27182";
	private static final String NAME = "Lovelace";
	private static final String TOKEN = "koha-access-token";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private final Logger logger = (Logger) LoggerFactory.getLogger("org.olf.dcb.core.interaction.koha");
	private final ListAppender<ILoggingEvent> captured = new ListAppender<>();
	private Level previousLevel;

	private MockKohaFixture mockKohaFixture;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();
		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		mockKohaFixture = new MockKohaFixture(mockServerClient, KOHA_HOST);

		// Every level: the rule is about what reaches any log, not only what reaches ERROR
		captured.list.clear();
		captured.start();
		previousLevel = logger.getLevel();
		logger.setLevel(Level.TRACE);
		logger.addAppender(captured);
	}

	@AfterEach
	void afterEach() {
		logger.detachAppender(captured);
		logger.setLevel(previousLevel);
	}

	@Test
	void shouldNotLogThePasswordCardNumberOrTokenWhenAPatronSignsIn() {
		mockKohaFixture.mockPasswordAccepted(CARDNUMBER, PASSWORD,
			KohaPatronAuthResponse.builder().patronId(12L).cardnumber(CARDNUMBER).build());
		mockKohaFixture.mockPatron(patron());

		assertThat(patronAuth("BASIC/BARCODE+PASSWORD", PASSWORD), is(notNullValue()));
		assertNothingSecretLogged(PASSWORD);
	}

	@Test
	void shouldNotLogThePasswordOrCardNumberWhenKohaRefusesIt() {
		mockKohaFixture.mockPasswordRefused(CARDNUMBER, PASSWORD, 401);

		assertThat(patronAuth("BASIC/BARCODE+PASSWORD", PASSWORD), is(nullValue()));
		assertNothingSecretLogged(PASSWORD);
	}

	@Test
	void shouldNotCarryThePasswordIntoTheErrorWhenKohaFails() {
		mockKohaFixture.mockPasswordRefused(CARDNUMBER, PASSWORD, 500);

		final var error = Mono.from(hostLmsFixture.createClient(KOHA_HOST_LMS_CODE)
				.patronAuth("BASIC/BARCODE+PASSWORD", CARDNUMBER, PASSWORD))
			.map(patron -> (Throwable) null)
			.onErrorResume(Mono::just)
			.block();

		assertThat(error, is(notNullValue()));
		assertThat(error.toString(), not(containsString(PASSWORD)));
		assertThat(error.toString(), not(containsString(CARDNUMBER)));
		assertNothingSecretLogged(PASSWORD);
	}

	@Test
	void shouldNotLogTheNameOrCardNumberWhenAPatronSignsInByName() {
		mockKohaFixture.mockPatronsByCardnumber(CARDNUMBER, patron());

		assertThat(patronAuth("BASIC/BARCODE+NAME", NAME), is(notNullValue()));
		assertNothingSecretLogged(NAME);
	}

	@Test
	void shouldNotCarryAPatronsDetailsIntoTheProblemWhenKohaRefusesTheVirtualPatron() {
		// Every parameter of the problem is copied into the patron request's audit record
		mockKohaFixture.mockCreatePatronRefused();

		final var error = Mono.from(hostLmsFixture.createClient(KOHA_HOST_LMS_CODE)
				.createPatron(Patron.builder()
					.uniqueIds(List.of(CARDNUMBER + "@borrowing-agency"))
					.localNames(List.of("Ada", NAME))
					.build()))
			.map(created -> (Throwable) null)
			.onErrorResume(Mono::just)
			.block();

		assertThat(error, is(instanceOf(ThrowableProblem.class)));
		assertThat(((ThrowableProblem) error).getParameters(), not(hasKey("Request Body")));
		assertThat(error.toString(), not(containsString(NAME)));
		assertThat(error.toString(), not(containsString(CARDNUMBER)));
		assertNothingSecretLogged(NAME);
	}

	private Object patronAuth(String authProfile, String secret) {
		return Mono.from(hostLmsFixture.createClient(KOHA_HOST_LMS_CODE)
				.patronAuth(authProfile, CARDNUMBER, secret))
			.onErrorResume(error -> Mono.empty())
			.block();
	}

	private void assertNothingSecretLogged(String secret) {
		final var logged = captured.list.stream()
			.map(ILoggingEvent::getFormattedMessage)
			.toList();

		assertThat(logged, not(hasItem(containsString(secret))));
		assertThat(logged, not(hasItem(containsString(CARDNUMBER))));
		assertThat(logged, not(hasItem(containsString(TOKEN))));
	}

	private static KohaPatron patron() {
		return KohaPatron.builder()
			.patronId(12L)
			.cardnumber(CARDNUMBER)
			.surname(NAME)
			.firstname("Ada")
			.libraryId("BRANCH-N")
			.categoryId("AD")
			.expiryDate("2099-12-31")
			.build();
	}
}
