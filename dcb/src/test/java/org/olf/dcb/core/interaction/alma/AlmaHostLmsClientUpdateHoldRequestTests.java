package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micronaut.http.client.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.LocalRequest;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.test.PublisherUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import io.micronaut.core.convert.ConversionService;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientUpdateHoldRequestTests {

	private HostLms hostLms;
	private HttpClient httpClient;
	private AlmaApiClient almaApi;
	private AlmaClientFactory clientFactory;
	private ReferenceValueMappingService refMapSvc;
	private MaterialTypeToItemTypeMappingService materialTypeSvc;
	private LocationToAgencyMappingService locationToAgencySvc;
	private ConversionService conversionService;
	private LocationService locationService;
	private HostLmsService hostLmsService;
	private ConsortiumService consortiumService;

	private AlmaHostLmsClient sut;

	@BeforeEach
	void setUp() {
		hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");

		httpClient = mock(HttpClient.class);
		almaApi = mock(AlmaApiClient.class);
		clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		refMapSvc = mock(ReferenceValueMappingService.class);
		materialTypeSvc = mock(MaterialTypeToItemTypeMappingService.class);
		locationToAgencySvc = mock(LocationToAgencyMappingService.class);
		conversionService = mock(ConversionService.class);
		locationService = mock(LocationService.class);
		hostLmsService = mock(HostLmsService.class);
		consortiumService = mock(ConsortiumService.class);

		sut = new AlmaHostLmsClient(
				hostLms,
				httpClient,
				clientFactory,
				refMapSvc,
				materialTypeSvc,
				locationToAgencySvc,
				conversionService,
				locationService,
				hostLmsService,
			consortiumService
		);
	}

	@Test
	void updatesBarcodeAndTypeAndReturnsSameRequest() {
		// Arrange
		LocalRequest req = mock(LocalRequest.class);
		when(req.getBibId()).thenReturn("mms1");
		when(req.getHoldingId()).thenReturn("hold1");
		when(req.getRequestedItemId()).thenReturn("pid1");
		when(req.getRequestedItemBarcode()).thenReturn("NEWBC");
		when(req.getCanonicalItemType()).thenReturn("BOOK");

		// mapping DCB "BOOK" -> local "MT999"
		ReferenceValueMapping mapping = mock(ReferenceValueMapping.class);
		when(mapping.getToValue()).thenReturn("MT999");
		when(refMapSvc.findMapping(eq("ItemType"), eq("DCB"), eq("BOOK"), eq("ItemType"), eq("ALMA")))
				.thenReturn(Mono.just(mapping));

		AlmaItem existing = AlmaItem.builder()
				.itemData(
						AlmaItemData.builder()
								.barcode("OLDBC")
								.physicalMaterialType(CodeValuePair.builder().value("OLDTYPE").build())
								.build()
				).build();

		when(almaApi.retrieveItem("mms1", "hold1", "pid1")).thenReturn(Mono.just(existing));

		@SuppressWarnings("unchecked")
		var updatedCaptor = org.mockito.ArgumentCaptor.forClass(AlmaItem.class);
		when(almaApi.updateItem(eq("mms1"), eq("hold1"), eq("pid1"), updatedCaptor.capture()))
				.thenAnswer(inv -> Mono.just(inv.getArgument(3)));

		// Act
		Mono<LocalRequest> mono = sut.updateHoldRequest(req);
		verifyNoInteractions(almaApi); // nothing ran before subscribe

		LocalRequest out = PublisherUtils.singleValueFrom(mono);

		// Assert
		assertThat(out, sameInstance(req));
		AlmaItem sent = updatedCaptor.getValue();
		assertNotNull(sent);
		assertNotNull(sent.getItemData());
		assertEquals("NEWBC", sent.getItemData().getBarcode());
		assertEquals("MT999", sent.getItemData().getPhysicalMaterialType().getValue());

		verify(almaApi, times(1)).retrieveItem("mms1", "hold1", "pid1");
		verify(almaApi, times(1)).updateItem(eq("mms1"), eq("hold1"), eq("pid1"), any(AlmaItem.class));
		verifyNoMoreInteractions(almaApi);
	}

	@Test
	void errorsWhenRequiredFieldMissing_itemBarcode() {
		// Arrange
		LocalRequest req = mock(LocalRequest.class);
		when(req.getBibId()).thenReturn("mms1");
		when(req.getHoldingId()).thenReturn("hold1");
		when(req.getRequestedItemId()).thenReturn("pid1");
		when(req.getRequestedItemBarcode()).thenReturn(null); // missing
		when(req.getCanonicalItemType()).thenReturn("BOOK");

		// Act + Assert
		StepVerifier.create(sut.updateHoldRequest(req))
				.expectErrorSatisfies(ex -> {
					assertTrue(ex instanceof IllegalArgumentException);
					assertTrue(ex.getMessage().contains("Item Barcode is required"));
				})
				.verify();

		verifyNoInteractions(almaApi);
	}

	@Test
	void propagatesUpdateError() {
		// Arrange
		LocalRequest req = mock(LocalRequest.class);
		when(req.getBibId()).thenReturn("mms1");
		when(req.getHoldingId()).thenReturn("hold1");
		when(req.getRequestedItemId()).thenReturn("pid1");
		when(req.getRequestedItemBarcode()).thenReturn("NEWBC");
		when(req.getCanonicalItemType()).thenReturn("BOOK");

		ReferenceValueMapping mapping = mock(ReferenceValueMapping.class);
		when(mapping.getToValue()).thenReturn("MT999");
		when(refMapSvc.findMapping(anyString(), anyString(), anyString(), anyString(), anyString()))
				.thenReturn(Mono.just(mapping));

		AlmaItem existing = AlmaItem.builder()
				.itemData(AlmaItemData.builder().barcode("OLDBC").build())
				.build();

		when(almaApi.retrieveItem("mms1", "hold1", "pid1")).thenReturn(Mono.just(existing));
		when(almaApi.updateItem(eq("mms1"), eq("hold1"), eq("pid1"), any(AlmaItem.class)))
				.thenReturn(Mono.error(new RuntimeException("boom")));

		// Act + Assert
		StepVerifier.create(sut.updateHoldRequest(req))
				.expectErrorMessage("boom")
				.verify();

		verify(almaApi, times(1)).retrieveItem("mms1", "hold1", "pid1");
		verify(almaApi, times(1)).updateItem(eq("mms1"), eq("hold1"), eq("pid1"), any(AlmaItem.class));
	}

	@Test
	void propagatesRetrieveErrorAndSkipsUpdate() {
		// Arrange
		LocalRequest req = mock(LocalRequest.class);
		when(req.getBibId()).thenReturn("mms1");
		when(req.getHoldingId()).thenReturn("hold1");
		when(req.getRequestedItemId()).thenReturn("pid1");
		when(req.getRequestedItemBarcode()).thenReturn("NEWBC");
		when(req.getCanonicalItemType()).thenReturn("BOOK");

		// mapping can exist; retrieve fails first
		ReferenceValueMapping mapping = mock(ReferenceValueMapping.class);
		when(mapping.getToValue()).thenReturn("MT999");
		when(refMapSvc.findMapping(anyString(), anyString(), anyString(), anyString(), anyString()))
			.thenReturn(Mono.just(mapping));

		when(almaApi.retrieveItem("mms1", "hold1", "pid1"))
			.thenReturn(Mono.error(new RuntimeException("retrieve failed")));

		// Act + Assert
		StepVerifier.create(sut.updateHoldRequest(req))
			.expectErrorSatisfies(ex -> {
				assertTrue(ex instanceof RuntimeException);
				assertEquals("retrieve failed", ex.getMessage());
			})
			.verify();

		// Update must not be called
		verify(almaApi, times(1)).retrieveItem("mms1", "hold1", "pid1");
		verify(almaApi, never()).updateItem(anyString(), anyString(), anyString(), any(AlmaItem.class));
	}
}
