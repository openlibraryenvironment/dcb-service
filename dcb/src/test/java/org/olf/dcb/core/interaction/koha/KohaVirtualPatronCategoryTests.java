package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.interaction.koha.dto.KohaPatronCategory;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The category a visiting patron is created in when their patron type has no mapping
 * yet, and the mailbox they are given: both are this Koha's settings, not DCB's.
 */
@MockServerMicronautTest
class KohaVirtualPatronCategoryTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-virtual-patron";
	private static final String KOHA_HOST = "fake-koha-virtual-patron";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture koha;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
	}

	@Test
	void shouldCreateAnUnmappedPatronInTheConfiguredCategory() {
		createPatronWithNoMappedType(Map.of("virtual-patron-category", "ILL"));

		koha.verifyPatronCreated("{\"category_id\": \"ILL\"}");
	}

	@Test
	void shouldDefaultToTheCategoryKohaSitesWereToldToCreate() {
		createPatronWithNoMappedType(Map.of());

		koha.verifyPatronCreated("{\"category_id\": \"DCB\"}");
	}

	@Test
	void shouldCreateTheVirtualPatronWithTheConsortiumMailbox() {
		createPatronWithNoMappedType(Map.of("virtual-patron-email", "ill-notices@consortium.example.org"));

		koha.verifyPatronCreated("{\"email\": \"ill-notices@consortium.example.org\"}");
	}

	@Test
	void shouldReportACategoryKohaDoesNotHave() {
		koha.mockItemTypes();
		koha.mockPatronCategories(new KohaPatronCategory("PT", "Patron"), new KohaPatronCategory("ST", "Staff"));
		koha.mockLibraries();
		koha.mockNoSystemPreferencesRoute();

		final var report = clientWith(Map.of()).checkConfiguration().block();

		assertThat(report.checks(), hasItem(new ConfigurationReport.Check("virtual-patron-category",
			"DCB", ConfigurationReport.CheckResult.MISSING, "Not found in Koha")));
	}

	private void createPatronWithNoMappedType(Map<String, Object> extraConfig) {
		koha.mockCreatePatron(KohaPatron.builder().patronId(77L).build());

		clientWith(extraConfig).createPatron(Patron.builder()
			.uniqueIds(List.of("unique-id"))
			.localNames(List.of("Ada", "Lovelace"))
			.build()).block();
	}

	private HostLmsClient clientWith(Map<String, Object> extraConfig) {
		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST, extraConfig);

		return hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}
}
