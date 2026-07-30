package org.olf.dcb.indexing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.indexing.opensearch.OpenSearchSharedIndexService;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.indices.AnalyzeRequest;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.olf.dcb.test.DcbTestContainerContextBuilder;

/**
 * Guards the OpenSearch shared index against the class of failure that shipped
 * undetected in the Micronaut 5 upgrade: the factory asked for a Jackson 2
 * ObjectMapper that no longer exists under Micronaut 5, so the whole application
 * failed to start whenever OpenSearch was configured. Production runs OpenSearch,
 * and nothing caught it -- no test constructed an OpenSearch client at all.
 *
 * <p>Simply reaching an assertion here means the context started with a real
 * OpenSearch client wired up, which is the bulk of the value. The assertions then
 * cover the serialization path (settings and mappings are written through the
 * JsonpMapper) and confirm the client can talk to the server.
 */
@MicronautTest(startApplication = false,
	contextBuilder = DcbTestContainerContextBuilder.class)
@TestInstance(PER_CLASS)
class OpenSearchSharedIndexIntegrationTests implements TestPropertyProvider {

	private static final String INDEX_NAME = "dcb-shared-index-test";
	private static final String OPENSEARCH_VERSION = "2.19.1";

	/**
	 * sharedIndex/settings-2.json declares an icu_folding_nopunc analyzer over
	 * icu_tokenizer/icu_folding, so a stock OpenSearch image cannot create the
	 * index. Mirrors scripts/os2-icu.Dockerfile. Testcontainers caches the built
	 * image, so this is only paid on the first run.
	 */
	private static final OpenSearchContainer<?> OPENSEARCH = buildContainer();

	private static OpenSearchContainer<?> buildContainer() {
		final String image;

		try {
			image = new ImageFromDockerfile("dcb-opensearch-icu-test:" + OPENSEARCH_VERSION, false)
				.withDockerfileFromBuilder(builder -> builder
					.from("opensearchproject/opensearch:" + OPENSEARCH_VERSION)
					.run("/usr/share/opensearch/bin/opensearch-plugin install --batch analysis-icu")
					.build())
				.get();
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not build the OpenSearch ICU image", e);
		}

		return new OpenSearchContainer<>(DockerImageName.parse(image)
			.asCompatibleSubstituteFor("opensearchproject/opensearch"));
	}

	@Override
	public Map<String, String> getProperties() {
		if (!OPENSEARCH.isRunning()) {
			OPENSEARCH.start();
		}

		// dcb.index.name is what activates SharedIndexConfiguration, and therefore the
		// whole shared index stack. Without it none of these beans exist.
		return Map.of(
			"opensearch.http-hosts", OPENSEARCH.getHttpHostAddress(),
			"dcb.index.name", INDEX_NAME,
			"dcb.index.number-of-replicas", "0");
	}

	@Inject
	private SharedIndexService sharedIndexService;

	@Inject
	private OpenSearchAsyncClient client;

	@Inject
	private SharedIndexBackendInfo backendInfo;

	@Test
	void shouldWireTheOpenSearchImplementationOfTheSharedIndex() {
		// Guards the bean wiring itself. Under Micronaut 5 this failed with
		// NoSuchBeanException for com.fasterxml.jackson.databind.ObjectMapper, taking
		// the entire application down at startup.
		assertThat(sharedIndexService, is(instanceOf(OpenSearchSharedIndexService.class)));
	}

	@Test
	void shouldCreateTheIndexFromTheCheckedInSettingsAndMappings() throws Exception {
		final var indexName = INDEX_NAME + "-" + SharedIndexConfiguration.LATEST_INDEX_VERSION;

		// The index is created on startup by SharedIndexLiveUpdater. Writing it means
		// settings-2.json and mappings-2.json round-tripped through the JsonpMapper --
		// the exact path that breaks when the mapper is misconfigured.
		final var exists = client.indices()
			.exists(b -> b.index(indexName))
			.get(30, TimeUnit.SECONDS)
			.value();

		assertThat("Expected the shared index to have been created on startup",
			exists, is(true));
	}

	@Test
	void shouldApplyTheConfiguredReplicaCount() throws Exception {
		final var indexName = INDEX_NAME + "-" + SharedIndexConfiguration.LATEST_INDEX_VERSION;
		final var response = client.indices()
			.getSettings(b -> b.index(indexName))
			.get(30, TimeUnit.SECONDS);

		assertThat(response.result().get(indexName).settings().index().numberOfReplicas(), is(0));
	}

	@Test
	void shouldRecordTheBackendVersionForTheInfoEndpoint() {
		// Reported at startup and surfaced on /info as dcb.index.backend.*, so a
		// deployment's search backend version can be established without holding
		// credentials for that cluster.
		assertThat(backendInfo.getDistribution(), is(Optional.of("opensearch")));
		assertThat(backendInfo.getVersion(), is(Optional.of(OPENSEARCH_VERSION)));
	}

	@Test
	void shouldApplyTheIcuAnalyzerDefinedInTheIndexSettings() throws Exception {
		final var indexName = INDEX_NAME + "-" + SharedIndexConfiguration.LATEST_INDEX_VERSION;

		// Proves the analyzer in settings-2.json survived serialization and is live on
		// the server, rather than the index having been created with defaults.
		final var response = client.indices()
			.analyze(AnalyzeRequest.of(b -> b
				.index(indexName)
				.analyzer("icu_folding_nopunc")
				.text("Brain of the Firm: Beer, Stafford")))
			.get(30, TimeUnit.SECONDS);

		final List<String> tokens = response.tokens().stream()
			.map(token -> token.token())
			.toList();

		assertThat(tokens, contains("brain", "of", "the", "firm", "beer", "stafford"));
	}
}
