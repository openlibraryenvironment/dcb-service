package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * A visiting patron's category follows their mapped patron type. Koha's patron schema
 * refuses a PUT carrying any property it marks read-only.
 */
@MockServerMicronautTest
class KohaUpdatePatronTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-update-patron";
	private static final String KOHA_HOST = "fake-koha-update-patron";
	private static final String PATRON_ID = "90";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture koha;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldSendTheNewCategoryWithoutKohasReadOnlyFlags() {
		koha.mockPatron(virtualPatron().categoryId("DCB").build());
		koha.mockUpdatePatron(virtualPatron().categoryId("DCB-STAFF").build());

		client.updatePatron(PATRON_ID, "DCB-STAFF").block();

		final var sent = koha.patronUpdateSent(PATRON_ID);
		assertThat(sent, hasEntry("category_id", "DCB-STAFF"));
		assertThat(sent, not(hasKey("expired")));
		assertThat(sent, not(hasKey("restricted")));
	}

	private static KohaPatron.KohaPatronBuilder virtualPatron() {
		return KohaPatron.builder()
			.patronId(Long.valueOf(PATRON_ID))
			.userid("872321@borrowing-agency")
			.surname("Patron")
			.firstname("Borrowing")
			.libraryId("DCB-SHARING")
			.expiryDate("2099-12-31")
			.expired(false)
			.restricted(false);
	}
}
