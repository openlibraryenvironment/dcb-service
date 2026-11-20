package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.olf.dcb.core.model.FunctionalSettingType.VIRTUAL_PATRON_NAMES_VISIBLE;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * A virtual patron carries the borrower's names only when the consortium's
 * VIRTUAL_PATRON_NAMES_VISIBLE setting is on.
 */
@MockServerMicronautTest
class KohaVirtualPatronNamesTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-virtual-patron-names";
	private static final String KOHA_HOST = "fake-koha-virtual-patron-names";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ConsortiumFixture consortiumFixture;

	private MockKohaFixture koha;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		consortiumFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
		koha.mockCreatePatron(KohaPatron.builder().patronId(77L).build());
	}

	@Test
	void shouldNameTheVirtualPatronAfterTheBorrowerWhenNamesAreVisible() {
		consortiumFixture.enableSetting(VIRTUAL_PATRON_NAMES_VISIBLE);

		createVirtualPatron();

		final var sent = koha.patronCreationSent();
		assertThat(sent, hasEntry("firstname", "Ada"));
		assertThat(sent, hasEntry("surname", "Lovelace"));
	}

	@Test
	void shouldNotNameTheVirtualPatronAfterTheBorrowerWhenNamesAreHidden() {
		consortiumFixture.disableSetting(VIRTUAL_PATRON_NAMES_VISIBLE);

		createVirtualPatron();

		final var sent = koha.patronCreationSent();
		assertThat(sent, hasEntry("firstname", "DCB"));
		assertThat(sent, hasEntry("surname", "VPATRON"));
	}

	@Test
	void shouldNotNameTheVirtualPatronAfterTheBorrowerWithoutAConsortium() {
		createVirtualPatron();

		final var sent = koha.patronCreationSent();
		assertThat(sent, hasEntry("firstname", "DCB"));
		assertThat(sent, hasEntry("surname", "VPATRON"));
	}

	@Test
	void shouldStillCarryTheHomeBarcodeForThePickupDeskWhenNamesAreHidden() {
		consortiumFixture.disableSetting(VIRTUAL_PATRON_NAMES_VISIBLE);

		createVirtualPatron();

		assertThat(koha.patronCreationSent(), hasEntry("other_name", "2100045"));
	}

	private void createVirtualPatron() {
		client.createPatron(Patron.builder()
			.uniqueIds(List.of("unique-id"))
			.localNames(List.of("Ada", "Lovelace"))
			.localBarcodes(List.of("2100045"))
			.localPatronType("DCB")
			.build()).block();
	}
}
