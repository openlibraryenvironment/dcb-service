package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * What DCB's preflight learns about a Koha patron: who they are, and whether Koha itself
 * would stop them. Koha's blocks are its own flags on the patron, not a status, and its
 * expiry is a date; debt is left to Koha, which refuses the hold and says so.
 */
@MockServerMicronautTest
class KohaPatronEligibilityTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";
	private static final String PATRON_ID = "12";

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
	void shouldFindAPatronByTheirKohaIdNotTheirCardNumber() {
		// ResolvePatronPreflightCheck passes the patron id DCB holds; searching it as a card
		// number found whichever card number contained those digits, or nobody
		mockKohaFixture.mockPatron(patron().build());

		final var patron = getPatronByIdentifier();

		assertThat(patron.getLocalId(), contains(PATRON_ID));
		assertThat(patron.getLocalBarcodes(), contains("CARD-0001"));
		assertThat(patron.getLocalPatronType(), is("AD"));
		assertThat(patron.getLocalHomeLibraryCode(), is("BRANCH-N"));
	}

	@Test
	void shouldAdmitAPatronKohaHasNoComplaintAbout() {
		mockKohaFixture.mockPatron(patron().build());

		final var patron = getPatronByIdentifier();

		assertThat(patron.getIsBlocked(), is(false));
		assertThat(patron.getIsActive(), is(true));
		assertThat("An expiry in the future must not read as expired",
			patron.getExpiryDate().after(new Date()), is(true));
	}

	@Test
	void shouldBlockARestrictedPatron() {
		mockKohaFixture.mockPatron(patron().restricted(true).build());

		assertThat(getPatronByIdentifier().getIsBlocked(), is(true));
	}

	@Test
	void shouldBlockAPatronWhoseCardIsLost() {
		mockKohaFixture.mockPatron(patron().patronCardLost(true).build());

		assertThat(getPatronByIdentifier().getIsBlocked(), is(true));
	}

	@Test
	void shouldCarryAnExpiryInThePast() {
		mockKohaFixture.mockPatron(patron().expiryDate("2026-01-31").expired(true).build());

		final var patron = getPatronByIdentifier();

		assertThat(patron.getExpiryDate(), is(lessThan(new Date())));
	}

	@Test
	void shouldNotTreatAnUnreadableExpiryAsExpired() {
		mockKohaFixture.mockPatron(patron().expiryDate("never").build());

		assertThat(getPatronByIdentifier().getExpiryDate(), is(nullValue()));
	}

	@Test
	void shouldAnswerEmptyForAPatronKohaDoesNotHave() {
		mockKohaFixture.mockPatronNotFound(PATRON_ID);

		StepVerifier.create(client.getPatronByIdentifier(PATRON_ID))
			.verifyComplete();
	}

	private Patron getPatronByIdentifier() {
		return client.getPatronByIdentifier(PATRON_ID).block();
	}

	private static KohaPatron.KohaPatronBuilder patron() {
		return KohaPatron.builder()
			.patronId(Long.valueOf(PATRON_ID))
			.cardnumber("CARD-0001")
			.surname("Example")
			.firstname("Grace")
			.libraryId("BRANCH-N")
			.categoryId("AD")
			.expiryDate("2099-12-31")
			.expired(false)
			.restricted(false)
			.patronCardLost(false);
	}
}
