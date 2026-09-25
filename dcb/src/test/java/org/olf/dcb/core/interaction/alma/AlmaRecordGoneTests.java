package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.items.AlmaHoldingData;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;
import services.k_int.interaction.alma.types.error.AlmaError;
import services.k_int.interaction.alma.types.error.AlmaErrorList;
import services.k_int.interaction.alma.types.error.AlmaErrorResponse;

/**
 * What the adapter says about a request or an item Alma no longer holds.
 * <p>
 * A cancelled Alma request leaves the patron's active list, so asking for it fails. Reported
 * as an error, the cancellation transitions - which watch for MISSING or CANCELLED - could
 * never fire, and cleanup could not tell an item already removed from one it failed to delete.
 */
@TestInstance(PER_CLASS)
class AlmaRecordGoneTests {
	private AlmaApiClient almaApi;
	private AlmaHostLmsClient client;

	@BeforeEach
	void beforeEach() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(Map.of());

		almaApi = mock(AlmaApiClient.class);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		client = new AlmaHostLmsClient(hostLms, mock(HttpClient.class), clientFactory,
			mock(ReferenceValueMappingService.class), mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class), mock(ConversionService.class),
			mock(LocationService.class), mock(HostLmsService.class), mock(ConsortiumService.class));
	}

	@Test
	void shouldReportARequestAlmaNoLongerHasAsMissing() {
		when(almaApi.retrieveUserRequest("patron-1", "request-1"))
			.thenReturn(Mono.error(almaError(400, "401694", "Request Identifier not found.")));

		final var request = client.getRequest(HostLmsRequest.builder()
			.localId("request-1")
			.localPatronId("patron-1")
			.build()).block();

		// The value both cancellation transitions watch for
		assertThat(request.getStatus(), is(HostLmsRequest.HOLD_MISSING));
	}

	@Test
	void shouldStillRaiseWhenAlmaFailsForAnotherReason() {
		when(almaApi.retrieveUserRequest("patron-1", "request-1"))
			.thenReturn(Mono.error(almaError(400, "401652", "General Error")));

		final var request = client.getRequest(HostLmsRequest.builder()
			.localId("request-1")
			.localPatronId("patron-1")
			.build()).onErrorReturn(HostLmsRequest.builder().localId("raised").build()).block();

		assertThat("An unexplained failure must not read as a cancelled hold",
			request.getLocalId(), is("raised"));
	}

	@Test
	void shouldReportNoItemWhenAlmaNoLongerHasIt() {
		when(almaApi.retrieveItem("bib-1", "holding-1", "item-1"))
			.thenReturn(Mono.error(almaError(404, "401652", "Item not found")));

		final var item = client.getItem(HostLmsItem.builder()
			.localId("item-1")
			.bibId("bib-1")
			.holdingId("holding-1")
			.build()).block();

		// Empty, so cleanup audits "Skipped" rather than claiming a delete failed
		assertThat(item, is(nullValue()));
	}

	@Test
	void shouldReportAnUnknownHoldCountRatherThanNone() {
		when(almaApi.retrieveItem("bib-1", "holding-1", "item-1"))
			.thenReturn(Mono.just(AlmaItem.builder()
				.itemData(AlmaItemData.builder().pid("item-1").build())
				.build()));

		when(almaApi.retrieveItemRequests("bib-1", "holding-1", "item-1"))
			.thenReturn(Mono.error(new RuntimeException("Alma is unavailable")));

		final var item = client.getItem(HostLmsItem.builder()
			.localId("item-1")
			.bibId("bib-1")
			.holdingId("holding-1")
			.build()).block();

		assertThat("Zero would say there are no holds on an item nobody could read",
			item.getHoldCount(), is(nullValue()));
	}

	@Test
	void shouldReportAnUnknownHoldCountForAnItemFoundByBarcode() {
		when(almaApi.retrieveItemBarcodeOnly("barcode-1"))
			.thenReturn(Mono.just(AlmaItem.builder()
				.bibData(AlmaBib.builder().mmsId("bib-1").build())
				.holdingData(AlmaHoldingData.builder().holdingId("holding-1").build())
				.itemData(AlmaItemData.builder()
					.pid("item-1")
					.barcode("barcode-1")
					.baseStatus(CodeValuePair.builder().value("1").desc("Item in place").build())
					.build())
				.build()));

		when(almaApi.retrieveItemRequests("bib-1", "holding-1", "item-1"))
			.thenReturn(Mono.error(new RuntimeException("Alma is unavailable")));

		final var item = client.getItemByBarcode("barcode-1").block();

		assertThat(item.getHoldCount(), is(nullValue()));
	}

	private static AlmaApiException almaError(int statusCode, String code, String message) {
		final var error = new AlmaError();
		error.setErrorCode(code);
		error.setErrorMessage(message);

		final var errorList = new AlmaErrorList();
		errorList.setError(List.of(error));

		final var response = new AlmaErrorResponse();
		response.setErrorsExist(true);
		response.setErrorList(errorList);

		return new AlmaApiException("GET", "/almaws/v1/whatever", statusCode, response);
	}
}
