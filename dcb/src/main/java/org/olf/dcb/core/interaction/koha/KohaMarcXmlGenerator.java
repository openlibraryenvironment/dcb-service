package org.olf.dcb.core.interaction.koha;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for generating MARCXML payloads
 * compatible with the Koha REST API. Probably needs more work to get the virtual bibs where we want them to be.
 */
@Slf4j
public class KohaMarcXmlGenerator {

	private static final DateTimeFormatter DATE_008_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

	/**
	 * Generates a basic Koha-compatible bibliographic MARCXML payload.
	 *
	 * @param title  Title of the bibliographic record (MARC 245 field)
	 * @param author Author of the work (MARC 100 field)
	 * @return XML string to be sent as request body to POST /api/v1/biblios
	 */
	public static String createBibXml(String title, String author) {
		if (title == null || title.isBlank()) {
			title = "DCB Virtual Record";
		}

		boolean hasAuthor = author != null && !author.isBlank();
		String date008 = LocalDateTime.now().format(DATE_008_FORMAT);

		StringBuilder xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<record xmlns=\"http://www.loc.gov/MARC21/slim\">\n")
			.append("  <leader>00000nam a2200000 a 4500</leader>\n")
			.append("  <controlfield tag=\"008\">").append(date008).append("s2026    xxu           000 0 eng d</controlfield>\n");

		if (hasAuthor) {
			xml.append("  <datafield tag=\"100\" ind1=\"1\" ind2=\" \">\n")
				.append("    <subfield code=\"a\">").append(escapeXml(author)).append("</subfield>\n")
				.append("  </datafield>\n");
		}

		xml.append("  <datafield tag=\"245\" ind1=\"1\" ind2=\"0\">\n")
			.append("    <subfield code=\"a\">").append(escapeXml(title)).append("</subfield>\n")
			.append("  </datafield>\n")
			.append("  <datafield tag=\"942\" ind1=\" \" ind2=\" \">\n")
			.append("    <subfield code=\"c\">DCB_VIRTUAL</subfield>\n") // Optional: highly recommended to assign a specific Koha bib-level item type
			.append("  </datafield>\n")
			.append("</record>");

		return xml.toString();
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
