package org.olf.dcb.ingest.marc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.impl.MarcFactoryImpl;
import org.olf.dcb.ingest.model.IngestRecord;

class MarcCanonicalMetadataTests {
	private static final MarcFactory MARC = new MarcFactoryImpl();

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void shouldPreserveRichPublicMetadata() {
		final var record = MARC.newRecord();
		record.addVariableField(field("100", '1', ' ', 'a', "Example, Alex", 'e', "author", '0', "auth-1"));
		record.addVariableField(field("650", ' ', '0', 'a', "Libraries", 'x', "Automation", '0', "subject-1"));
		record.addVariableField(field("300", ' ', ' ', 'a', "1 volume", 'b', "illustrations", 'c', "24 cm"));
		record.addVariableField(field("336", ' ', ' ', 'a', "text"));
		record.addVariableField(field("337", ' ', ' ', 'a', "unmediated"));
		record.addVariableField(field("338", ' ', ' ', 'a', "volume"));
		record.addVariableField(field("490", '1', ' ', 'a', "Example series", 'v', "12"));
		record.addVariableField(field("050", ' ', '0', 'a', "Z678.9", 'b', ".E93"));
		record.addVariableField(field("084", ' ', ' ', 'a', "TEST 1", '2', "local"));
		record.addVariableField(field("773", '0', ' ', 'i', "In:", 't', "Host work", 'w', "HOST-1"));
		record.addVariableField(field("880", '1', '0', '6', "245-01/(2/r", 'a', "並列タイトル"));

		final MarcIngestSource source = mock(MarcIngestSource.class, CALLS_REAL_METHODS);
		final var metadata = source.enrichWithCanonicalRecord(
			IngestRecord.builder().sourceRecordId("rich-1").title("Rich record"), record)
			.build().getCanonicalMetadata();

		final var agents = (List<Map<String, Object>>) metadata.get("agents");
		assertThat(metadata.get("dcbMarcIngestSeq"), is("2"));
		assertThat(agents.getFirst(), hasEntry("role", "author"));
		assertThat(agents.getFirst(), hasEntry("primary", true));
		assertThat(agents.getFirst(), hasEntry("authority", "auth-1"));

		final var subjects = (List<Map<String, Object>>) metadata.get("subjects");
		assertThat(subjects.getFirst(), hasEntry("fullLabel", "Libraries Automation"));
		assertThat(subjects.getFirst(), hasEntry("authority", "subject-1"));

		assertThat((List<String>) metadata.get("series"), contains("Example series 12"));
		assertThat(label(metadata, "physical-description"), is("1 volume illustrations 24 cm"));
		assertThat(label(metadata, "content-type"), is("text"));
		assertThat(label(metadata, "media-type"), is("unmediated"));
		assertThat(label(metadata, "carrier-type"), is("volume"));

		final var classifications = (List<Map<String, Object>>) metadata.get("classifications");
		assertThat(classifications.get(0), hasEntry("label", "Z678.9 .E93"));
		assertThat(classifications.get(0), hasEntry("subtype", "lcc"));
		assertThat(classifications.get(1), hasEntry("source", "local"));

		final var relationships = (List<Map<String, Object>>) metadata.get("relationships");
		assertThat(relationships.getFirst(), hasEntry("type", "part-of"));
		assertThat(relationships.getFirst(), hasEntry("label", "Host work"));
		final var alternateScripts = (List<Map<String, Object>>) metadata.get("alternateScripts");
		assertThat(alternateScripts.getFirst(), hasEntry("linkedTag", "245"));
		assertThat(alternateScripts.getFirst(), hasEntry("label", "並列タイトル"));
	}

	@SuppressWarnings("unchecked")
	private static String label(Map<String, Object> metadata, String property) {
		return (String) ((List<Map<String, Object>>) metadata.get(property)).getFirst().get("label");
	}

	private static DataField field(String tag, char indicator1, char indicator2, Object... subfields) {
		final var field = MARC.newDataField(tag, indicator1, indicator2);
		for (int index = 0; index < subfields.length; index += 2) {
			field.addSubfield(MARC.newSubfield((char) subfields[index], (String) subfields[index + 1]));
		}
		return field;
	}
}
