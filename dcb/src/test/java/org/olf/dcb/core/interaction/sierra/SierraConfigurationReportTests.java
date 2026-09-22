package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpRequest.request;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.MappingValueCheck;
import org.olf.dcb.core.interaction.MappingVocabulary;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraConfigurationReportTests {
	private static final String HOST_LMS_CODE = "sierra-configuration";
	private static final String BASE_URL = "https://sierra-configuration-tests.com";

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials("key", "secret", "token", 3600);

		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, "key", "secret", BASE_URL, "item");

		// Two pages, so a location on the second is only found by following the paging
		mockServerClient.when(branchesPage("0"))
			.respond(okJson(Map.of("total", 2, "start", 0, "entries", List.of(Map.of(
				"id", "1", "name", "Main",
				"locations", List.of(Map.of("code", "mainf", "name", "Main fiction")))))));

		mockServerClient.when(branchesPage("1"))
			.respond(okJson(Map.of("total", 2, "start", 1, "entries", List.of(Map.of(
				"id", "2", "name", "East",
				"locations", List.of(Map.of("code", "eastj ", "name", "East juvenile")))))));

		mockServerClient.when(request().withMethod("GET").withPath("/iii/sierra-api/v6/patrons/metadata"))
			.respond(okJson(List.of(
				Map.of("field", "patronType", "values", List.of(Map.of("code", 3, "desc", "Adult"))),
				Map.of("field", "pMessage", "values", List.of(Map.of("code", "f", "desc", "Fines"))))));
	}

	@Test
	void shouldFindALocationOnALaterBranchPage() {
		assertThat(resultOf(MappingVocabulary.LOCATION, "eastj"), is(MappingValueCheck.Result.PRESENT));
		assertThat(resultOf(MappingVocabulary.LOCATION, "westa"), is(MappingValueCheck.Result.MISSING));
	}

	@Test
	void shouldReadPatronTypesAsTheNumbersDcbMapsOn() {
		assertThat(resultOf(MappingVocabulary.PATRON_TYPE, "3"), is(MappingValueCheck.Result.PRESENT));

		// A code from another metadata field is not a patron type
		assertThat(resultOf(MappingVocabulary.PATRON_TYPE, "f"), is(MappingValueCheck.Result.MISSING));
	}

	@Test
	void shouldSayItemTypesCannotBeRead() {
		assertThat(resultOf(MappingVocabulary.ITEM_TYPE, "1"), is(MappingValueCheck.Result.NOT_SUPPORTED));

		final var report = client().checkConfiguration().block();

		assertThat(report.status(), is(ConfigurationReport.Status.CHECKED));
		assertThat(report.vocabularies().stream()
			.filter(vocabulary -> "Item types".equals(vocabulary.name()))
			.findFirst().orElseThrow().entries().isEmpty(), is(true));
	}

	private static org.mockserver.model.HttpRequest branchesPage(String offset) {
		return request().withMethod("GET")
			.withPath("/iii/sierra-api/v6/branches")
			.withQueryStringParameter("offset", offset);
	}

	private MappingValueCheck.Result resultOf(MappingVocabulary vocabulary, String value) {
		return client().checkMappingValue(vocabulary, value).block().result();
	}

	private HostLmsClient client() {
		return hostLmsFixture.createClient(HOST_LMS_CODE);
	}
}
