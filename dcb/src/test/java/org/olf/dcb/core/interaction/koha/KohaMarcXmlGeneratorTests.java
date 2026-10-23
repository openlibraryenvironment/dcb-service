package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

/**
 * The MARC a Koha virtual bib is created from. It stands in for a title held elsewhere,
 * so it must never surface in the library's OPAC or come back through DCB's own harvest.
 */
class KohaMarcXmlGeneratorTests {
	@Test
	void shouldSuppressTheVirtualBibFromTheOpac() {
		final var marc = KohaMarcXmlGenerator.createBibXml("A title", "An author");

		// Koha's OpacSuppression flag, and the first condition of the koha-default ruleset
		assertThat(marc, containsString("<subfield code=\"n\">1</subfield>"));
		assertThat(marc, containsString("<subfield code=\"c\">DCB_VIRTUAL</subfield>"));
	}

	@Test
	void shouldCarryTheTitleAndAuthor() {
		final var marc = KohaMarcXmlGenerator.createBibXml("A title", "An author");

		assertThat(marc, containsString("<datafield tag=\"245\""));
		assertThat(marc, containsString("<subfield code=\"a\">A title</subfield>"));
		assertThat(marc, containsString("<datafield tag=\"100\""));
		assertThat(marc, containsString("<subfield code=\"a\">An author</subfield>"));
	}

	@Test
	void shouldEscapeMarkupInTheTitle() {
		final var marc = KohaMarcXmlGenerator.createBibXml("Tom & Jerry <vol. 1>", null);

		assertThat(marc, containsString("Tom &amp; Jerry &lt;vol. 1&gt;"));
		assertThat(marc, not(containsString("<datafield tag=\"100\"")));
	}
}
