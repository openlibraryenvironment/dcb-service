package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import java.io.IOException;

import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Page;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.ingest.IngestService;
import org.olf.dcb.test.HostLmsFixture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@MicronautTest(transactional = false, rebuildContext = true)
@Property(name = "r2dbc.datasources.default.options.maxSize", value = "1")
@Property(name = "r2dbc.datasources.default.options.initialSize", value = "1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClusterRecordTests {

	private final Logger log = LoggerFactory.getLogger(ClusterRecordTests.class);

	private static final String HOST_LMS_CODE = "cluster-record-tests";

	@Inject
	private ResourceLoader loader;
	@Inject
	private IngestService ingestService;
	@Inject
	private HostLmsFixture hostLmsFixture;

	private static final String CP_RESOURCES = "classpath:mock-responses/sierra/";
	@Inject
	@Client("/")
	private HttpClient client;

	private String getResourceAsString(String resourceName) throws IOException {
		return new String(loader.getResourceAsStream(CP_RESOURCES + resourceName).get().readAllBytes());
	}

	@BeforeAll
	public void addFakeSierraApis(MockServerClient mock) throws IOException {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://cluster-record-tests.com";
		final String KEY = "cluster-record-key";
		final String SECRET = "cluster-record-secret";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE, "item");

		var mockSierra = SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		// Mock bibs returned by the sierra system for ingest.
		mockSierra.whenRequest(req -> req.withMethod("GET").withPath("/iii/sierra-api/v6/bibs/*"))
			.respond(okJson(getResourceAsString("bibs-slice-0-2.json")));
	}

	@Test
	void getClusterRecords() {
		// Arrange
		ingestService.getBibRecordStream().collectList().block();

		final var blockingClient = client.toBlocking();
		final var request = HttpRequest.GET("/clusters?page=0&size=10");

		// Act
		final var response = blockingClient.exchange(request, Argument.of(Page.class, ClusterRecord.class));

		// Assert
		assertThat(response.getStatus(), is(OK));
		assertThat(response.getBody().isPresent(), is(true));
		assertThat(response.getBody().get().getContent().size(), is(1));

		Page<ClusterRecord> page = response.getBody().get();
		final var content = page.getContent();
		final var metadata = content.get(0).selectedBib().canonicalMetadata();

		final var author = metadata.author();
		assertThat(author, is(nullValue()));

		final var title = metadata.title();
		assertThat(title, is("Basic circuit theory [by] Charles A. Desoer and Ernest S. Kuh."));

		final var subjects = metadata.subjects();

		final var subject = subjects.stream()
			.filter(s -> "topical-term".equals(s.subtype()))
			.findFirst()
			.orElse(null);

		assertThat(subject, is(notNullValue()));
		assertThat(subject.label(), is("Electric circuits."));

		final var identifiers = metadata.identifiers();

		final var isbnIdentifier = identifiers.stream()
			.filter(identifier -> "ISBN".equals(identifier.namespace()))
			.findFirst()
			.orElse(null);

		assertThat(isbnIdentifier, is(notNullValue()));
		assertThat(isbnIdentifier.value(), is("9781234567890"));

		final var issnIdentifier = identifiers.stream()
			.filter(identifier -> "ISSN".equals(identifier.namespace()))
			.findFirst()
			.orElse(null);

		assertThat(issnIdentifier, is(notNullValue()));
		assertThat(issnIdentifier.value(), is("1234-5678"));
	}
}
