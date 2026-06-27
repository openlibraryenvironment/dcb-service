package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import reactor.core.publisher.Mono;

class NcipDeclarativeRequestTransportTests {
	@Test
	void postsRequestItemToConfiguredHostLmsEndpointAndMapsResponse() {
		final var hostLmsService = mock(HostLmsService.class);
		final var httpClient = mock(HttpClient.class);
		final var transport = transport(hostLmsService, httpClient);
		when(hostLmsService.findByCode("supplier-host"))
			.thenReturn(Mono.just(hostLms("supplier-host")));
		when(httpClient.exchange(any(HttpRequest.class), eq(Argument.of(String.class))))
			.thenReturn(Mono.just(HttpResponse.ok(
				NcipControllerTests.validRequestItemResponse())));

		final var response = singleValueFrom(transport.send(new DeclarativeTransportRequest(
			NcipProtocol.PROTOCOL,
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			"supplier-host",
			"supplier-agency",
			"request-1:SUPPLIER",
			NcipProtocol.REQUEST_ITEM,
			"<NCIPMessage><RequestItem/></NCIPMessage>")));

		final var requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
		verify(httpClient).exchange(
			requestCaptor.capture(),
			eq(Argument.of(String.class)));

		final var httpRequest = requestCaptor.getValue();
		assertThat(httpRequest.getUri(),
			is(URI.create("https://supplier.example.org/ncip/v2_02")));
		assertThat(httpRequest.getContentType().orElseThrow(),
			is(MediaType.APPLICATION_XML_TYPE));
		assertThat(httpRequest.getHeaders().accept(),
			contains(MediaType.APPLICATION_XML_TYPE));
		assertThat((String) httpRequest.getBody(String.class).orElseThrow(),
			containsString("<RequestItem"));
		assertThat(response.remoteRequestId(), is("request-1:SUPPLIER"));
		assertThat(response.status(), is("CONFIRMED"));
		assertThat(response.rawStatus(), is(NcipProtocol.REQUEST_ITEM_RESPONSE));
	}

	@Test
	void postsAcceptItemAndMapsBorrowerResponse() {
		final var hostLmsService = mock(HostLmsService.class);
		final var httpClient = mock(HttpClient.class);
		final var transport = transport(hostLmsService, httpClient);
		when(hostLmsService.findByCode("borrower-host"))
			.thenReturn(Mono.just(hostLms("borrower-host")));
		when(httpClient.exchange(any(HttpRequest.class), eq(Argument.of(String.class))))
			.thenReturn(Mono.just(HttpResponse.ok(
				NcipControllerTests.validAcceptItemResponse())));

		final var response = singleValueFrom(transport.send(new DeclarativeTransportRequest(
			NcipProtocol.PROTOCOL,
			LifecycleRole.BORROWER,
			LifecycleOperation.PLACE_REQUEST,
			"borrower-host",
			"borrower-agency",
			"request-1:BORROWER",
			NcipProtocol.ACCEPT_ITEM,
			"<NCIPMessage><AcceptItem/></NCIPMessage>")));

		assertThat(response.remoteRequestId(), is("request-1:BORROWER"));
		assertThat(response.status(), is("CONFIRMED"));
		assertThat(response.rawStatus(), is(NcipProtocol.ACCEPT_ITEM_RESPONSE));
	}

	@Test
	void rejectsHostLmsWithoutNcipEndpointConfiguration() {
		final var hostLmsService = mock(HostLmsService.class);
		final var transport = transport(hostLmsService, mock(HttpClient.class));
		final var hostLms = new DataHostLms();
		hostLms.setCode("supplier-host");
		hostLms.setClientConfig(Map.of());
		when(hostLmsService.findByCode("supplier-host"))
			.thenReturn(Mono.just(hostLms));

		final var error = assertThrows(IllegalArgumentException.class,
			() -> singleValueFrom(transport.send(new DeclarativeTransportRequest(
				NcipProtocol.PROTOCOL,
				LifecycleRole.SUPPLIER,
				LifecycleOperation.PLACE_REQUEST,
				"supplier-host",
				"supplier-agency",
				"request-1:SUPPLIER",
				NcipProtocol.REQUEST_ITEM,
				"<NCIPMessage><RequestItem/></NCIPMessage>"))));

		assertThat(error.getMessage(),
			is("Missing required configuration property: \"ncip-endpoint-url\""));
	}

	private static NcipDeclarativeRequestTransport transport(
		HostLmsService hostLmsService,
		HttpClient httpClient) {

		return new NcipDeclarativeRequestTransport(
			hostLmsService,
			httpClient,
			new NcipInboundXmlMapper(),
			new NcipHostLmsConfiguration());
	}

	private static DataHostLms hostLms(String code) {
		final var hostLms = new DataHostLms();
		hostLms.setCode(code);
		hostLms.setClientConfig(Map.of(
			NcipHostLmsConfiguration.ENDPOINT_URL_KEY,
			"https://%s.example.org/ncip/v2_02".formatted(code.split("-")[0])));

		return hostLms;
	}
}
