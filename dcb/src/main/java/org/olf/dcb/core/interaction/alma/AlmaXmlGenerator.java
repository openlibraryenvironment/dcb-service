package org.olf.dcb.core.interaction.alma;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for generating XML payloads
 * compatible with the Ex Libris Alma API.
*/
public class AlmaXmlGenerator {

	private static final DateTimeFormatter DATE_008_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

	/**
	 * Generates a basic Alma-compatible bibliographic MARC21 XML payload.
	 *
	 * @param title  Title of the bibliographic record (MARC 245 field)
	 * @param author Author of the work (MARC 100 field), omitted when blank
	 * @return XML string to be sent as request body to POST /almaws/v1/bibs
	 *
	 * @throws IllegalArgumentException if title is null/empty
	 */
	public static String createBibXml(String title, String author) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("Title must not be null or empty.");
		}

		final boolean hasAuthor = author != null && !author.isBlank();

		final LocalDate today = LocalDate.now();

		final var xml = new StringBuilder()
			.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<bib>\n")
			.append("  <suppress_from_publishing>true</suppress_from_publishing>\n")
			.append("  <record>\n")
			.append("    <leader>00000nam a2200000 a 4500</leader>\n")
			// No 001 or 005: Alma writes the MMS id and its own timestamp there. A 001 of our own is
			// a match point an import profile can overlay onto
			.append("    <controlfield tag=\"008\">").append(today.format(DATE_008_FORMAT))
			.append("s").append(today.getYear()).append("    xxu           000 0 eng d</controlfield>\n");

		if (hasAuthor) {
			xml.append("    <datafield tag=\"100\" ind1=\"1\" ind2=\" \">\n")
				.append("      <subfield code=\"a\">").append(escapeXml(author)).append("</subfield>\n")
				.append("    </datafield>\n");
		}

		xml.append("    <datafield tag=\"245\" ind1=\"").append(hasAuthor ? "1" : "0").append("\" ind2=\"0\">\n")
			.append("      <subfield code=\"a\">").append(escapeXml(title)).append("</subfield>\n")
			.append("    </datafield>\n")
			.append("  </record>\n")
			.append("</bib>");

		return xml.toString();
	}

	public static String generateHoldingXml(String locationCode, String shelvingLocation, String callNumber, String holdingNote) {
		if (locationCode == null || locationCode.isBlank()) {
			throw new IllegalArgumentException("Location code must not be null or empty.");
		}
		if (shelvingLocation == null || shelvingLocation.isBlank()) {
			throw new IllegalArgumentException("Shelving location must not be null or empty.");
		}
		if (callNumber == null || callNumber.isBlank()) {
			throw new IllegalArgumentException("Call number must not be null or empty.");
		}
		if (holdingNote == null || holdingNote.isBlank()) {
			throw new IllegalArgumentException("Holding note must not be null or empty.");
		}

		return """
		<?xml version="1.0" encoding="UTF-8"?>
		<holding>
		  <record>
		    <datafield tag="852" ind1="0" ind2=" ">
		      <subfield code="b">%s</subfield>
		      <subfield code="c">%s</subfield>
		      <subfield code="h">%s</subfield>
		    </datafield>
		    <datafield tag="866" ind1=" " ind2=" ">
		      <subfield code="a">%s</subfield>
		    </datafield>
		  </record>
		  <suppress_from_publishing>true</suppress_from_publishing>
		</holding>
		""".formatted(
			escapeXml(locationCode),
			escapeXml(shelvingLocation),
			escapeXml(callNumber),
			escapeXml(holdingNote)
		).trim();
	}

	private static String escapeXml(String input) {
		if (input == null) return "";
		return input.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&apos;");
	}
}
