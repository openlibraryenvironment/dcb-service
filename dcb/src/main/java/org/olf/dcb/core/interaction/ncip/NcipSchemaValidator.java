package org.olf.dcb.core.interaction.ncip;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

/**
 * Validates NCIP XML against the packaged NCIP v2.02 schema. Shared by every
 * NCIP caller so imperative and declarative payloads are held to the same wire
 * contract.
 */
public class NcipSchemaValidator {
	private final Schema schema;

	public NcipSchemaValidator(Path schemaPath) {
		try {
			final var schemaFactory = SchemaFactory.newInstance(
				XMLConstants.W3C_XML_SCHEMA_NS_URI);
			schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");

			this.schema = schemaFactory.newSchema(schemaSource(schemaPath));
		}
		catch (SAXException e) {
			throw new IllegalArgumentException(
				"Could not load NCIP schema from " + schemaPath, e);
		}
	}

	/** Convenience factory that resolves the schema via {@link NcipSchemaPath}. */
	public static NcipSchemaValidator usingDefaultSchema() {
		return new NcipSchemaValidator(NcipSchemaPath.schemaPath());
	}

	public void validate(String xml) {
		try {
			schema.newValidator().validate(
				new StreamSource(new StringReader(xml)));
		}
		catch (SAXException | IOException e) {
			throw new IllegalArgumentException(
				"Invalid NCIP XML: " + e.getMessage(), e);
		}
	}

	private static StreamSource schemaSource(Path schemaPath) {
		final var extensionSchema = schemaPath.resolveSibling(
			"openrs_ncip_extension.xsd");

		if (!Files.exists(extensionSchema)) {
			return new StreamSource(schemaPath.toFile());
		}

		final var wrapper = """
			<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
			  <xs:import namespace="http://www.niso.org/2008/ncip" schemaLocation="%s"/>
			  <xs:import namespace="https://openrs.org/ncip/fallback-host" schemaLocation="%s"/>
			</xs:schema>
			""".formatted(schemaPath.toUri(), extensionSchema.toUri());

		return new StreamSource(new StringReader(wrapper));
	}
}
