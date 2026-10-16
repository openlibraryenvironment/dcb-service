package org.olf.dcb.request.resolution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.SupplierPreflight;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.request.fulfilment.SupplierPreflightService;

import lombok.Builder;
import reactor.core.publisher.Mono;

/** No database: which supplier answers exclude a copy. */
class ExcludeSupplierThatCannotSupplyItemFilterTests {
	private final SupplierPreflightService service = mock(SupplierPreflightService.class);
	private final ExcludeSupplierThatCannotSupplyItemFilter filter = new ExcludeSupplierThatCannotSupplyItemFilter(service);

	@Test
	void shouldExcludeACopyAtASupplierThatCannotSupply() {
		when(service.checkSupplier("SUPPLIER")).thenReturn(Mono.just(SupplierPreflight.cannotSupply("why", "fix")));

		assertThat(included(copyAt("SUPPLIER")), is(false));
	}

	@Test
	void shouldIncludeACopyAtASupplierThatWasOnlyUnreachable() {
		when(service.checkSupplier("SUPPLIER")).thenReturn(Mono.just(SupplierPreflight.unreachable("why")));

		assertThat(included(copyAt("SUPPLIER")), is(true));
	}

	@Test
	void shouldIncludeACopyWhenTheSupplierCannotBeChecked() {
		when(service.checkSupplier("SUPPLIER")).thenReturn(Mono.error(new RuntimeException("database unavailable")));

		assertThat(included(copyAt("SUPPLIER")), is(true));
	}

	@Test
	void shouldIncludeACopyWithNoSupplier() {
		assertThat(included(Item.builder().localId("copy").build()), is(true));
	}

	private boolean included(Item item) {
		return Mono.from(filter.filterItem(Parameters.builder().build()).apply(item)).block();
	}

	private static Item copyAt(String supplier) {
		return Item.builder()
			.localId("copy")
			.agency(DataAgency.builder().code("agency").hostLms(DataHostLms.builder().code(supplier).build()).build())
			.build();
	}

	@Builder
	record Parameters(List<String> excludedSupplyingAgencyCodes,
		String borrowingAgencyCode, String borrowingHostLmsCode, Boolean isExpeditedCheckout,
		String pickupAgencyCode, String requestedVolumeDesignation) implements ItemFilterParameters { }
}
