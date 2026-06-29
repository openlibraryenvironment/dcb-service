package org.olf.dcb.request.lifecycle.ncip;

import jakarta.inject.Singleton;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Singleton
public class NcipResponseBuilder {
	private static final String PROBLEM_TYPE = "Processing Error";

	public String itemShippedResponse() {
		final var document = newDocument();
		message(document, "ItemShippedResponse");

		return toXml(document);
	}

	public String itemRequestedResponse() {
		final var document = newDocument();
		message(document, NcipProtocol.ITEM_REQUESTED_RESPONSE);

		return toXml(document);
	}

	public String cancelRequestItemResponse() {
		final var document = newDocument();
		message(document, NcipProtocol.CANCEL_REQUEST_ITEM_RESPONSE);

		return toXml(document);
	}

	public String itemShippedProblem(String detail) {
		final var document = newDocument();
		final var response = message(document, "ItemShippedResponse");
		response.appendChild(problem(document, detail));

		return toXml(document);
	}

	public String itemRequestedProblem(String detail) {
		final var document = newDocument();
		final var response = message(document, NcipProtocol.ITEM_REQUESTED_RESPONSE);
		response.appendChild(problem(document, detail));

		return toXml(document);
	}

	public String cancelRequestItemProblem(String detail) {
		final var document = newDocument();
		final var response = message(document, NcipProtocol.CANCEL_REQUEST_ITEM_RESPONSE);
		response.appendChild(problem(document, detail));

		return toXml(document);
	}

	public String problem(String detail) {
		final var document = newDocument();
		final var message = rootMessage(document);
		message.appendChild(problem(document, detail));

		return toXml(document);
	}

	private static Element message(Document document, String messageName) {
		final var message = rootMessage(document);
		final var payload = element(document, messageName);
		message.appendChild(payload);

		return payload;
	}

	private static Element rootMessage(Document document) {
		final var message = element(document, "NCIPMessage");
		message.setAttributeNS(
			NcipPayloadBuilder.NCIP_NAMESPACE,
			"version",
			NcipPayloadBuilder.NCIP_VERSION);
		document.appendChild(message);

		return message;
	}

	private static Element problem(Document document, String detail) {
		final var problem = element(document, "Problem");
		problem.appendChild(value(document, "ProblemType", PROBLEM_TYPE));
		problem.appendChild(value(document, "ProblemDetail", detail));

		return problem;
	}

	private static Element value(Document document, String name, String value) {
		final var element = element(document, name);
		element.setTextContent(value);

		return element;
	}

	private static Element element(Document document, String name) {
		return document.createElementNS(NcipPayloadBuilder.NCIP_NAMESPACE, name);
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
}
