package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.MappingVocabulary;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronCategory;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The vocabularies a mapping is built from. A Koha older than a list's route answers 404,
 * which is an empty list and not a failure; a Koha that fails still fails.
 */
@MockServerMicronautTest
class KohaVocabularyTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-vocabulary";
	private static final String KOHA_HOST = "fake-koha-vocabulary";

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
	void shouldAnswerEmptyWhenThisKohaHasNoSuchList() {
		koha.mockPatronCategoriesNotFound();

		assertThat(client.fetchVocabulary(MappingVocabulary.PATRON_TYPE).block(), is(empty()));
	}

	@Test
	void shouldStillFailWhenKohaFails() {
		koha.mockPatronCategoriesUnavailable();

		StepVerifier.create(client.fetchVocabulary(MappingVocabulary.PATRON_TYPE))
			.expectError()
			.verify();
	}

	@Test
	void shouldReportTheOtherListsWhenOneAnswersWithNoBody() {
		koha.mockItemTypesWithNoBody();
		koha.mockPatronCategories(new KohaPatronCategory("PT", "Patron"));
		koha.mockLibraries();
		koha.mockNoSystemPreferencesRoute();

		final var report = client.checkConfiguration().block();

		assertThat(report.status(), is(ConfigurationReport.Status.CHECKED));
		assertThat(report.vocabularies().get(1).entries(),
			is(List.of(new ConfigurationReport.Entry("PT", "Patron"))));
	}
}
