package org.olf.dcb.indexing.elasticsearch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.indexing.SharedIndexDocumentFixture;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ElasticsearchIndexDocumentSerializationTests {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void shouldSerializeDiscoveryFieldsIntoTheElasticsearchSource() throws Exception {
		final var source = SharedIndexDocumentFixture.serialize();

		assertThat(source.get("bibClusterId").asText(),
			is(SharedIndexDocumentFixture.CLUSTER_ID.toString()));
		assertThat(source.get("title").asText(), is("DCB discovery serialization fixture"));
		assertThat(source.get("metadata"),
			equalTo(JSON.valueToTree(SharedIndexDocumentFixture.METADATA)));
		assertThat(source.at("/members/0/bibId").asText(),
			is(SharedIndexDocumentFixture.BIB_ID.toString()));
		assertThat(source.at("/members/0/sourceSystemCode").asText(), is("fixture-lms"));
		assertThat(source.at("/members/0/sourceRecordId").asText(), is("member-1"));
		assertThat(source.at("/members/0/title").asText(), is("Member title"));
		assertThat(source.at("/members/0/primary").asBoolean(), is(true));
		// dcb-locate aggregates on this field name — see NestedBibIndexDoc.AvailabilityEntry
		assertThat(source.at("/members/0/availability/0/library").asText(), is("AGENCY-A"));
		assertThat(source.at("/members/0/availability/0/combined").asText(), is("AGENCY-A.stacks"));
		assertThat(source.at("/members/0/availability/0/count").asInt(), is(3));
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
