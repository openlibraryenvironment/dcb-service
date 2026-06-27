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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Singleton
public class NcipPayloadBuilder {
	public static final String NCIP_NAMESPACE = "http://www.niso.org/2008/ncip";
	public static final String NCIP_VERSION = "2.02";

	public String requestItem(NcipRequestItemPayload payload) {
		final var document = newDocument();
		final var requestItem = message(document, "RequestItem");

		requestItem.appendChild(userId(document, payload.userIdentifierValue()));
		requestItem.appendChild(bibliographicId(
			document,
			payload.bibliographicRecordIdentifier(),
			payload.bibliographicRecordAgencyId()));
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

		return toXml(document);
	}

	public String acceptItem(NcipAcceptItemPayload payload) {
		final var document = newDocument();
		final var acceptItem = message(document, "AcceptItem");

		acceptItem.appendChild(requestId(
			document,
			payload.requestIdentifierValue()));
		acceptItem.appendChild(value(
			document,
			"RequestedActionType",
			payload.requestedActionType()));

		if (hasText(payload.userIdentifierValue())) {
			acceptItem.appendChild(userId(document, payload.userIdentifierValue()));
		}

		if (hasText(payload.itemIdentifierValue())) {
			acceptItem.appendChild(itemId(document, payload.itemIdentifierValue()));
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

	private static Element userId(Document document, String userIdentifierValue) {
		final var userId = element(document, "UserId");
		userId.appendChild(value(
			document,
			"UserIdentifierValue",
			userIdentifierValue));

		return userId;
	}

	private static Element itemId(Document document, String itemIdentifierValue) {
		final var itemId = element(document, "ItemId");
		itemId.appendChild(value(
			document,
			"ItemIdentifierValue",
			itemIdentifierValue));

		return itemId;
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
