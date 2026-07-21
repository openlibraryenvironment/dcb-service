package org.olf.dcb.request.lifecycle.ncip;

import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import jakarta.inject.Singleton;
import org.olf.dcb.core.interaction.RequestShippingContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Singleton
public class NcipPayloadBuilder {
	public static final String NCIP_NAMESPACE = "http://www.niso.org/2008/ncip";
	public static final String NCIP_VERSION = "2.02";
	public static final String OPENRS_SHIPPING_NAMESPACE = "https://openrs.org/ncip/dcb-shipping/v1";

	public String requestItem(NcipRequestItemPayload payload) {
		final var document = newDocument();
		final var requestItem = message(document, "RequestItem");

		requestItem.appendChild(initiationHeader(document, payload.party()));
		requestItem.appendChild(userId(document, payload.userAgencyId(), payload.userIdentifierValue()));
		requestItem.appendChild(bibliographicId(
			document,
			payload.bibliographicRecordIdentifier(),
			payload.bibliographicRecordAgencyId()));
		if (hasText(payload.itemIdentifierValue())) {
			requestItem.appendChild(itemId(
				document,
				payload.itemAgencyId(),
				payload.itemIdentifierType(),
				payload.itemIdentifierValue()));
		}
		if (hasText(payload.localItemIdentifierValue())
			&& !payload.localItemIdentifierValue().equals(payload.itemIdentifierValue())) {
			requestItem.appendChild(itemId(
				document,
				payload.itemAgencyId(),
				"local-item-id",
				payload.localItemIdentifierValue()));
		}
		requestItem.appendChild(requestId(
			document,
			payload.requestIdentifierValue()));
		requestItem.appendChild(value(
			document,
			"RequestType",
			payload.requestType()));
		requestItem.appendChild(value(
			document,
			"RequestScopeType",
			payload.requestScopeType()));
		if (payload.bibliographicDescription() != null
			&& payload.bibliographicDescription().hasContent()) {
			requestItem.appendChild(itemOptionalFields(document, payload.bibliographicDescription()));
		}
		if (payload.shippingContext() != null) {
			requestItem.appendChild(shippingInformation(
				document,
				payload.shippingContext(),
				payload.includeOpenRsShippingExtension()));
		}

		return toXml(document);
	}

	public String lookupItemSet(NcipLookupItemSetPayload payload) {
		final var document = newDocument();
		final var lookupItemSet = message(document, "LookupItemSet");
		lookupItemSet.appendChild(initiationHeader(document, payload.party()));
		lookupItemSet.appendChild(bibliographicId(
			document,
			payload.bibliographicRecordIdentifier(),
			payload.bibliographicRecordAgencyId()));
		return toXml(document);
	}

	public String acceptItem(NcipAcceptItemPayload payload) {
		final var document = newDocument();
		final var acceptItem = message(document, "AcceptItem");

		acceptItem.appendChild(initiationHeader(document, payload.party()));
		acceptItem.appendChild(requestId(
			document,
			payload.requestIdentifierValue()));
		acceptItem.appendChild(value(
			document,
			"RequestedActionType",
			payload.requestedActionType()));

		if (hasText(payload.userIdentifierValue())) {
			acceptItem.appendChild(userId(document, payload.userAgencyId(), payload.userIdentifierValue()));
		}

		if (hasText(payload.itemIdentifierValue())) {
			acceptItem.appendChild(itemId(
				document,
				payload.itemAgencyId(),
				payload.itemIdentifierType(),
				payload.itemIdentifierValue()));
		}

		if (payload.bibliographicDescription() != null
			&& payload.bibliographicDescription().hasContent()) {
			acceptItem.appendChild(itemOptionalFields(
				document,
				payload.bibliographicDescription()));
		}

		return toXml(document);
	}

	public String itemShipped(NcipItemShippedPayload payload) {
		final var document = newDocument();
		final var itemShipped = message(document, NcipProtocol.ITEM_SHIPPED);

		itemShipped.appendChild(initiationHeader(document, payload.party()));
		itemShipped.appendChild(requestId(
			document, payload.requestIdentifierValue()));
		itemShipped.appendChild(itemId(
			document,
			payload.itemAgencyId(),
			null,
			payload.itemIdentifierValue()));
		itemShipped.appendChild(value(
			document, "DateShipped", payload.dateShipped().toString()));
		itemShipped.appendChild(returnShippingInformation(document));

		return toXml(document);
	}

	public String lookupUser(NcipLookupUserPayload payload) {
		final var document = newDocument();
		final var lookupUser = message(document, "LookupUser");

		if (payload.party() != null) {
			lookupUser.appendChild(initiationHeader(document, payload.party()));
		}
		if (payload.hasSecret()) {
			lookupUser.appendChild(authenticationInput(
				document,
				"Username",
				payload.userIdentifierValue()));
			lookupUser.appendChild(authenticationInput(
				document,
				"Password",
				payload.secret()));
		}
		else {
			lookupUser.appendChild(userId(
				document,
				payload.agencyId(),
				payload.userIdentifierValue()));
		}

		return toXml(document);
	}

	private static Element message(Document document, String messageName) {
		final var message = element(document, "NCIPMessage");
		message.setAttributeNS(NCIP_NAMESPACE, "version", NCIP_VERSION);
		document.appendChild(message);

		final var payload = element(document, messageName);
		message.appendChild(payload);

		return payload;
	}

	private static Element initiationHeader(Document document, NcipParty party) {
		final var header = element(document, "InitiationHeader");
		header.appendChild(systemId(document, "FromSystemId", party.fromSystemId()));
		header.appendChild(agencyId(document, "FromAgencyId", party.fromAgencyId()));
		header.appendChild(systemId(document, "ToSystemId", party.toSystemId()));
		header.appendChild(agencyId(document, "ToAgencyId", party.toAgencyId()));
		return header;
	}

	private static Element agencyId(Document document, String wrapperName, String agencyId) {
		final var wrapper = element(document, wrapperName);
		wrapper.appendChild(value(document, "AgencyId", agencyId));
		return wrapper;
	}

	private static Element systemId(Document document, String wrapperName, String systemId) {
		return value(document, wrapperName, systemId);
	}

	private static Element userId(Document document, String userIdentifierValue) {
		return userId(document, null, userIdentifierValue);
	}

	private static Element userId(
		Document document,
		String agencyId,
		String userIdentifierValue) {

		final var userId = element(document, "UserId");
		if (hasText(agencyId)) {
			userId.appendChild(value(document, "AgencyId", agencyId));
		}
		userId.appendChild(value(
			document,
			"UserIdentifierValue",
			userIdentifierValue));

		return userId;
	}

	private static Element authenticationInput(
		Document document,
		String inputType,
		String data) {

		final var authenticationInput = element(document, "AuthenticationInput");
		authenticationInput.appendChild(value(
			document,
			"AuthenticationInputData",
			data));
		authenticationInput.appendChild(value(
			document,
			"AuthenticationDataFormatType",
			"Text"));
		authenticationInput.appendChild(value(
			document,
			"AuthenticationInputType",
			inputType));

		return authenticationInput;
	}

	private static Element itemId(
		Document document,
		String agencyId,
		String itemIdentifierType,
		String itemIdentifierValue) {

		final var itemId = element(document, "ItemId");
		if (hasText(agencyId)) {
			itemId.appendChild(value(document, "AgencyId", agencyId));
		}
		if (hasText(itemIdentifierType)) {
			itemId.appendChild(schemeValue(document, "ItemIdentifierType", itemIdentifierType));
		}
		itemId.appendChild(value(
			document,
			"ItemIdentifierValue",
			itemIdentifierValue));

		return itemId;
	}

	private static Element itemOptionalFields(
		Document document,
		NcipBibliographicDescription description) {

		final var itemOptionalFields = element(document, "ItemOptionalFields");
		itemOptionalFields.appendChild(bibliographicDescription(document, description));
		return itemOptionalFields;
	}

	private static Element bibliographicDescription(
		Document document,
		NcipBibliographicDescription description) {

		final var bibliographicDescription = element(document, "BibliographicDescription");
		if (hasText(description.author())) {
			bibliographicDescription.appendChild(value(document, "Author", description.author()));
		}
		if (hasText(description.itemIdentifierValue())) {
			final var bibliographicItemId = element(document, "BibliographicItemId");
			bibliographicItemId.appendChild(value(
				document,
				"BibliographicItemIdentifier",
				description.itemIdentifierValue()));
			bibliographicItemId.appendChild(schemeValue(
				document,
				"BibliographicItemIdentifierCode",
				"barcode"));
			bibliographicDescription.appendChild(bibliographicItemId);
		}
		if (hasText(description.bibliographicRecordIdentifier())) {
			bibliographicDescription.appendChild(bibliographicRecordId(
				document,
				description.bibliographicRecordIdentifier(),
				description.bibliographicRecordAgencyId()));
		}
		if (hasText(description.edition())) {
			bibliographicDescription.appendChild(value(document, "Edition", description.edition()));
		}
		if (hasText(description.title())) {
			bibliographicDescription.appendChild(value(document, "Title", description.title()));
		}
		return bibliographicDescription;
	}

	private static Element shippingInformation(
		Document document,
		RequestShippingContext context,
		boolean includeExtension) {

		final var shippingInformation = element(document, "ShippingInformation");
		shippingInformation.appendChild(value(
			document, "ShippingInstructions", context.shippingInstructions()));

		final var physicalAddress = element(document, "PhysicalAddress");
		final var unstructuredAddress = element(document, "UnstructuredAddress");
		unstructuredAddress.appendChild(schemeValue(
			document, "UnstructuredAddressType", "Pickup Location"));
		unstructuredAddress.appendChild(value(
			document, "UnstructuredAddressData", context.unstructuredAddress()));
		physicalAddress.appendChild(unstructuredAddress);
		physicalAddress.appendChild(schemeValue(document, "PhysicalAddressType", "Ship To"));
		shippingInformation.appendChild(physicalAddress);

		if (includeExtension) {
			shippingInformation.appendChild(shippingExtension(document, context));
		}
		return shippingInformation;
	}

	private static Element returnShippingInformation(Document document) {
		final var shippingInformation = element(document, "ShippingInformation");
		final var electronicAddress = element(document, "ElectronicAddress");
		electronicAddress.appendChild(schemeValue(
			document, "ElectronicAddressType", "Email"));
		electronicAddress.appendChild(value(
			document,
			"ElectronicAddressData",
			"return-shipment@openrs.local"));
		shippingInformation.appendChild(electronicAddress);
		return shippingInformation;
	}

	private static Element shippingExtension(Document document, RequestShippingContext context) {
		final var ext = element(document, "Ext");
		final var shippingContext = extensionElement(document, "ShippingContext");
		shippingContext.appendChild(extensionValue(document, "SchemaVersion", context.schemaVersion()));
		shippingContext.appendChild(extensionValue(document, "WorkflowCode", context.workflowCode()));
		shippingContext.appendChild(extensionValue(document, "RouteMode", context.routeMode()));
		shippingContext.appendChild(patron(document, context.patron()));
		shippingContext.appendChild(endpoint(document, "BorrowingLibrary", context.borrowingLibrary()));
		shippingContext.appendChild(endpoint(document, "Supplier", context.supplier()));
		shippingContext.appendChild(pickupDestination(document, context.pickupDestination()));
		shippingContext.appendChild(provenance(document, context.provenance()));
		ext.appendChild(shippingContext);
		return ext;
	}

	private static Element patron(Document document, RequestShippingContext.Patron patron) {
		final var element = extensionElement(document, "Patron");
		appendExtensionValue(document, element, "Barcode", patron.barcode());
		appendExtensionValue(document, element, "SystemCode", patron.systemCode());
		appendExtensionValue(document, element, "AgencyCode", patron.agencyCode());
		return element;
	}

	private static Element endpoint(
		Document document, String name, RequestShippingContext.Endpoint endpoint) {

		final var element = extensionElement(document, name);
		appendExtensionValue(document, element, "SystemCode", endpoint.systemCode());
		appendExtensionValue(document, element, "AgencyCode", endpoint.agencyCode());
		appendExtensionValue(document, element, "AgencyName", endpoint.agencyName());
		return element;
	}

	private static Element pickupDestination(
		Document document, RequestShippingContext.PickupDestination destination) {

		final var element = extensionElement(document, "PickupDestination");
		appendExtensionValue(document, element, "Kind", destination.kind());
		element.appendChild(endpoint(document, "Owner", destination.owner()));
		appendExtensionValue(document, element, "DcbLocationId", destination.dcbLocationId());
		appendExtensionValue(document, element, "DcbLocationCode", destination.dcbLocationCode());
		appendExtensionValue(document, element, "LocalLocationCode", destination.localLocationCode());
		appendExtensionValue(document, element, "DisplayName", destination.displayName());
		if (destination.address() != null) {
			final var address = extensionElement(document, "Address");
			appendExtensionValue(document, address, "Formatted", destination.address().formatted());
			appendExtensionValue(document, address, "Scope", destination.address().scope());
			appendExtensionValue(document, address, "Source", destination.address().source());
			element.appendChild(address);
		}
		return element;
	}

	private static Element provenance(
		Document document, RequestShippingContext.Provenance provenance) {

		final var element = extensionElement(document, "Provenance");
		appendExtensionValue(document, element, "Source", provenance.source());
		appendExtensionValue(document, element, "SelectedPickupValue", provenance.selectedPickupValue());
		appendExtensionValue(document, element, "SelectedPickupCodeContext", provenance.selectedPickupCodeContext());
		appendExtensionValue(document, element, "SelectedPickupContext", provenance.selectedPickupContext());
		appendExtensionValue(document, element, "RequestCreatedAt", provenance.requestCreatedAt());
		appendExtensionValue(document, element, "LocationLastImportedAt", provenance.locationLastImportedAt());
		return element;
	}

	private static void appendExtensionValue(
		Document document, Element parent, String name, Object value) {

		if (value != null && !value.toString().isBlank()) {
			parent.appendChild(extensionValue(document, name, value));
		}
	}

	private static Element extensionValue(Document document, String name, Object value) {
		final var element = extensionElement(document, name);
		element.setTextContent(value.toString());
		return element;
	}

	private static Element extensionElement(Document document, String name) {
		return document.createElementNS(OPENRS_SHIPPING_NAMESPACE, "dcb:" + name);
	}

	private static Element requestId(
		Document document,
		String requestIdentifierValue) {

		final var requestId = element(document, "RequestId");
		requestId.appendChild(value(
			document,
			"RequestIdentifierValue",
			requestIdentifierValue));

		return requestId;
	}

	private static Element bibliographicId(
		Document document,
		String bibliographicRecordIdentifier,
		String bibliographicRecordAgencyId) {

		final var bibliographicId = element(document, "BibliographicId");
		final var bibliographicRecordId = element(document, "BibliographicRecordId");

		bibliographicRecordId.appendChild(value(
			document,
			"BibliographicRecordIdentifier",
			bibliographicRecordIdentifier));
		bibliographicRecordId.appendChild(value(
			document,
			"AgencyId",
			bibliographicRecordAgencyId));
		bibliographicId.appendChild(bibliographicRecordId);

		return bibliographicId;
	}

	private static Element bibliographicRecordId(
		Document document,
		String bibliographicRecordIdentifier,
		String bibliographicRecordAgencyId) {

		final var bibliographicRecordId = element(document, "BibliographicRecordId");
		bibliographicRecordId.appendChild(value(
			document,
			"BibliographicRecordIdentifier",
			bibliographicRecordIdentifier));
		bibliographicRecordId.appendChild(value(
			document,
			"AgencyId",
			bibliographicRecordAgencyId));
		return bibliographicRecordId;
	}

	private static Element schemeValue(Document document, String name, String value) {
		return value(document, name, value);
	}

	private static Element value(Document document, String name, String value) {
		final var element = element(document, name);
		element.setTextContent(value);

		return element;
	}

	private static Element element(Document document, String name) {
		return document.createElementNS(NCIP_NAMESPACE, name);
	}

	private static Document newDocument() {
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);

		try {
			return factory.newDocumentBuilder().newDocument();
		}
		catch (ParserConfigurationException e) {
			throw new IllegalStateException("Could not create NCIP XML document", e);
		}
	}

	private static String toXml(Document document) {
		final var transformerFactory = TransformerFactory.newInstance();

		try {
			final var transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

			final var writer = new StringWriter();
			transformer.transform(new DOMSource(document), new StreamResult(writer));

			return writer.toString();
		}
		catch (TransformerException e) {
			throw new IllegalStateException("Could not serialise NCIP XML", e);
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
