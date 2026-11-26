package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.marc4j.MarcJsonReader;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.olf.dcb.core.interaction.Bib;

/**
 * The MARC a Koha virtual bib is created from, read back as Koha reads it. It stands in for a
 * title held elsewhere, so it must never surface in the library's OPAC or come back through
 * DCB's own harvest.
 */
class KohaVirtualBibTests {
	@Test
	void shouldSuppressTheVirtualBibFromTheOpac() {
		final var record = sent(bib("A title", "An author", null));

		// Koha's OpacSuppression flag, and the first condition of the koha-default ruleset
		assertThat(subfield(record, "942", 'n'), is("1"));
		assertThat(subfield(record, "942", 'c'), is("DCB_VIRTUAL"));
	}

	@Test
	void shouldCarryTheTitleAndAuthor() {
		final var record = sent(bib("A title", "An author", null));

		assertThat(subfield(record, "245", 'a'), is("A title"));
		assertThat(subfield(record, "100", 'a'), is("An author"));
	}

	@Test
	void shouldCarryMarkupAndControlCharactersUnchanged() {
		final var title = "Tom & Jerry <vol. 1> \"quoted\" \u0001 café";

		assertThat(subfield(sent(bib(title, null, null)), "245", 'a'), is(title));
	}

	@Test
	void shouldLeaveOutTheMainEntryWithoutAnAuthor() {
		assertThat(sent(bib("A title", null, null)).getVariableField("100"), is(nullValue()));
		assertThat(sent(bib("A title", " ", null)).getVariableField("100"), is(nullValue()));
	}

	@Test
	void shouldGiveATitleWhenTheSourceHasNone() {
		assertThat(subfield(sent(bib(null, "An author", null)), "245", 'a'), is("DCB Virtual Record"));
		assertThat(subfield(sent(null), "245", 'a'), is("DCB Virtual Record"));
	}

	@Test
	void shouldTypeTheRecordAsTheSourceMaterial() {
		assertThat(sent(bib("A film", null, "g")).getLeader().getTypeOfRecord(), is('g'));
		assertThat(sent(bib("A book", null, null)).getLeader().getTypeOfRecord(), is('a'));
		assertThat(KohaVirtualBib.leader("j"), hasLength(24));
	}

	@Test
	void shouldClaimNoPublicationDates() {
		final var fixed = KohaVirtualBib.fixedLengthDataElements();

		assertThat(fixed, hasLength(40));
		assertThat(fixed.substring(6, 15), is("n||||||||"));
		assertThat(fixed.substring(35, 38), is("und"));
	}

	static Record marcInJson(String body) {
		final var reader = new MarcJsonReader(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

		return reader.next();
	}

	static String subfield(Record record, String tag, char code) {
		return ((DataField) record.getVariableField(tag)).getSubfield(code).getData();
	}

	private static Record sent(Bib bib) {
		return marcInJson(KohaVirtualBib.marcInJson(bib));
	}

	private static Bib bib(String title, String author, String typeOfRecord) {
		return Bib.builder().title(title).author(author).typeOfRecord(typeOfRecord).build();
	}
}
