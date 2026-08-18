package org.olf.dcb.indexing.elasticsearch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;

class ElasticsearchIndexDocumentSerializationTests {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void shouldSerializeDiscoveryFieldsIntoTheElasticsearchSource() throws Exception {
		final var clusterId = UUID.randomUUID();
		final var bibId = UUID.randomUUID();
		final var metadata = Map.<String, Object>of(
			"identifiers", List.of(Map.of("namespace", "ISBN", "value", "9780000000001")));
		final var bib = mock(BibRecord.class);
		final var cluster = mock(ClusterRecord.class);

		when(bib.getId()).thenReturn(bibId);
		when(bib.getCanonicalMetadata()).thenReturn(metadata);
		when(cluster.getId()).thenReturn(clusterId);
		when(cluster.getTitle()).thenReturn("DCB discovery serialization fixture");
		when(cluster.getLastIndexed()).thenReturn(Instant.parse("2026-08-18T09:00:00Z"));
		when(cluster.getSelectedBib()).thenReturn(bibId);
		when(cluster.getBibs()).thenReturn(Set.of(bib));

		final var document = new ClusterRecordIndexDoc(cluster, ignored -> "fixture-lms", Map.of());
		final var mapper = new JacksonJsonpMapper();
		final var output = new StringWriter();
		final var generator = mapper.jsonProvider().createGenerator(output);

		mapper.serialize(document, generator);
		generator.close();

		final JsonNode source = JSON.readTree(output.toString());

		assertThat(source.get("bibClusterId").asText(), is(clusterId.toString()));
		assertThat(source.get("title").asText(), is("DCB discovery serialization fixture"));
		assertThat(source.get("metadata"), equalTo(JSON.valueToTree(metadata)));
	}

	@Test
	void shouldRetainNativeReflectionForTheElasticsearchDocumentGetters() throws Exception {
		try (var input = getClass().getResourceAsStream(
			"/META-INF/native-image/org.olf.dcb/dcb/reflect-config.json")) {
			final List<Map<String, Object>> entries = JSON.readValue(
				new String(input.readAllBytes(), StandardCharsets.UTF_8),
				new TypeReference<>() { });

			assertThat(entries, hasItem(Map.of(
				"name", ClusterRecordIndexDoc.class.getName(),
				"allPublicMethods", true)));
		}
	}
}
