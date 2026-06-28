package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import org.olf.dcb.core.interaction.AbstractHostLmsClient;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import reactor.core.publisher.Mono;

@Prototype
public class ORSApplianceHostLMS extends AbstractHostLmsClient {
	public static final String NCIP_ENDPOINT_URL_KEY
		= NcipHostLmsConfiguration.ENDPOINT_URL_KEY;

	private static final String REQUEST_TYPE = "Hold";
	private static final String REQUEST_SCOPE_TYPE = "Bibliographic Item";
	private static final String REQUESTED_ACTION_TYPE = "Accept For Loan";

	private final DeclarativeRequestTransport transport;
	private final NcipPayloadBuilder payloadBuilder;
	private final HttpClient httpClient;
	private final NcipHostLmsConfiguration hostLmsConfiguration;

	@Creator
	public ORSApplianceHostLMS(
		@Parameter HostLms hostLms,
		DeclarativeRequestTransport transport,
		NcipPayloadBuilder payloadBuilder,
		@Client("/") HttpClient httpClient) {

		super(hostLms);
		this.transport = transport;
		this.payloadBuilder = payloadBuilder;
		this.httpClient = httpClient;
		this.hostLmsConfiguration = new NcipHostLmsConfiguration();
	}

	@Override
	public List<HostLmsPropertyDefinition> getSettings() {
		return List.of(NcipHostLmsConfiguration.ENDPOINT_URL);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtSupplyingAgency(
		PlaceHoldRequestParameters parameters) {

		final var correlationId = correlationIdFor(
			parameters,
			LifecycleRole.SUPPLIER);
		final var payload = payloadBuilder.requestItem(new NcipRequestItemPayload(
			firstText(
				parameters.getLocalPatronBarcode(),
				parameters.getLocalPatronId(),
				parameters.getPatronRequestId()),
			firstText(
				parameters.getLocalBibId(),
				parameters.getSupplyingLocalBibId(),
				parameters.getPatronRequestId()),
			firstText(parameters.getSupplyingAgencyCode(), getHostLmsCode()),
			correlationId,
			REQUEST_TYPE,
			REQUEST_SCOPE_TYPE));

		return send(
			parameters,
			LifecycleRole.SUPPLIER,
			NcipProtocol.REQUEST_ITEM,
			correlationId,
			payload);
	}

	@Override
	public Mono<LocalRequest> placeHoldRequestAtBorrowingAgency(
		PlaceHoldRequestParameters parameters) {

		final var correlationId = correlationIdFor(
			parameters,
			LifecycleRole.BORROWER);
		final var payload = payloadBuilder.acceptItem(new NcipAcceptItemPayload(
			correlationId,
			REQUESTED_ACTION_TYPE,
			firstText(
				parameters.getLocalPatronBarcode(),
				parameters.getLocalPatronId(),
				parameters.getPatronRequestId()),
			firstText(
				parameters.getSupplyingLocalItemId(),
				parameters.getLocalItemId(),
				parameters.getSupplyingLocalItemBarcode(),
				parameters.getLocalItemBarcode())));

		return send(
			parameters,
			LifecycleRole.BORROWER,
			NcipProtocol.ACCEPT_ITEM,
			correlationId,
			payload);
	}

	@Override
	public Mono<Patron> patronAuth(
		String authProfile,
		String patronPrinciple,
		String secret) {

		final var payload = payloadBuilder.lookupUser(new NcipLookupUserPayload(
			getDefaultAgencyCode(),
			patronPrinciple,
			secret));

		return postLookupUser(payload)
			.map(ORSApplianceHostLMS::lookupUserResponseToPatron);
	}

	@Override
	public Mono<Patron> getPatronByUsername(String username) {
		final var payload = payloadBuilder.lookupUser(new NcipLookupUserPayload(
			getDefaultAgencyCode(),
			username,
			null));

		return postLookupUser(payload)
			.map(ORSApplianceHostLMS::lookupUserResponseToPatron);
	}

	private Mono<String> postLookupUser(String payload) {
		final var endpoint = hostLmsConfiguration.endpointUriFor(getHostLms());
		final var request = HttpRequest.POST(endpoint, payload)
			.contentType(MediaType.APPLICATION_XML_TYPE)
			.accept(MediaType.APPLICATION_XML_TYPE);

		return Mono.from(httpClient.exchange(request, Argument.of(String.class)))
			.map(response -> response.getBody()
				.orElseThrow(() -> new NcipProblemException(
					"NCIP LookupUserResponse body is empty")));
	}

	private Mono<LocalRequest> send(
		PlaceHoldRequestParameters parameters,
		LifecycleRole role,
		String messageKind,
		String correlationId,
		String payload) {

		return transport.send(new DeclarativeTransportRequest(
				NcipProtocol.PROTOCOL,
				role,
				LifecycleOperation.PLACE_REQUEST,
				getHostLmsCode(),
				parameters.getSupplyingAgencyCode(),
				correlationId,
				messageKind,
				payload))
			.map(ORSApplianceHostLMS::toLocalRequest);
	}

	private static LocalRequest toLocalRequest(
		DeclarativeTransportResponse response) {

		return LocalRequest.builder()
			.localId(response.remoteRequestId())
			.localStatus(response.status())
			.rawLocalStatus(response.rawStatus())
			.build();
	}

	private static String correlationIdFor(
		PlaceHoldRequestParameters parameters,
		LifecycleRole role) {

		return "%s:%s".formatted(
			firstText(parameters.getPatronRequestId()),
			role.name());
	}

	private static String firstText(String... values) {
		for (final var value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}

		throw new IllegalArgumentException("Expected at least one non-blank value");
	}

	private static Patron lookupUserResponseToPatron(String xml) {
		final var document = parse(xml);
		final var lookupUserResponse = Optional.ofNullable(
				document.getDocumentElement())
			.flatMap(element -> firstDescendant(element, NcipProtocol.LOOKUP_USER_RESPONSE))
			.orElseThrow(() -> new NcipProblemException(
				"NCIP response does not contain LookupUserResponse"));

		if (firstDescendant(lookupUserResponse, "Problem").isPresent()) {
			throw new NcipProblemException(
				"LookupUserResponse contains Problem: "
					+ optionalText(lookupUserResponse, "ProblemDetail")
						.orElse("No problem detail supplied"));
		}

		final var localId = requiredText(lookupUserResponse, "UserIdentifierValue");
		final var displayName = optionalText(lookupUserResponse, "UnstructuredPersonalUserName")
			.orElse(localId);
		final var homeLocationCode = firstDescendant(lookupUserResponse, "UserPrivilege")
			.flatMap(userPrivilege -> optionalText(userPrivilege, "AgencyId"))
			.or(() -> optionalText(lookupUserResponse, "AgencyId"))
			.orElse(null);

		return Patron.builder()
			.localId(List.of(localId))
			.localNames(List.of(displayName))
			.localBarcodes(List.of(localId))
			.uniqueIds(List.of(localId))
			.localHomeLibraryCode(homeLocationCode)
			.isActive(true)
			.build();
	}

	private static org.w3c.dom.Document parse(String xml) {
		try {
			final var factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			return factory.newDocumentBuilder()
				.parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		}
		catch (Exception e) {
			throw new NcipProblemException("Could not parse NCIP LookupUserResponse", e);
		}
	}

	private static Optional<org.w3c.dom.Element> firstDescendant(
		org.w3c.dom.Element element,
		String name) {

		final var nodes = element.getElementsByTagNameNS(
			NcipPayloadBuilder.NCIP_NAMESPACE,
			name);

		if (nodes.getLength() == 0) {
			return Optional.empty();
		}

		return Optional.of((org.w3c.dom.Element) nodes.item(0));
	}

	private static String requiredText(org.w3c.dom.Element element, String name) {
		return optionalText(element, name)
			.orElseThrow(() -> new NcipProblemException(
				"NCIP LookupUserResponse requires " + name));
	}

	private static Optional<String> optionalText(
		org.w3c.dom.Element element,
		String name) {

		return firstDescendant(element, name)
			.map(org.w3c.dom.Element::getTextContent)
			.map(String::trim)
			.filter(value -> !value.isBlank());
	}
}
