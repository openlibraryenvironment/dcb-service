package org.olf.dcb.api;

import static io.micronaut.http.HttpStatus.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static services.k_int.interaction.sierra.SierraTestUtils.okJson;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.ingest.IngestService;
import org.olf.dcb.test.ClusterRecordFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Page;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ClusterRecordTests {
	private static final String HOST_LMS_CODE = "cluster-record-tests";

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private IngestService ingestService;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ClusterRecordFixture clusterRecordFixture;

	@Inject
	@Client("/")
	private HttpClient client;

	@BeforeAll
	void addFakeSierraApis(MockServerClient mock) {
		final String TOKEN = "test-token";
		final String BASE_URL = "https://cluster-record-tests.com";
		final String KEY = "cluster-record-key";
		final String SECRET = "cluster-record-secret";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");

		var mockSierra = SierraTestUtils.mockFor(mock, BASE_URL)
			.setValidCredentials(KEY, SECRET, TOKEN, 60);

		// Mock bibs returned by the sierra system for ingest.
		final var resourceLoader = testResourceLoaderProvider.forBasePath("classpath:mock-responses/sierra/");

		mockSierra.whenRequest(req -> req.withMethod("GET").withPath("/iii/sierra-api/v6/bibs/*"))
			.respond(okJson(resourceLoader.getResource("bibs-slice-0-2.json")));
	}

	@BeforeEach
	void beforeEach() {
		clusterRecordFixture.deleteAll();
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

		assertThat(metadata.identifiers(), containsInAnyOrder(
			hasIdentifier("ISBN", "9781234567890"),
			hasIdentifier("ISSN", "1234-5678"),
			hasIdentifier("LCCN", "68009551"),
			hasIdentifier("GOLDRUSH", "basiccircuittheory                                               1969876    mca                              "),
			hasIdentifier("BLOCKING_TITLE", "basic circuit theory charles desoer ernest kuh")));
	}

	private static Matcher<ClusterRecord.Identifier> hasIdentifier(String namespace, String value) {
		return allOf(
			hasProperty("namespace", is(namespace)),
			hasProperty("value", is(value)));
	}
}
