package org.olf.dcb.request.lifecycle.ncip;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

public class NcipSchemaValidator {
	private final Schema schema;

	public NcipSchemaValidator(Path schemaPath) {
		try {
			final var schemaFactory = SchemaFactory.newInstance(
				XMLConstants.W3C_XML_SCHEMA_NS_URI);
			schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

			this.schema = schemaFactory.newSchema(schemaPath.toFile());
		}
		catch (SAXException e) {
			throw new IllegalArgumentException(
				"Could not load NCIP schema from " + schemaPath, e);
		}
	}

	public void validate(String xml) {
		try {
			schema.newValidator().validate(
				new StreamSource(new StringReader(xml)));
		}
		catch (SAXException | IOException e) {
			throw new IllegalArgumentException("Invalid NCIP XML", e);
		}
	}
}
