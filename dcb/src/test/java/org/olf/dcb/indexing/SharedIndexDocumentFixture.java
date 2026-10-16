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
 * <p>{@link #document()} is what this build writes, shared by every test asking what
 * the current mapping must declare. {@link #lastReleasedDocument()} is what the
 * baseline release wrote, and exists only because the merge check indexes into an
 * index that release built.
 */
public final class SharedIndexDocumentFixture {

	public static final UUID CLUSTER_ID = UUID.fromString("6f1a5cf6-0000-4000-8000-00000000c1c1");
	public static final UUID BIB_ID = UUID.fromString("6f1a5cf6-0000-4000-8000-00000000b1b1");

	public static final Map<String, Object> METADATA = Map.of(
		"identifiers", List.of(Map.of("namespace", "ISBN", "value", "9780000000001")),
		// What MarcIngestSource already writes from MARC 082, 050 and 084. The shelf fields
		// are derived from this rather than from anything new at ingest.
		"classifications", List.of(
			Map.of("subtype", "dewey", "label", "638.1"),
			Map.of("subtype", "lcc", "label", "PS3566.Y55"),
			Map.of("subtype", "other", "label", "LOCAL SHELF 3")));

	/** METADATA as of the release mappings-2.last-released.json was taken from. */
	private static final Map<String, Object> LAST_RELEASED_METADATA = Map.of(
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
		return document(METADATA, Instant.parse("2026-07-01T09:00:00Z"));
	}

	/**
	 * The document as the last release wrote it, for the merge check alone.
	 *
	 * Pinned beside mappings-2.last-released.json and refreshed with it. The merge check
	 * asks whether this build's mapping applies to an index built by that release, and an
	 * index built by that release holds documents that release wrote — so feeding it a
	 * CURRENT document models a combination that cannot occur, and fails on fields the two
	 * shipped together. members.classification is the worked example: emitted and declared
	 * by one commit, so no index can hold it undeclared.
	 */
	public static ClusterRecordIndexDoc lastReleasedDocument() {
		return document(LAST_RELEASED_METADATA, null);
	}

	private static ClusterRecordIndexDoc document(Map<String, Object> metadata,
			Instant dateCreated) {

		final var bib = mock(BibRecord.class);
		final var cluster = mock(ClusterRecord.class);
		final var availability = mock(BibAvailabilityCount.class);

		when(bib.getId()).thenReturn(BIB_ID);
		when(bib.getTitle()).thenReturn("Member title");
		when(bib.getSourceRecordId()).thenReturn("member-1");
		when(bib.getSourceSystemId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000123"));
		when(bib.getCanonicalMetadata()).thenReturn(metadata);
		when(cluster.getId()).thenReturn(CLUSTER_ID);
		when(cluster.getTitle()).thenReturn("DCB discovery serialization fixture");
		when(cluster.getLastIndexed()).thenReturn(Instant.parse("2026-08-18T09:00:00Z"));
		when(cluster.getDateCreated()).thenReturn(dateCreated);
		when(cluster.getSelectedBib()).thenReturn(BIB_ID);
		when(cluster.getBibs()).thenReturn(Set.of(bib));
		// The agency the location mapped to, not a location — see NestedBibIndexDoc.getAvailability
		when(availability.getInternalLocationCode()).thenReturn("AGENCY-A");
		when(availability.getRemoteLocationCode()).thenReturn("stacks");
		when(availability.getCount()).thenReturn(3);

		return new ClusterRecordIndexDoc(cluster, ignored -> "fixture-lms",
			Map.of(BIB_ID.toString(), List.of(availability)));
	}
}
