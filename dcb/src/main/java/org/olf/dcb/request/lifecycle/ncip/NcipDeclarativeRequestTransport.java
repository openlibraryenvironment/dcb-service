package org.olf.dcb.request.lifecycle.ncip;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.olf.dcb.core.interaction.ncip.NcipProtocol;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.ncip.peerauth.NcipPeerAuthorizationService;
import reactor.core.publisher.Mono;

@Singleton
public class NcipDeclarativeRequestTransport
	implements DeclarativeRequestTransport {
	private final HostLmsService hostLmsService;
	private final HttpClient httpClient;
	private final NcipInboundXmlMapper inboundXmlMapper;
	private final NcipHostLmsConfiguration hostLmsConfiguration;
	private final NcipPeerAuthorizationService peerAuthorizationService;

	public NcipDeclarativeRequestTransport(
		HostLmsService hostLmsService,
		@Client("/") HttpClient httpClient,
		NcipInboundXmlMapper inboundXmlMapper,
		NcipPeerAuthorizationService peerAuthorizationService) {

		this(
			hostLmsService,
			httpClient,
			inboundXmlMapper,
			new NcipHostLmsConfiguration(),
			peerAuthorizationService);
	}

	NcipDeclarativeRequestTransport(
		HostLmsService hostLmsService,
		HttpClient httpClient,
		NcipInboundXmlMapper inboundXmlMapper,
		NcipHostLmsConfiguration hostLmsConfiguration,
		NcipPeerAuthorizationService peerAuthorizationService) {

		this.hostLmsService = hostLmsService;
		this.httpClient = httpClient;
		this.inboundXmlMapper = inboundXmlMapper;
		this.hostLmsConfiguration = hostLmsConfiguration;
		this.peerAuthorizationService = peerAuthorizationService;
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
			.flatMap(hostLms -> post(hostLms, request))
			.map(responseXml -> toTransportResponse(request, responseXml));
	}

	private Mono<String> post(HostLms hostLms, DeclarativeTransportRequest request) {
		final var endpoint = hostLmsConfiguration.endpointUriFor(hostLms);
		final var httpRequest = peerAuthorizationService.authorize(
			HttpRequest.POST(endpoint, request.payload())
			.contentType(MediaType.APPLICATION_XML_TYPE)
			.accept(MediaType.APPLICATION_XML_TYPE),
			hostLms);

		return Mono.from(httpClient.exchange(
				httpRequest,
				Argument.of(String.class)))
			.map(response -> response.getBody()
				.orElseThrow(() -> new NcipProblemException(
					"NCIP " + request.messageKind() + " response body is empty")))
			.onErrorMap(HttpClientResponseException.class, error -> new NcipProblemException(
				"NCIP %s rejected by %s: %s".formatted(
					request.messageKind(), request.hostLmsCode(), responseDetail(error)),
				error));
	}

	private static String responseDetail(HttpClientResponseException error) {
		Optional<?> body = error.getResponse().getBody(Argument.of(Map.class));
		if (body.isPresent() && body.get() instanceof Map<?, ?> values) {
			Object detail = values.get("detail");
			if (detail != null && !detail.toString().isBlank()) {
				return detail.toString();
			}
		}
		return error.getResponse().getBody(String.class)
			.filter(value -> !value.isBlank())
			.orElse(error.getMessage());
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
			case NcipProtocol.ITEM_SHIPPED -> NcipProtocol.ITEM_SHIPPED_RESPONSE;
			default -> throw new NcipProblemException(
				"Unsupported NCIP outbound message: " + request.messageKind());
		};
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
