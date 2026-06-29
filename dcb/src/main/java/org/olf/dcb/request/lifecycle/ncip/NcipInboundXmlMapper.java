package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Prototype;
import java.io.ByteArrayInputStream;
import java.time.Instant;
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

	public NcipInboundMessage map(String xml) {
		final var document = parse(xml);
		final var message = firstElementChild(document.getDocumentElement())
			.orElseThrow(() -> new NcipProblemException(
				"NCIPMessage does not contain a message payload"));

		return switch (message.getLocalName()) {
			case NcipProtocol.ITEM_SHIPPED -> itemShipped(message, xml);
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
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			requiredAgencyId(itemShipped, "FromAgencyId"),
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

		return new NcipInboundMessage(
			NcipProtocol.REQUEST_ITEM_RESPONSE,
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			requiredResponseAgencyId(response),
			requestId,
			requestId,
			problem.isPresent() ? MISSING_STATUS : CONFIRMED_STATUS,
			problemDetail
				.map(detail -> NcipProtocol.REQUEST_ITEM_RESPONSE + ":Problem:" + detail)
				.orElse(NcipProtocol.REQUEST_ITEM_RESPONSE),
			optionalText(response, "ItemIdentifierValue").orElse(null),
			null,
			null,
			rawMessageReference(NcipProtocol.REQUEST_ITEM_RESPONSE, xml));
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
			requiredResponseAgencyId(response),
			requestId,
			requestId,
			CONFIRMED_STATUS,
			NcipProtocol.ACCEPT_ITEM_RESPONSE,
			optionalText(response, "ItemIdentifierValue").orElse(null),
			null,
			null,
			rawMessageReference(NcipProtocol.ACCEPT_ITEM_RESPONSE, xml));
	}

	private static String requiredResponseAgencyId(Element response) {
		final var responseHeader = firstDescendant(response, "ResponseHeader")
			.orElseThrow(() -> new NcipProblemException(
				response.getLocalName() + " requires ResponseHeader"));

		return requiredAgencyId(responseHeader, "FromAgencyId");
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
