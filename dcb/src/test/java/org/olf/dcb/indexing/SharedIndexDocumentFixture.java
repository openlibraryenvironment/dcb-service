package org.olf.dcb.indexing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.olf.dcb.availability.job.BibAvailabilityCount;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.indexing.model.ClusterRecordIndexDoc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.json.jackson.JacksonJsonpMapper;

/**
 * The document the shared index actually writes, serialized through the same
 * JsonpMapper the index service uses.
 *
 * <p>One fixture rather than two, because two tests ask different questions of the
 * same document: whether the mapping declares the fields it emits, and whether
 * those declarations can merge into an index that already exists. A second copy
 * would drift, and drift would silently weaken whichever test kept the older shape.
 */
public final class SharedIndexDocumentFixture {

	public static final UUID CLUSTER_ID = UUID.fromString("6f1a5cf6-0000-4000-8000-00000000c1c1");
	public static final UUID BIB_ID = UUID.fromString("6f1a5cf6-0000-4000-8000-00000000b1b1");

	public static final Map<String, Object> METADATA = Map.of(
		"identifiers", List.of(Map.of("namespace", "ISBN", "value", "9780000000001")));

	private static final ObjectMapper JSON = new ObjectMapper();

	private SharedIndexDocumentFixture() {
	}

	public static JsonNode serialize() throws Exception {
		final var mapper = new JacksonJsonpMapper();
		final var output = new StringWriter();

		try (var generator = mapper.jsonProvider().createGenerator(output)) {
			mapper.serialize(document(), generator);
		}

		return JSON.readTree(output.toString());
	}

	public static ClusterRecordIndexDoc document() {
		final var bib = mock(BibRecord.class);
		final var cluster = mock(ClusterRecord.class);
		final var availability = mock(BibAvailabilityCount.class);

		when(bib.getId()).thenReturn(BIB_ID);
		when(bib.getTitle()).thenReturn("Member title");
		when(bib.getSourceRecordId()).thenReturn("member-1");
		when(bib.getSourceSystemId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000123"));
		when(bib.getCanonicalMetadata()).thenReturn(METADATA);
		when(cluster.getId()).thenReturn(CLUSTER_ID);
		when(cluster.getTitle()).thenReturn("DCB discovery serialization fixture");
		when(cluster.getLastIndexed()).thenReturn(Instant.parse("2026-08-18T09:00:00Z"));
		when(cluster.getSelectedBib()).thenReturn(BIB_ID);
		when(cluster.getBibs()).thenReturn(Set.of(bib));
		when(availability.getInternalLocationCode()).thenReturn("main");
		when(availability.getRemoteLocationCode()).thenReturn("stacks");
		when(availability.getCount()).thenReturn(3);

		return new ClusterRecordIndexDoc(cluster, ignored -> "fixture-lms",
			Map.of(BIB_ID.toString(), List.of(availability)));
	}
}
