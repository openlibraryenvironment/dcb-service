package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.Bib;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class KohaCreateVirtualBibTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-create-virtual-bib";
	private static final String KOHA_HOST = "fake-koha-create-virtual-bib";

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
	void shouldUseTheBiblionumberKohaAnswersAndSayTheVirtualBibIsNotADuplicate() {
		koha.mockCreateBib(4001);

		assertThat(client.createBib(bib()).block(), is("4001"));
	}

	@Test
	void shouldRefuseToCarryOnWhenKohaGivesNoBiblionumber() {
		koha.mockCreateBibWithoutId();

		StepVerifier.create(client.createBib(bib()))
			.expectErrorMessage("Koha created the virtual bib but did not say its id")
			.verify();
	}

	private static Bib bib() {
		return Bib.builder().title("And then there were none").author("Agatha Christie").build();
	}
}
