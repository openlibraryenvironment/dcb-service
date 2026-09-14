package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.test.PublisherUtils;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.AlmaLocation;
import services.k_int.interaction.alma.types.holdings.AlmaHolding;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientCreateItemTests {
	private AlmaApiClient almaApi;
	private AlmaHostLmsClient sut;

	@BeforeEach
	void setUp() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(Map.of(
			"virtual-item-library-code", "DCB-LIB",
			"virtual-item-location-code", "DCB-LOC"));

		almaApi = mock(AlmaApiClient.class);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		final var referenceValueMappingService = mock(ReferenceValueMappingService.class);
		when(referenceValueMappingService.findMapping("ItemType", "DCB", "BOOK", "ItemType", "ALMA"))
			.thenReturn(Mono.just(ReferenceValueMapping.builder().toValue("BK").build()));

		sut = new AlmaHostLmsClient(
			hostLms,
			mock(HttpClient.class),
			clientFactory,
			referenceValueMappingService,
			mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class),
			mock(ConversionService.class),
			mock(LocationService.class),
			mock(HostLmsService.class),
			mock(ConsortiumService.class));

		when(almaApi.retrieveLocation("DCB-LIB", "DCB-LOC"))
			.thenReturn(Mono.just(AlmaLocation.builder().code("DCB-LOC").build()));

		when(almaApi.createHoldingRecord(eq("bib-id"), any()))
			.thenReturn(Mono.just(AlmaHolding.builder().holdingId("holding-id").build()));
	}

	@Test
	void shouldReturnTheCreatedItemWithItsNewHolding() {
		when(almaApi.createItem(eq("bib-id"), eq("holding-id"), any()))
			.thenReturn(Mono.just(AlmaItem.builder()
				.itemData(AlmaItemData.builder().pid("item-id").barcode("BC1").build())
				.build()));

		final var item = PublisherUtils.singleValueFrom(sut.createItem(command()));

		assertThat(item.getLocalId(), is("item-id"));
		assertThat(item.getHoldingId(), is("holding-id"));
		assertThat(item.getBibId(), is("bib-id"));
	}

	@Test
	void shouldDeleteTheNewHoldingWhenTheItemCannotBeCreated() {
		when(almaApi.createItem(eq("bib-id"), eq("holding-id"), any()))
			.thenReturn(Mono.error(new RuntimeException("Alma rejected the item")));
		when(almaApi.deleteHoldingsRecord("bib-id", "holding-id"))
			.thenReturn(Mono.just("Holding deleted"));

		final var error = assertThrows(RuntimeException.class, () -> sut.createItem(command()).block());

		assertThat(error.getMessage(), is("Alma rejected the item"));
		verify(almaApi).deleteHoldingsRecord("bib-id", "holding-id");
	}

	@Test
	void shouldReportTheItemFailureWhenTheHoldingCannotBeDeletedEither() {
		when(almaApi.createItem(eq("bib-id"), eq("holding-id"), any()))
			.thenReturn(Mono.error(new RuntimeException("Alma rejected the item")));
		when(almaApi.deleteHoldingsRecord("bib-id", "holding-id"))
			.thenReturn(Mono.error(new RuntimeException("Alma refused the delete")));

		final var error = assertThrows(RuntimeException.class, () -> sut.createItem(command()).block());

		assertThat(error.getMessage(), is("Alma rejected the item"));
	}

	private static CreateItemCommand command() {
		return CreateItemCommand.builder()
			.bibId("bib-id")
			.barcode("BC1")
			.canonicalItemType("BOOK")
			.build();
	}
}
