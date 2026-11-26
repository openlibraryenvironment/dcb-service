package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.core.interaction.polaris.Direction.POLARIS_TO_HOST_LMS;
import static org.olf.dcb.core.interaction.polaris.PolarisItem.mapItemStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.core.interaction.HostLmsItem;

class PolarisItemStatusTests {
	@ParameterizedTest
	@ValueSource(strings = { "Withdrawn", "In-Repair", "In Repair", "Damaged", "Unavailable", "Bindery",
		"In Process", "On-Order", "Routed", "Mended" })
	void aStatusDcbHasNoStateForIsKeptAsPolarisSpellsIt(String status) {
		assertThat(mapItemStatus(POLARIS_TO_HOST_LMS, status), is(status));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Claim Returned", "Claim Never Had", "Claim Missing Parts" })
	void aClaimIsALoanNobodyHasResolved(String status) {
		assertThat(mapItemStatus(POLARIS_TO_HOST_LMS, status), is(HostLmsItem.ITEM_LOANED));
	}

	@Test
	void aNonCirculatingCopyIsLibraryUseOnly() {
		assertThat(mapItemStatus(POLARIS_TO_HOST_LMS, "Non-Circulating"), is(HostLmsItem.LIBRARY_USE_ONLY));
	}

	@Test
	void aLostCopyIsStillMissing() {
		assertThat(mapItemStatus(POLARIS_TO_HOST_LMS, "Lost"), is(HostLmsItem.ITEM_MISSING));
	}
}
