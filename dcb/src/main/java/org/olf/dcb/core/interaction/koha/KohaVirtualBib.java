package org.olf.dcb.core.interaction.koha;

import static io.micrometer.common.util.StringUtils.isBlank;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.marc4j.MarcJsonWriter;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.impl.MarcFactoryImpl;
import org.olf.dcb.core.interaction.Bib;

/**
 * The MARC record a Koha virtual bib is created from, sent as MARC-in-JSON so that no value
 * is escaped by hand. The leader and 008 are copies of Polaris's
 * ({@code ApplicationServicesClient.buildLeader} and {@code build008}): change both or neither.
 */
final class KohaVirtualBib {
	static final String MARC_IN_JSON = "application/marc-in-json";

	private static final String FALLBACK_TITLE = "DCB Virtual Record";

	private static final MarcFactory MARC = new MarcFactoryImpl();

	private KohaVirtualBib() { }

	static String marcInJson(Bib bib) {
		final var out = new ByteArrayOutputStream();
		final var writer = new MarcJsonWriter(out, MarcJsonWriter.MARC_IN_JSON);
		writer.write(record(bib));
		writer.close();

		return out.toString(StandardCharsets.UTF_8);
	}

	static Record record(Bib bib) {
		final var title = bib == null || isBlank(bib.getTitle()) ? FALLBACK_TITLE : bib.getTitle();
		final var author = bib == null ? null : bib.getAuthor();

		final var record = MARC.newRecord(leader(bib == null ? null : bib.getTypeOfRecord()));
		record.addVariableField(MARC.newControlField("008", fixedLengthDataElements()));

		if (!isBlank(author)) {
			final var mainEntry = MARC.newDataField("100", '1', ' ');
			mainEntry.addSubfield(MARC.newSubfield('a', author));
			record.addVariableField(mainEntry);
		}

		final var titleStatement = MARC.newDataField("245", '1', '0');
		titleStatement.addSubfield(MARC.newSubfield('a', title));
		record.addVariableField(titleStatement);

		// 942$n is Koha's OpacSuppression flag and the first condition of the koha-default
		// ruleset: without it the virtual bib shows in the OPAC and DCB's own OAI harvest
		// reads it back as a holding of the borrowing library
		final var kohaFields = MARC.newDataField("942", ' ', ' ');
		kohaFields.addSubfield(MARC.newSubfield('c', "DCB_VIRTUAL"));
		kohaFields.addSubfield(MARC.newSubfield('n', "1"));
		record.addVariableField(kohaFields);

		return record;
	}

	static String leader(String typeOfRecord) {
		final char type = !isBlank(typeOfRecord) ? typeOfRecord.charAt(0) : 'a';

		return "00000"        // 00-04 record length, recalculated on ingest
			+ "c"               // 05 record status: corrected or revised
			+ type              // 06 type of record: the source material type
			+ "m"               // 07 bibliographic level: monograph
			+ " "               // 08 type of control: no specified type
			+ "a"               // 09 character coding scheme: unicode
			+ "22"              // 10-11 indicator count and subfield code count
			+ "00000"           // 12-16 base address of data, recalculated on ingest
			+ " "               // 17 encoding level: full level
			+ "a"               // 18 descriptive cataloguing form: AACR2
			+ " "               // 19 multipart resource record level
			+ "4500";           // 20-23 entry map
	}

	static String fixedLengthDataElements() {
		final String dateEntered = ofPattern("yyMMdd").withZone(UTC).format(Instant.now());

		return dateEntered
			+ "n"             // 06 no dates given
			+ "||||"          // 07-10 date 1 uncoded
			+ "||||"          // 11-14 date 2 uncoded
			+ "xx "           // 15-17 place of publication unknown
			+ "|".repeat(17)  // 18-34 material specific, uncoded
			+ "und"           // 35-37 language undetermined
			+ "|"             // 38 modified record
			+ "d";            // 39 cataloguing source: other
	}
}
