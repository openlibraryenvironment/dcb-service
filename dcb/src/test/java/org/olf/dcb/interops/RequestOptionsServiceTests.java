package org.olf.dcb.interops;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.RequestOptionsQuery;
import org.olf.dcb.core.interaction.RequestOptionsReport;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Finding what to ask about from DCB's own records, for a request whose supplier hold failed.
 * <p>
 * A refused hold leaves the supplier request without its virtual identity, which is exactly
 * the case this is for, so the identity has to be found some other way.
 */
@TestInstance(PER_CLASS)
class RequestOptionsServiceTests {
	private static final String SUPPLIER = "SUPPLIER";
	private static final UUID SUPPLIER_ID = UUID.randomUUID();
	private static final UUID REQUEST_ID = UUID.randomUUID();

	private PatronRequestRepository patronRequests;
	private SupplierRequestRepository supplierRequests;
	private PatronIdentityRepository patronIdentities;
	private HostLmsService hostLmsService;
	private HostLmsClient client;
	private RequestOptionsService service;

	@BeforeEach
	void beforeEach() {
		patronRequests = mock(PatronRequestRepository.class);
		supplierRequests = mock(SupplierRequestRepository.class);
		patronIdentities = mock(PatronIdentityRepository.class);
		hostLmsService = mock(HostLmsService.class);
		client = mock(HostLmsClient.class);

		when(hostLmsService.findByCode(SUPPLIER))
			.thenReturn(Mono.just(DataHostLms.builder().id(SUPPLIER_ID).code(SUPPLIER).build()));
		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.just(client));
		when(client.checkRequestOptions(any())).thenReturn(Mono.just(new RequestOptionsReport(
			SUPPLIER, RequestOptionsReport.Status.CHECKED, null, true, List.of("HOLD"), null)));

		service = new RequestOptionsService(patronRequests, supplierRequests, patronIdentities, hostLmsService);
	}

	@Test
	void shouldAskAboutTheVirtualPatronAndTheCopyTheSupplierWasAskedFor() {
		final var patronRequest = patronRequest();
		supplierRequest(patronRequest);

		when(patronIdentities.findAllByPatron(patronRequest.getPatron())).thenReturn(Flux.just(
			identity("home-patron", true, UUID.randomUUID()),
			identity("virtual-patron", false, SUPPLIER_ID)));

		final var report = service.forPatronRequest(REQUEST_ID).block();

		assertThat(report.status(), is(RequestOptionsReport.Status.CHECKED));

		final var query = ArgumentCaptor.forClass(RequestOptionsQuery.class);
		verify(client).checkRequestOptions(query.capture());

		assertThat(query.getValue(), is(new RequestOptionsQuery(
			"virtual-patron", "bib-1", null, "item-1", "barcode-1")));
	}

	@Test
	void shouldNotAskAboutThePatronsHomeIdentity() {
		final var patronRequest = patronRequest();
		supplierRequest(patronRequest);

		// Same system, but the patron's own account there - not what DCB placed the hold for
		when(patronIdentities.findAllByPatron(patronRequest.getPatron())).thenReturn(Flux.just(
			identity("home-patron", true, SUPPLIER_ID)));

		final var report = service.forPatronRequest(REQUEST_ID).block();

		assertThat(report.status(), is(RequestOptionsReport.Status.FAILED));
		assertThat(report.detail(), containsString("no virtual identity at SUPPLIER"));
		verify(client, never()).checkRequestOptions(any());
	}

	@Test
	void shouldSayWhenThereIsNoSupplierToAsk() {
		final var patronRequest = patronRequest();

		when(supplierRequests.findAllByPatronRequestAndIsActive(eq(patronRequest), eq(true)))
			.thenReturn(Flux.empty());

		final var report = service.forPatronRequest(REQUEST_ID).block();

		assertThat(report.status(), is(RequestOptionsReport.Status.FAILED));
		assertThat(report.detail(), containsString("no active supplier request"));
	}

	@Test
	void shouldSayWhenThereIsNoSuchRequest() {
		when(patronRequests.findById(REQUEST_ID)).thenReturn(Mono.empty());

		final var report = service.forPatronRequest(REQUEST_ID).block();

		assertThat(report.status(), is(RequestOptionsReport.Status.FAILED));
		assertThat(report.detail(), containsString("No patron request"));
	}

	private PatronRequest patronRequest() {
		final var patronRequest = PatronRequest.builder()
			.id(REQUEST_ID)
			.patron(Patron.builder().id(UUID.randomUUID()).build())
			.build();

		when(patronRequests.findById(REQUEST_ID)).thenReturn(Mono.just(patronRequest));

		return patronRequest;
	}

	private void supplierRequest(PatronRequest patronRequest) {
		when(supplierRequests.findAllByPatronRequestAndIsActive(eq(patronRequest), eq(true)))
			.thenReturn(Flux.just(SupplierRequest.builder()
				.id(UUID.randomUUID())
				.hostLmsCode(SUPPLIER)
				.localBibId("bib-1")
				.localItemId("item-1")
				.localItemBarcode("barcode-1")
				.build()));
	}

	private static PatronIdentity identity(String localId, boolean home, UUID hostLmsId) {
		return PatronIdentity.builder()
			.id(UUID.randomUUID())
			.localId(localId)
			.homeIdentity(home)
			.hostLms(DataHostLms.builder().id(hostLmsId).build())
			.build();
	}
}
