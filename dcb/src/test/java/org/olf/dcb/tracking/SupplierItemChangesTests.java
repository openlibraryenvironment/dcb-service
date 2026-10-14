package org.olf.dcb.tracking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.SupplierRequest;

class SupplierItemChangesTests {
	@Test
	void shouldNotTreatARenewableFlagTheHostLmsDoesNotReportAsAChange() {
		final var held = heldInTransit();

		assertThat(held.getLocalRenewable(), is(true));
		assertThat(SupplierItemChanges.hasChanged(held, inTransit().renewable(null).build()), is(false));
	}

	@Test
	void shouldNotTreatCountsTheHostLmsDoesNotReportAsAChange() {
		assertThat(SupplierItemChanges.hasChanged(heldInTransit(),
			inTransit().renewalCount(null).holdCount(null).build()), is(false));
	}

	@Test
	void shouldTreatAReportedRenewableFlagThatDiffersAsAChange() {
		assertThat(SupplierItemChanges.hasChanged(heldInTransit(), inTransit().renewable(false).build()), is(true));
	}

	@Test
	void shouldTreatAReportedCountThatDiffersAsAChange() {
		assertThat(SupplierItemChanges.hasChanged(heldInTransit(), inTransit().renewalCount(1).build()), is(true));
		assertThat(SupplierItemChanges.hasChanged(heldInTransit(), inTransit().holdCount(2).build()), is(true));
	}

	@Test
	void shouldTreatADifferentStatusAsAChange() {
		assertThat(SupplierItemChanges.hasChanged(heldInTransit(), inTransit().status("RECEIVED").build()), is(true));
	}

	@Test
	void shouldNotFailWhenNeitherSideHasAStatus() {
		final var held = heldInTransit();
		held.setLocalItemStatus(null);

		assertThat(SupplierItemChanges.hasChanged(held, inTransit().status(null).build()), is(false));
	}

	// The renewable flag is left at the entity's default, as a supplier request read back from the database has it
	private static SupplierRequest heldInTransit() {
		return SupplierRequest.builder()
			.localItemStatus("TRANSIT")
			.localRenewalCount(0)
			.localHoldCount(1)
			.build();
	}

	private static HostLmsItem.HostLmsItemBuilder inTransit() {
		return HostLmsItem.builder()
			.status("TRANSIT")
			.renewalCount(0)
			.holdCount(1);
	}
}
