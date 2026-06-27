package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class NcipPayloadBuilderTests {
	private final NcipPayloadBuilder builder = new NcipPayloadBuilder();
	private final NcipSchemaValidator validator = new NcipSchemaValidator(schemaPath());

	@Test
	void buildsValidRequestItemPayload() {
		final var xml = builder.requestItem(new NcipRequestItemPayload(
			"patron-123",
			"bib-456",
			"supplier-agency",
			"request-789:SUPPLIER",
			"Hold",
			"Item"));

		assertDoesNotThrow(() -> validator.validate(xml));

		final var document = parse(xml);

		assertThat(document.getDocumentElement().getLocalName(),
			equalTo("NCIPMessage"));
		assertThat(xml, containsString("<RequestItem"));
		assertThat(xml, containsString("<UserIdentifierValue>patron-123</UserIdentifierValue>"));
		assertThat(xml, containsString("<BibliographicRecordIdentifier>bib-456</BibliographicRecordIdentifier>"));
		assertThat(xml, containsString("<RequestIdentifierValue>request-789:SUPPLIER</RequestIdentifierValue>"));
	}

	@Test
	void buildsValidAcceptItemPayload() {
		final var xml = builder.acceptItem(new NcipAcceptItemPayload(
			"request-789:BORROWER",
			"Accept For Loan",
			"patron-123",
			"item-456"));

		assertDoesNotThrow(() -> validator.validate(xml));

		final var document = parse(xml);

		assertThat(document.getDocumentElement().getLocalName(),
			equalTo("NCIPMessage"));
		assertThat(xml, containsString("<AcceptItem"));
		assertThat(xml, containsString("<RequestIdentifierValue>request-789:BORROWER</RequestIdentifierValue>"));
		assertThat(xml, containsString("<RequestedActionType>Accept For Loan</RequestedActionType>"));
		assertThat(xml, containsString("<ItemIdentifierValue>item-456</ItemIdentifierValue>"));
	}

	private static Document parse(String xml) {
		try {
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);

			return factory.newDocumentBuilder().parse(
				new ByteArrayInputStream(xml.getBytes()));
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Could not parse NCIP XML", e);
		}
	}

	private static Path schemaPath() {
		final var workingDirectory = Paths.get("").toAbsolutePath();
		final var repositorySchema = workingDirectory.resolve(
			"src/xsd/ncip_v2_02.xsd");

		if (Files.exists(repositorySchema)) {
			return repositorySchema;
		}

		final var moduleSchema = workingDirectory.resolve(
			"../src/xsd/ncip_v2_02.xsd").normalize();

		if (Files.exists(moduleSchema)) {
			return moduleSchema;
		}

		throw new IllegalStateException(
			"Could not find NCIP schema from " + workingDirectory);
	}
}
