package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.core.interaction.MappingVocabulary.LOCATION;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.MappingValueCheck.Result;
import org.olf.dcb.core.interaction.koha.dto.KohaLibrary;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class KohaMappingValueCheckTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-mapping-value";
	private static final String KOHA_HOST = "fake-koha-mapping-value";

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
	void shouldAskKohaForEveryLibraryRatherThanItsDefaultPageOfTwenty() {
		// The expectation answers only a read that asks for every entry
		koha.mockLibraries(KohaLibrary.builder().libraryId("CPL").name("Centerville").build());

		final var check = client.checkMappingValue(LOCATION, "CPL").block();

		assertThat(check.result(), is(Result.PRESENT));
	}

	@Test
	void shouldReportWhyAVocabularyCouldNotBeRead() {
		koha.mockLibrariesUnreadable();

		final var check = client.checkMappingValue(LOCATION, "CPL").block();

		assertThat(check.result(), is(Result.UNKNOWN));
		assertThat(check.detail(), containsString("Could not read LOCATION from " + KOHA_HOST_LMS_CODE));
		assertThat(check.detail(), containsString("Koha API Error"));
	}
}
