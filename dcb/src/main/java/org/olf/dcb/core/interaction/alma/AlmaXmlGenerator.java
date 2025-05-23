package org.olf.dcb.core.interaction.alma;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for generating XML payloads
 * compatible with the Ex Libris Alma API.
*/
@Slf4j
public class AlmaXmlGenerator {

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final DateTimeFormatter DATE_008_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

	/**
	 * Generates a basic Alma-compatible bibliographic MARC21 XML payload.
	 *
	 * @param title  Title of the bibliographic record (MARC 245 field)
	 * @param author Author of the work (MARC 100 and 245 fields)
	 * @return XML string to be sent as request body to POST /almaws/v1/bibs
	 *
	 * @throws IllegalArgumentException if title is null/empty
	 */
	public static String createBibXml(String title, String author) {
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("Title must not be null or empty.");
		}
		if (author == null || author.isBlank()) {
			log.warn("Author is null or empty.");
			author = "";
		}

		String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
		String date008 = LocalDateTime.now().format(DATE_008_FORMAT);

		return """
			<?xml version="1.0" encoding="UTF-8"?>
			<bib>
			  <suppress_from_publishing>false</suppress_from_publishing>
			  <record>
			    <leader>00000nam a2200000 a 4500</leader>
			    <controlfield tag="001">DCB%s</controlfield>
			    <controlfield tag="005">%s.0</controlfield>
			    <controlfield tag="008">%ss2023    xxu           000 0 eng d</controlfield>
			    <datafield tag="020" ind1=" " ind2=" ">
			      <subfield code="a">978-0-DCB-%s</subfield>
			    </datafield>
			    <datafield tag="100" ind1="1" ind2=" ">
			      <subfield code="a">%s</subfield>
			    </datafield>
			    <datafield tag="245" ind1="1" ind2="0">
			      <subfield code="a">%s</subfield>
			      <subfield code="c">%s</subfield>
			    </datafield>
			    <datafield tag="260" ind1=" " ind2=" ">
			      <subfield code="a">DCB City</subfield>
			      <subfield code="b">DCB Publisher</subfield>
			      <subfield code="c">2023</subfield>
			    </datafield>
			    <datafield tag="300" ind1=" " ind2=" ">
			      <subfield code="a">100 p.</subfield>
			    </datafield>
			    <datafield tag="650" ind1=" " ind2="0">
			      <subfield code="a">DCB Subject</subfield>
			    </datafield>
			  </record>
			</bib>
			""".formatted(timestamp, timestamp, date008, timestamp, author, title, author).trim();
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
