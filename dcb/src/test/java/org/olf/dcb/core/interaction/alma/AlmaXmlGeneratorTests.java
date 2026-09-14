package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

class AlmaXmlGeneratorTests {
	@Test
	void shouldEscapeMarkupInTheTitleAndAuthor() throws Exception {
		final var document = parse(AlmaXmlGenerator.createBibXml("Pride & Prejudice <1813>", "Austen, Jane & Co"));

		assertThat(subfieldA(document, "245"), is("Pride & Prejudice <1813>"));
		assertThat(subfieldA(document, "100"), is("Austen, Jane & Co"));
	}

	@Test
	void shouldNotWriteInventedCatalogueData() throws Exception {
		final var tags = datafieldTags(parse(AlmaXmlGenerator.createBibXml("A title", "An author")));

		for (String invented : List.of("020", "260", "300", "650")) {
			assertThat(tags, not(hasItem(invented)));
		}
	}

	@Test
	void shouldOmitTheAuthorFieldWhenThereIsNoAuthor() throws Exception {
		final var document = parse(AlmaXmlGenerator.createBibXml("A title", " "));

		assertThat(subfieldA(document, "100"), is(nullValue()));
		assertThat(datafield(document, "245").getAttribute("ind1"), is("0"));
	}

	private static Document parse(String xml) throws Exception {
		return DocumentBuilderFactory.newInstance()
			.newDocumentBuilder()
			.parse(new InputSource(new StringReader(xml)));
	}

	private static List<String> datafieldTags(Document document) {
		final var fields = document.getElementsByTagName("datafield");
		final var tags = new ArrayList<String>();

		for (int i = 0; i < fields.getLength(); i++) {
			tags.add(((Element) fields.item(i)).getAttribute("tag"));
		}

		return tags;
	}

	private static Element datafield(Document document, String tag) {
		final var fields = document.getElementsByTagName("datafield");

		for (int i = 0; i < fields.getLength(); i++) {
			final var field = (Element) fields.item(i);

			if (tag.equals(field.getAttribute("tag"))) {
				return field;
			}
		}

		return null;
	}

	private static String subfieldA(Document document, String tag) {
		final var field = datafield(document, tag);

		return field == null
			? null
			: field.getElementsByTagName("subfield").item(0).getTextContent();
	}
}
