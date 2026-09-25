package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.items.AlmaHoldingData;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientPreventRenewalTests {
	private AlmaApiClient almaApi;
	private AlmaHostLmsClient sut;

	@BeforeEach
	void setUp() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(Map.of());

		almaApi = mock(AlmaApiClient.class);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		sut = new AlmaHostLmsClient(
			hostLms,
			clientFactory,
			mock(ReferenceValueMappingService.class),
			mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class),
			mock(ConsortiumService.class));
	}

	@Test
	void shouldDenyRenewalBySettingTheItemPolicy() {
		when(almaApi.retrieveItem("bib-1", "hol-1", "item-1"))
			.thenReturn(Mono.just(virtualItem("BOOK")), Mono.just(virtualItem("DCB_NO_RENEW")));

		when(almaApi.updateItem(anyString(), anyString(), anyString(), any()))
			.thenReturn(Mono.just(virtualItem("DCB_NO_RENEW")));

		sut.preventRenewalOnLoan(command()).block();

		final var sent = ArgumentCaptor.forClass(AlmaItem.class);
		verify(almaApi).updateItem(eq("bib-1"), eq("hol-1"), eq("item-1"), sent.capture());

		assertThat(sent.getValue().getItemData().getPolicy().getValue(), is("DCB_NO_RENEW"));
	}

	@Test
	void shouldRefuseToTouchAnItemDcbDidNotCreate() {
		when(almaApi.retrieveItem("bib-1", "hol-1", "item-1"))
			.thenReturn(Mono.just(itemWithCallNumber("823.91 SMI", "BOOK")));

		final var error = assertThrows(RuntimeException.class,
			() -> sut.preventRenewalOnLoan(command()).block());

		assertThat(error.getMessage(), containsString("does not carry the DCB_VIRTUAL_COLLECTION"));
		verify(almaApi, never()).updateItem(anyString(), anyString(), anyString(), any());
	}

	@Test
	void shouldFailWhenThePolicyDidNotTake() {
		// The item comes back unchanged, so the loan rule would never have refused the renewal
		when(almaApi.retrieveItem("bib-1", "hol-1", "item-1"))
			.thenReturn(Mono.just(virtualItem("BOOK")), Mono.just(virtualItem("BOOK")));

		when(almaApi.updateItem(anyString(), anyString(), anyString(), any()))
			.thenReturn(Mono.just(virtualItem("BOOK")));

		final var error = assertThrows(RuntimeException.class,
			() -> sut.preventRenewalOnLoan(command()).block());

		assertThat(error.getMessage(), containsString("still has policy 'BOOK'"));
	}

	@Test
	void shouldWarnStaffOnTheVirtualItemWhenThePolicyCannotBeSet() {
		when(almaApi.retrieveItem("bib-1", "hol-1", "item-1"))
			.thenReturn(Mono.just(virtualItem("BOOK")));

		// The expected failure: the library has not created the no-renew item policy
		when(almaApi.updateItem(anyString(), anyString(), anyString(), any()))
			.thenReturn(Mono.error(new AlmaHostLmsClientException("Invalid item policy")),
				Mono.just(virtualItem("BOOK")));

		final var error = assertThrows(RuntimeException.class,
			() -> sut.preventRenewalOnLoan(command()).block());

		// The workflow still has to see the original failure, or the request is never marked
		assertThat(error.getMessage(), containsString("Invalid item policy"));

		final var sent = ArgumentCaptor.forClass(AlmaItem.class);
		verify(almaApi, times(2)).updateItem(anyString(), anyString(), anyString(), sent.capture());

		assertThat(sent.getAllValues().get(1).getItemData().getFulfillmentNote(),
			containsString("please do not renew"));
	}

	@Test
	void shouldRefuseWithoutTheBibAndHoldingThatAddressTheItem() {
		final var error = assertThrows(RuntimeException.class,
			() -> sut.preventRenewalOnLoan(PreventRenewalCommand.builder()
				.requestId("request-id")
				.itemId("item-1")
				.build()).block());

		assertThat(error.getMessage(), containsString("bib, holding and item id"));
		verifyNoInteractions(almaApi);
	}

	private static PreventRenewalCommand command() {
		return PreventRenewalCommand.builder()
			.requestId("request-id")
			.itemId("item-1")
			.localBibId("bib-1")
			.localHoldingId("hol-1")
			.build();
	}

	private static AlmaItem virtualItem(String policy) {
		return itemWithCallNumber("DCB_VIRTUAL_COLLECTION", policy);
	}

	private static AlmaItem itemWithCallNumber(String callNumber, String policy) {
		return AlmaItem.builder()
			.holdingData(AlmaHoldingData.builder()
				.holdingId("hol-1")
				.callNumber(callNumber)
				.build())
			.itemData(AlmaItemData.builder()
				.pid("item-1")
				.policy(CodeValuePair.builder().value(policy).build())
				.build())
			.build();
	}
}
