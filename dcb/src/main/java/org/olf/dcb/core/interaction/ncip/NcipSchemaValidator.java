package org.olf.dcb.core.interaction.ncip;

import java.io.IOException;
import java.io.StringReader;
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

			this.schema = schemaFactory.newSchema(new StreamSource(schemaPath.toFile()));
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

}
