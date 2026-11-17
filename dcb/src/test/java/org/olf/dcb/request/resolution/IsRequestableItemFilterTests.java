package org.olf.dcb.request.resolution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.DerivedLoanPolicy.LOCAL_ONLY;
import static org.olf.dcb.core.model.DerivedLoanPolicy.NO_LEND;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.item.availability.RequestableItemService;

import reactor.core.publisher.Mono;

class IsRequestableItemFilterTests {
	private final RequestableItemService requestableItemService = mock(RequestableItemService.class);
	private final IsRequestableItemFilter filter = new IsRequestableItemFilter(requestableItemService);

	@Test
	void aLocalOnlyItemIsKeptForABorrowerOnItsOwnSystem() {
		final var item = localOnlyItemOn("OWNER");
		when(requestableItemService.isRequestableWithinItsSystem(item)).thenReturn(Mono.just(true));

		assertThat(keeps(item, borrowingOn("OWNER")), is(true));
	}

	@Test
	void aLocalOnlyItemIsNotKeptForABorrowerOnAnotherSystem() {
		final var item = localOnlyItemOn("OWNER");

		assertThat(keeps(item, borrowingOn("ELSEWHERE")), is(false));
		verify(requestableItemService, never()).isRequestableWithinItsSystem(any());
	}

	@Test
	void anItemNeverLentIsNotKeptForItsOwnSystemEither() {
		final var item = Item.builder().agency(onSystem("OWNER")).derivedLoanPolicy(NO_LEND).isRequestable(false).build();

		assertThat(keeps(item, borrowingOn("OWNER")), is(false));
	}

	@Test
	void aRequestableItemIsKeptForAnyBorrower() {
		final var item = Item.builder().agency(onSystem("OWNER")).isRequestable(true).build();

		assertThat(keeps(item, borrowingOn("ELSEWHERE")), is(true));
	}

	private Boolean keeps(Item item, ItemFilterParameters parameters) {
		return Mono.from(filter.filterItem(parameters).apply(item)).block();
	}

	private static Item localOnlyItemOn(String hostLmsCode) {
		return Item.builder().agency(onSystem(hostLmsCode)).derivedLoanPolicy(LOCAL_ONLY).isRequestable(false).build();
	}

	private static ItemFilterParameters borrowingOn(String hostLmsCode) {
		return new ItemFilterParameters() {
			public List<String> excludedSupplyingAgencyCodes() { return List.of(); }
			public String borrowingAgencyCode() { return "borrowing-agency"; }
			public String borrowingHostLmsCode() { return hostLmsCode; }
			public String pickupAgencyCode() { return "pickup-agency"; }
			public Boolean isExpeditedCheckout() { return false; }
			public String requestedVolumeDesignation() { return null; }
		};
	}

	private static DataAgency onSystem(String hostLmsCode) {
		return DataAgency.builder().code("agency").hostLms(DataHostLms.builder().code(hostLmsCode).build()).build();
	}
}
