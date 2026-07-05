package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Prototype;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Prototype
public class NcipInboundXmlMapper {
	private static final String CONFIRMED_STATUS = "CONFIRMED";
	private static final String MISSING_STATUS = "MISSING";
	private static final String PLACED_STATUS = "PLACED";

	public NcipInboundMessage map(String xml) {
		final var document = parse(xml);
		final var message = firstElementChild(document.getDocumentElement())
			.orElseThrow(() -> new NcipProblemException(
				"NCIPMessage does not contain a message payload"));

		return switch (message.getLocalName()) {
			case NcipProtocol.ITEM_SHIPPED -> itemShipped(message, xml);
			case NcipProtocol.ITEM_RECEIVED -> itemReceived(message, xml);
			case NcipProtocol.ITEM_CHECKED_IN -> itemCheckedIn(message, xml);
			case NcipProtocol.ITEM_CHECKED_OUT -> itemCheckedOut(message, xml);
			case NcipProtocol.ITEM_REQUESTED -> itemRequested(message, xml);
			case NcipProtocol.CANCEL_REQUEST_ITEM -> cancelRequestItem(message, xml);
			case NcipProtocol.REQUEST_ITEM_RESPONSE -> requestItemResponse(
				message, xml);
			case NcipProtocol.ACCEPT_ITEM_RESPONSE -> acceptItemResponse(
				message, xml);
			default -> throw new NcipProblemException(
				"Unsupported NCIP message: " + message.getLocalName());
		};
	}

	private static NcipInboundMessage itemShipped(
		Element itemShipped,
		String xml) {

		final var requestId = requiredText(
			itemShipped, "RequestIdentifierValue");
		final var dateShipped = requiredText(itemShipped, "DateShipped");

		return new NcipInboundMessage(
			NcipProtocol.ITEM_SHIPPED,
			roleFor(requestId),
			LifecycleOperation.PLACE_REQUEST,
			requiredInitiatingPeerId(itemShipped),
			requestId,
			requestId,
			"SHIPPED",
			"ItemShipped",
			optionalText(itemShipped, "ItemIdentifierValue").orElse(null),
			null,
			Instant.parse(dateShipped),
			"ncip:ItemShipped:%s".formatted(Integer.toHexString(xml.hashCode())));
	}

	private static NcipInboundMessage requestItemResponse(
		Element response,
		String xml) {

		final var requestId = requiredText(
			response, "RequestIdentifierValue");
		final var problem = firstDescendant(response, "Problem");
		final var problemDetail = problem
			.flatMap(element -> optionalText(element, "ProblemDetail"));

		final var status = problem.isPresent()
			? MISSING_STATUS
			: requestItemResponseStatus(response);

		return new NcipInboundMessage(
			NcipProtocol.REQUEST_ITEM_RESPONSE,
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			requiredResponsePeerId(response),
			requestId,
			requestId,
			status,
			problemDetail
				.map(detail -> NcipProtocol.REQUEST_ITEM_RESPONSE + ":Problem:" + detail)
				.orElse(CONFIRMED_STATUS.equals(status)
					? NcipProtocol.REQUEST_ITEM_RESPONSE
					: NcipProtocol.REQUEST_ITEM_RESPONSE + ":" + status),
			optionalText(response, "ItemIdentifierValue").orElse(null),
			null,
			null,
			rawMessageReference(NcipProtocol.REQUEST_ITEM_RESPONSE, xml));
	}

	private static NcipInboundMessage itemReceived(Element itemReceived, String xml) {
		final var requestId = requiredText(itemReceived, "RequestIdentifierValue");
		final var dateReceived = requiredText(itemReceived, "DateReceived");
		final var orientation = initiationOrientation(itemReceived);

		return new NcipInboundMessage(
			NcipProtocol.ITEM_RECEIVED,
			roleFor(requestId),
			LifecycleOperation.PLACE_REQUEST,
			requiredInitiatingPeerId(itemReceived),
			requestId,
			requestId,
			"RECEIVED",
			NcipProtocol.ITEM_RECEIVED,
			requiredText(itemReceived, "ItemIdentifierValue"),
			requiredText(itemReceived, "ItemIdentifierValue"),
			Instant.parse(dateReceived),
			rawMessageReference(NcipProtocol.ITEM_RECEIVED, xml),
			orientation);
	}

	private static NcipInboundMessage itemCheckedIn(Element itemCheckedIn, String xml) {
		final var requestId = optionalTextAnyNamespace(itemCheckedIn, "RequestIdentifierValue")
			.orElseThrow(() -> new NcipProblemException(
				"ItemCheckedIn requires openrs RequestIdentifierValue"));
		final var itemId = requiredText(itemCheckedIn, "ItemIdentifierValue");
		final var orientation = initiationOrientation(itemCheckedIn);

		return new NcipInboundMessage(
			NcipProtocol.ITEM_CHECKED_IN,
			roleFor(requestId),
			LifecycleOperation.PLACE_REQUEST,
			requiredInitiatingPeerId(itemCheckedIn),
			requestId,
			requestId,
			"CHECKED_IN",
			NcipProtocol.ITEM_CHECKED_IN,
			itemId,
			itemId,
			null,
			rawMessageReference(NcipProtocol.ITEM_CHECKED_IN, xml),
			orientation);
	}

	private static NcipInboundMessage itemCheckedOut(Element itemCheckedOut, String xml) {
		final var requestId = requiredText(itemCheckedOut, "RequestIdentifierValue");
		final var itemId = requiredText(itemCheckedOut, "ItemIdentifierValue");
		final var orientation = initiationOrientation(itemCheckedOut);
		final var properties = optionalText(itemCheckedOut, "DateDue")
			.<Map<String, Object>>map(dateDue -> Map.of(
				"fromAgencyId", orientation.get("fromAgencyId"),
				"toAgencyId", orientation.get("toAgencyId"),
				"dateDue", dateDue))
			.orElse(orientation);

		return new NcipInboundMessage(
			NcipProtocol.ITEM_CHECKED_OUT,
			roleFor(requestId),
			LifecycleOperation.PLACE_REQUEST,
			requiredInitiatingPeerId(itemCheckedOut),
			requestId,
			requestId,
			"CHECKED_OUT",
			NcipProtocol.ITEM_CHECKED_OUT,
			itemId,
			itemId,
			null,
			rawMessageReference(NcipProtocol.ITEM_CHECKED_OUT, xml),
			properties);
	}

	private static NcipInboundMessage itemRequested(Element itemRequested, String xml) {
		final var requestId = requiredText(itemRequested, "RequestIdentifierValue");
		final var itemId = optionalText(itemRequested, "ItemIdentifierValue")
			.or(() -> optionalTextAnyNamespace(itemRequested, "SelectedItemBarcode"))
			.orElse(null);

		return new NcipInboundMessage(
			NcipProtocol.ITEM_REQUESTED,
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			requiredInitiatingPeerId(itemRequested),
			requestId,
			requestId,
			CONFIRMED_STATUS,
			NcipProtocol.ITEM_REQUESTED,
			itemId,
			itemId,
			null,
			rawMessageReference(NcipProtocol.ITEM_REQUESTED, xml));
	}

	private static NcipInboundMessage cancelRequestItem(
		Element cancelRequestItem,
		String xml) {

		final var requestId = requiredText(cancelRequestItem, "RequestIdentifierValue");
		final var itemId = optionalText(cancelRequestItem, "ItemIdentifierValue").orElse(null);
		final var reason = optionalTextAnyNamespace(cancelRequestItem, "ReasonCode")
			.or(() -> optionalTextAnyNamespace(cancelRequestItem, "ProblemDetail"))
			.or(() -> optionalTextAnyNamespace(cancelRequestItem, "ProcessingNote"))
			.orElse("NOT_SUPPLIED");

		return new NcipInboundMessage(
			NcipProtocol.CANCEL_REQUEST_ITEM,
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			requiredInitiatingPeerId(cancelRequestItem),
			requestId,
			requestId,
			MISSING_STATUS,
			NcipProtocol.CANCEL_REQUEST_ITEM + ":" + reason,
			itemId,
			itemId,
			null,
			rawMessageReference(NcipProtocol.CANCEL_REQUEST_ITEM, xml));
	}

	private static String requestItemResponseStatus(Element response) {
		// Fallback Host profiles use a minimal RequestItemResponse as ACK only.
		// Full NCIP hold-placement responses still carry UserId/RequestType and remain CONFIRMED.
		if (firstDescendant(response, "UserId").isEmpty()
			|| firstDescendant(response, "RequestType").isEmpty()) {
			return PLACED_STATUS;
		}
		return CONFIRMED_STATUS;
	}

	private static NcipInboundMessage acceptItemResponse(
		Element response,
		String xml) {

		rejectProblem(response, NcipProtocol.ACCEPT_ITEM_RESPONSE);

		final var requestId = requiredText(
			response, "RequestIdentifierValue");

		return new NcipInboundMessage(
			NcipProtocol.ACCEPT_ITEM_RESPONSE,
			LifecycleRole.BORROWER,
			LifecycleOperation.PLACE_REQUEST,
			requiredResponsePeerId(response),
			requestId,
			requestId,
			CONFIRMED_STATUS,
			NcipProtocol.ACCEPT_ITEM_RESPONSE,
			optionalText(response, "ItemIdentifierValue").orElse(null),
			null,
			null,
			rawMessageReference(NcipProtocol.ACCEPT_ITEM_RESPONSE, xml));
	}

	private static LifecycleRole roleFor(String requestId) {
		if (requestId != null && requestId.endsWith(":BORROWER")) {
			return LifecycleRole.BORROWER;
		}
		return LifecycleRole.SUPPLIER;
	}

	private static Map<String, Object> initiationOrientation(Element message) {
		final var initiationHeader = firstDescendant(message, "InitiationHeader");
		if (initiationHeader.isEmpty()) {
			return Map.of();
		}
		return Map.of(
			"fromAgencyId", requiredAgencyId(initiationHeader.get(), "FromAgencyId"),
			"toAgencyId", requiredAgencyId(initiationHeader.get(), "ToAgencyId"));
	}

	private static String requiredResponsePeerId(Element response) {
		final var responseHeader = firstDescendant(response, "ResponseHeader")
			.orElseThrow(() -> new NcipProblemException(
				response.getLocalName() + " requires ResponseHeader"));

		return optionalSystemId(responseHeader, "FromSystemId")
			.orElseGet(() -> requiredAgencyId(responseHeader, "FromAgencyId"));
	}

	private static String requiredInitiatingPeerId(Element message) {
		final var initiationHeader = firstDescendant(message, "InitiationHeader")
			.orElseThrow(() -> new NcipProblemException(
				message.getLocalName() + " requires InitiationHeader"));

		return optionalSystemId(initiationHeader, "FromSystemId")
			.orElseGet(() -> requiredAgencyId(initiationHeader, "FromAgencyId"));
	}

	private static void rejectProblem(Element response, String messageKind) {
		final var problem = firstDescendant(response, "Problem");

		if (problem.isPresent()) {
			throw new NcipProblemException("%s contains Problem: %s".formatted(
				messageKind,
				optionalText(problem.get(), "ProblemDetail")
					.orElse("No problem detail supplied")));
		}
	}

	private static String requiredAgencyId(Element element, String agencyElementName) {
		final var agencyElement = firstDescendant(element, agencyElementName)
			.orElseThrow(() -> new NcipProblemException(
				"NCIP message requires " + agencyElementName));

		return requiredText(agencyElement, "AgencyId");
	}

	private static Optional<String> optionalSystemId(Element element, String systemElementName) {
		return firstDescendant(element, systemElementName)
			.map(Element::getTextContent)
			.map(String::trim)
			.filter(value -> !value.isBlank());
	}

	private static String requiredText(Element element, String name) {
		return optionalText(element, name)
			.orElseThrow(() -> new NcipProblemException(
				"NCIP message requires " + name));
	}

	private static Optional<String> optionalText(Element element, String name) {
		return firstDescendant(element, name)
			.map(Element::getTextContent)
			.map(String::trim)
			.filter(value -> !value.isBlank());
	}

	private static Optional<String> optionalTextAnyNamespace(
		Element element,
		String name) {

		final var nodes = element.getElementsByTagNameNS("*", name);

		if (nodes.getLength() == 0) {
			return Optional.empty();
		}

		return Optional.of((Element) nodes.item(0))
			.map(Element::getTextContent)
			.map(String::trim)
			.filter(value -> !value.isBlank());
	}

	private static Optional<Element> firstDescendant(Element element, String name) {
		final var nodes = element.getElementsByTagNameNS(
			NcipPayloadBuilder.NCIP_NAMESPACE, name);

		if (nodes.getLength() == 0) {
			return Optional.empty();
		}

		return Optional.of((Element) nodes.item(0));
	}

	private static String rawMessageReference(String messageKind, String xml) {
		return "ncip:%s:%s".formatted(
			messageKind,
			Integer.toHexString(xml.hashCode()));
	}

	private static Optional<Element> firstElementChild(Element element) {
		var child = element.getFirstChild();

		while (child != null) {
			if (child instanceof Element childElement) {
				return Optional.of(childElement);
			}

			child = child.getNextSibling();
		}

		return Optional.empty();
	}

	private static Document parse(String xml) {
		try {
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			secure(factory);

			return factory.newDocumentBuilder().parse(
				new ByteArrayInputStream(xml.getBytes()));
		}
		catch (Exception e) {
			throw new NcipProblemException("Could not parse NCIP XML", e);
		}
	}

	private static void secure(DocumentBuilderFactory factory)
		throws ParserConfigurationException {

		factory.setFeature(
			"http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature(
			"http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature(
			"http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
	}
}
