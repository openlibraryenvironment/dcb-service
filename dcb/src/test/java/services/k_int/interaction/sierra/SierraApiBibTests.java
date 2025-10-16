package services.k_int.interaction.sierra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraBibsAPIFixture;
import org.olf.dcb.test.HostLmsFixture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micronaut.http.client.HttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class SierraApiBibTests {
	private static final String HOST_LMS_CODE = "sierra-bib-api-tests";

	@Inject
	private HttpClient client;
	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;

	private SierraBibsAPIFixture sierraBibsAPIFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://bib-api-tests.com";
		final String KEY = "bib-key";
		final String SECRET = "bib-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		sierraBibsAPIFixture = sierraApiFixtureProvider.bibsApiFor(mockServerClient);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@Test
	void testBibsGET() {
		// Arrange
		sierraBibsAPIFixture.createGetBibsMockWithQueryStringParameters();

		final var sierraApiClient = hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE);

		// Act
		var response = Mono.from( sierraApiClient.bibs(3, 1, "null",
			"null", null, false, null, false,
			List.of("a") )).block();

		// Assert
		assertNotNull(response);
		assertEquals(response.getClass(), BibResultSet.class);
		assertEquals(response.total(), 3);
		assertEquals(response.entries().get(0).id(), "1000002");
		assertEquals(response.entries().get(1).id(), "1000003");
		assertEquals(response.entries().get(2).id(), "1000004");
	}

	@Test
	void testBibsPOST() {
		// Arrange
		final var fixedFields = Map.of(31, FixedField.builder().label("suppress").value("n").build());
		final var bibPatch = BibPatch.builder()
			.fixedFields(fixedFields)
			.authors(List.of("John Smith"))
			.titles(List.of("The Book of John"))
			.build();

		ObjectMapper objectMapper = new ObjectMapper();
		String bibPatchJson;
		try {
			bibPatchJson = objectMapper.writeValueAsString(bibPatch);
			log.debug("testBibsPOST: {}", bibPatchJson);
		} catch (JsonProcessingException e) {
			log.error("Error converting BibPatch to JSON: {}", e.getMessage());
		}

		sierraBibsAPIFixture.createPostBibsMock(bibPatch, 7916922);

		final var sierraApiClient = hostLmsFixture.createLowLevelSierraClient(HOST_LMS_CODE);

		// Act
		var response = Mono.from( sierraApiClient.bibs(bibPatch) ).block();

		// Assert
		assertNotNull(response);
		assertEquals(response.getClass(), LinkResult.class);
		assertEquals(response.link, "https://sandbox.iii.com/iii/sierra-api/v6/bibs/7916922");
	}
}
