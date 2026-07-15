package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.RequestShippingContext;
import org.w3c.dom.Document;

class NcipPayloadBuilderTests {
	private final NcipPayloadBuilder builder = new NcipPayloadBuilder();
	private final NcipSchemaValidator validator = new NcipSchemaValidator(schemaPath());

	@Test
	void buildsValidRequestItemPayload() {
		final var xml = builder.requestItem(new NcipRequestItemPayload(
			party(),
			"borrower-agency",
			"patron-123",
			"bib-456",
			"supplier-agency",
			"supplier-agency",
			"barcode",
			"item-456",
			"request-789:SUPPLIER",
			"Hold",
			"Item"));

		assertDoesNotThrow(() -> validator.validate(xml));

		final var document = parse(xml);

		assertThat(document.getDocumentElement().getLocalName(),
			equalTo("NCIPMessage"));
		assertThat(xml, containsString("<RequestItem"));
		assertThat(xml, containsString("<FromSystemId"));
		assertThat(xml, containsString("<FromSystemId>dcb-system</FromSystemId>"));
		assertThat(xml, containsString("<AgencyId>borrower-agency</AgencyId>"));
		assertThat(xml, containsString("<UserIdentifierValue>patron-123</UserIdentifierValue>"));
		assertThat(xml, containsString("<BibliographicRecordIdentifier>bib-456</BibliographicRecordIdentifier>"));
		assertThat(xml, containsString("<ItemIdentifierValue>item-456</ItemIdentifierValue>"));
		assertThat(xml, containsString("<RequestIdentifierValue>request-789:SUPPLIER</RequestIdentifierValue>"));
	}

	@Test
	void buildsValidRequestItemShippingInformation() {
		final var xml = builder.requestItem(new NcipRequestItemPayload(
			party(),
			"borrower-agency",
			"patron-123",
			"bib-456",
			"supplier-agency",
			"supplier-agency",
			"barcode",
			"item-456",
			null,
			"request-789:SUPPLIER",
			"Hold",
			"Bibliographic Item",
			null,
			shippingContext(),
			true));

		assertDoesNotThrow(() -> validator.validate(xml));
		assertThat(xml, containsString("<ShippingInstructions>For pickup by patron-123@borrower-system"));
		assertThat(xml, containsString("<UnstructuredAddressData>Main Library, 1 Main Street"));
		assertThat(xml, containsString("<dcb:ShippingContext"));
		assertThat(xml, containsString("<dcb:RouteMode>Direct</dcb:RouteMode>"));
		assertThat(xml, containsString("<dcb:LocalLocationCode>MAIN</dcb:LocalLocationCode>"));
	}

	@Test
	void buildsValidLookupItemSetPayload() {
		final var xml = builder.lookupItemSet(new NcipLookupItemSetPayload(
			party(),
			"bib-456",
			"supplier-agency"));

		assertDoesNotThrow(() -> validator.validate(xml));
		assertThat(xml, containsString("<LookupItemSet"));
		assertThat(xml, containsString("<BibliographicRecordIdentifier>bib-456</BibliographicRecordIdentifier>"));
	}

	@Test
	void buildsValidAcceptItemPayload() {
		final var xml = builder.acceptItem(new NcipAcceptItemPayload(
			party(),
			"request-789:BORROWER",
			"Accept For Loan",
			"borrower-agency",
			"patron-123",
			"supplier-agency",
			"barcode",
			"item-456",
			new NcipBibliographicDescription(
				"A Philosophy of Software Design, 2nd Edition",
				null,
				"bib-456",
				"supplier-agency",
				"item-456",
				"2nd Edition")));

		assertDoesNotThrow(() -> validator.validate(xml));

		final var document = parse(xml);

		assertThat(document.getDocumentElement().getLocalName(),
			equalTo("NCIPMessage"));
		assertThat(xml, containsString("<AcceptItem"));
		assertThat(xml, containsString("<RequestIdentifierValue>request-789:BORROWER</RequestIdentifierValue>"));
		assertThat(xml, containsString("<RequestedActionType>Accept For Loan</RequestedActionType>"));
		assertThat(xml, containsString("<ItemIdentifierType"));
		assertThat(xml, containsString(">barcode</"));
		assertThat(xml, containsString("<ItemIdentifierValue>item-456</ItemIdentifierValue>"));
		assertThat(xml, containsString("<ItemOptionalFields>"));
		assertThat(xml, containsString("<BibliographicDescription>"));
		assertThat(xml, containsString("<Title>A Philosophy of Software Design, 2nd Edition</Title>"));
		assertThat(xml, containsString("<BibliographicRecordIdentifier>bib-456</BibliographicRecordIdentifier>"));
	}

	@Test
	void buildsValidItemShippedPayload() {
		final var xml = builder.itemShipped(new NcipItemShippedPayload(
			party(),
			"request-789:SUPPLIER",
			"supplier-agency",
			"item-456",
			Instant.parse("2026-07-15T09:30:00Z")));

		assertDoesNotThrow(() -> validator.validate(xml));
		assertThat(xml, containsString("<ItemShipped"));
		assertThat(xml, containsString(
			"<RequestIdentifierValue>request-789:SUPPLIER</RequestIdentifierValue>"));
		assertThat(xml, containsString(
			"<ItemIdentifierValue>item-456</ItemIdentifierValue>"));
		assertThat(xml, containsString(
			"<DateShipped>2026-07-15T09:30:00Z</DateShipped>"));
		assertThat(xml, containsString("<ShippingInformation>"));
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

	private static NcipParty party() {
		return new NcipParty(
			"borrower-agency",
			"supplier-agency",
			"dcb-system",
			"supplier-system");
	}

	private static RequestShippingContext shippingContext() {
		return new RequestShippingContext(
			1,
			"RET-STD",
			"Direct",
			new RequestShippingContext.Patron("patron-123", "borrower-system", "borrower-agency"),
			new RequestShippingContext.Endpoint("borrower-system", "borrower-agency", "Borrower"),
			new RequestShippingContext.Endpoint("supplier-system", "supplier-agency", "Supplier"),
			new RequestShippingContext.PickupDestination(
				"PICKUP_LOCATION",
				new RequestShippingContext.Endpoint("borrower-system", "borrower-agency", "Borrower"),
				"location-id",
				"BORROWER-MAIN",
				"MAIN",
				"Main Library",
				new RequestShippingContext.AddressSnapshot(
					"1 Main Street", "PICKUP_LIBRARY", "DCB_LIBRARY_DIRECTORY")),
			new RequestShippingContext.Provenance(
				"DCB_PATRON_REQUEST", "location-id", "borrower-system", null,
				Instant.parse("2026-07-13T09:00:00Z"), null));
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
