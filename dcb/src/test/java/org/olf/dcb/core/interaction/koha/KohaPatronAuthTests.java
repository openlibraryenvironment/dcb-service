package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.UnsupportedAuthProfileException;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronAuthResponse;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Signing a Koha patron in. Koha validates the OPAC password itself, so PIN and PASSWORD
 * are one profile to it; the name profile is DCB's comparison against the names on
 * record. A wrong credential is the empty answer; a Koha that cannot be asked is an error.
 */
@MockServerMicronautTest
class KohaPatronAuthTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";
	private static final String PATRON_ID = "12";
	private static final String CARDNUMBER = "CARD-0001";
	private static final String PASSWORD = "correct-horse-battery";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture mockKohaFixture;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		mockKohaFixture = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldDeclareTheProfilesKohaCanAuthenticateWith() {
		assertThat(client.supportedAuthProfiles(), contains(
			"BASIC/BARCODE+PASSWORD", "BASIC/BARCODE+PIN", "BASIC/BARCODE+NAME"));
	}

	@Test
	void shouldSignInWithTheRightPassword() {
		mockKohaFixture.mockPasswordAccepted(CARDNUMBER, PASSWORD, validated());
		mockKohaFixture.mockPatron(patron().build());

		final var patron = patronAuth("BASIC/BARCODE+PASSWORD", PASSWORD);

		assertThat(patron.getLocalId(), contains(PATRON_ID));
		assertThat(patron.getLocalBarcodes(), contains(CARDNUMBER));
	}

	@Test
	void shouldTreatPinAsThePassword() {
		mockKohaFixture.mockPasswordAccepted(CARDNUMBER, PASSWORD, validated());
		mockKohaFixture.mockPatron(patron().build());

		assertThat(patronAuth("BASIC/BARCODE+PIN", PASSWORD).getLocalId(), contains(PATRON_ID));
	}

	@Test
	void shouldRefuseAWrongPasswordQuietly() {
		mockKohaFixture.mockPasswordRefused(CARDNUMBER, "wrong", 401);

		StepVerifier.create(client.patronAuth("BASIC/BARCODE+PASSWORD", CARDNUMBER, "wrong"))
			.verifyComplete();
	}

	@Test
	void shouldRefuseAnUnknownCardNumberQuietly() {
		mockKohaFixture.mockPasswordRefused("CARD-9999", PASSWORD, 400);

		StepVerifier.create(client.patronAuth("BASIC/BARCODE+PASSWORD", "CARD-9999", PASSWORD))
			.verifyComplete();
	}

	@Test
	void shouldNotTreatAKohaOutageAsAWrongPassword() {
		mockKohaFixture.mockPasswordRefused(CARDNUMBER, PASSWORD, 500);

		StepVerifier.create(client.patronAuth("BASIC/BARCODE+PASSWORD", CARDNUMBER, PASSWORD))
			.expectError()
			.verify();
	}

	@Test
	void shouldSignInByNameWhenItStartsANameOnRecord() {
		mockKohaFixture.mockPatronsByCardnumber(CARDNUMBER, patron().build());

		assertThat(patronAuth("BASIC/BARCODE+NAME", "example").getLocalId(), contains(PATRON_ID));
	}

	@Test
	void shouldRefuseANameNotOnRecord() {
		mockKohaFixture.mockPatronsByCardnumber(CARDNUMBER, patron().build());

		StepVerifier.create(client.patronAuth("BASIC/BARCODE+NAME", CARDNUMBER, "Babbage"))
			.verifyComplete();
	}

	@Test
	void shouldRefuseAnUnknownProfileAsAConfigurationError() {
		assertThrows(UnsupportedAuthProfileException.class,
			() -> Mono.from(client.patronAuth("BASIC/UNIQUE-ID+PIN", CARDNUMBER, PASSWORD)).block());
	}

	private Patron patronAuth(String profile, String secret) {
		return client.patronAuth(profile, CARDNUMBER, secret).block();
	}

	private static KohaPatronAuthResponse validated() {
		return KohaPatronAuthResponse.builder()
			.patronId(Long.valueOf(PATRON_ID))
			.cardnumber(CARDNUMBER)
			.userid("grace")
			.build();
	}

	private static KohaPatron.KohaPatronBuilder patron() {
		return KohaPatron.builder()
			.patronId(Long.valueOf(PATRON_ID))
			.cardnumber(CARDNUMBER)
			.surname("Example")
			.firstname("Grace")
			.libraryId("BRANCH-N")
			.categoryId("AD")
			.expiryDate("2099-12-31");
	}
}
