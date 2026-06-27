package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.Objects;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import reactor.core.publisher.Mono;

@Singleton
public class NcipDeclarativeRequestTransport
	implements DeclarativeRequestTransport {
	private final HostLmsService hostLmsService;
	private final HttpClient httpClient;
	private final NcipInboundXmlMapper inboundXmlMapper;
	private final NcipHostLmsConfiguration hostLmsConfiguration;

	public NcipDeclarativeRequestTransport(
		HostLmsService hostLmsService,
		@Client("/") HttpClient httpClient,
		NcipInboundXmlMapper inboundXmlMapper) {

		this(
			hostLmsService,
			httpClient,
			inboundXmlMapper,
			new NcipHostLmsConfiguration());
	}

	NcipDeclarativeRequestTransport(
		HostLmsService hostLmsService,
		HttpClient httpClient,
		NcipInboundXmlMapper inboundXmlMapper,
		NcipHostLmsConfiguration hostLmsConfiguration) {

		this.hostLmsService = hostLmsService;
		this.httpClient = httpClient;
		this.inboundXmlMapper = inboundXmlMapper;
		this.hostLmsConfiguration = hostLmsConfiguration;
	}

	@Override
	public Mono<DeclarativeTransportResponse> send(
		DeclarativeTransportRequest request) {

		if (!NcipProtocol.PROTOCOL.equals(request.protocol())) {
			return Mono.error(new IllegalArgumentException(
				"Unsupported declarative protocol: " + request.protocol()));
		}

		if (!hasText(request.hostLmsCode())) {
			return Mono.error(new IllegalArgumentException(
				"NCIP transport requires hostLmsCode"));
		}

		return hostLmsService.findByCode(request.hostLmsCode())
			.map(hostLmsConfiguration::endpointUriFor)
			.flatMap(endpoint -> post(endpoint, request))
			.map(responseXml -> toTransportResponse(request, responseXml));
	}

	private Mono<String> post(URI endpoint, DeclarativeTransportRequest request) {
		final var httpRequest = HttpRequest.POST(endpoint, request.payload())
			.contentType(MediaType.APPLICATION_XML_TYPE)
			.accept(MediaType.APPLICATION_XML_TYPE);

		return Mono.from(httpClient.exchange(
				httpRequest,
				Argument.of(String.class)))
			.map(response -> response.getBody()
				.orElseThrow(() -> new NcipProblemException(
					"NCIP " + request.messageKind() + " response body is empty")));
	}

	private DeclarativeTransportResponse toTransportResponse(
		DeclarativeTransportRequest request,
		String responseXml) {

		final var response = inboundXmlMapper.map(responseXml);
		final var expectedKind = expectedResponseKindFor(request);

		if (!expectedKind.equals(response.messageKind())) {
			throw new NcipProblemException(
				"Expected NCIP %s but received %s".formatted(
					expectedKind,
					response.messageKind()));
		}

		if (response.role() != request.role()) {
			throw new NcipProblemException(
				"NCIP response role does not match request role");
		}

		if (!Objects.equals(response.correlationId(), request.correlationId())) {
			throw new NcipProblemException(
				"NCIP response correlation id does not match request");
		}

		return new DeclarativeTransportResponse(
			response.hostRequestId(),
			response.status(),
			response.rawStatus(),
			response.rawMessageReference());
	}

	private static String expectedResponseKindFor(
		DeclarativeTransportRequest request) {

		return switch (request.messageKind()) {
			case NcipProtocol.REQUEST_ITEM -> NcipProtocol.REQUEST_ITEM_RESPONSE;
			case NcipProtocol.ACCEPT_ITEM -> NcipProtocol.ACCEPT_ITEM_RESPONSE;
			default -> throw new NcipProblemException(
				"Unsupported NCIP outbound message: " + request.messageKind());
		};
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
