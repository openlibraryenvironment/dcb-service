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
import org.olf.dcb.indexing.model.ShelfSortKey;

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
	void shouldSerializeTheShelfFieldsPerMemberRatherThanPerCluster() throws Exception {
		// A classification is assigned by the CATALOGUING library, so it belongs beside that
		// library's code. A cluster-level copy would be whichever member happened to be
		// selected, which is not the sequence any particular reader is looking at.
		final var source = SharedIndexDocumentFixture.serialize();

		assertThat(source.at("/members/0/classification/dewey").asText(), is("638.1"));
		assertThat(source.at("/members/0/classification/lcc").asText(), is("PS3566.Y55"));
		assertThat(source.at("/members/0/shelfSort/dewey").asText(),
			is(ShelfSortKey.of("dewey", "638.1")));
		assertThat(source.at("/members/0/shelfSort/lcc").asText(),
			is(ShelfSortKey.of("lcc", "PS3566.Y55")));

		// 084 is displayed and never sorted: it names its own scheme in $2 and can carry a
		// local shelving code, so a sort key for it would be a guess.
		assertThat(source.at("/members/0/classification/other").asText(), is("LOCAL SHELF 3"));
		assertThat(source.at("/members/0/shelfSort").has("other"), is(false));
	}

	@Test
	void shouldSerializeFirstSeenSeparatelyFromLastIndexed() throws Exception {
		// A reindex rewrites lastIndexed for every document, so new arrivals sorted on it
		// would declare the whole catalogue new the morning after one. The two carrying
		// different instants is what makes this assertion worth making.
		final var source = SharedIndexDocumentFixture.serialize();

		assertThat(source.get("firstSeen").asText(), is("2026-07-01T09:00:00Z"));
		assertThat(source.get("lastIndexed").asText(), is("2026-08-18T09:00:00Z"));
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
