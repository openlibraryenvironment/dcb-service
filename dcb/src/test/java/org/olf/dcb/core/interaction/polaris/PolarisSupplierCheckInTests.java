package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

/**
 * A lent Polaris copy checked in at a branch that has a hold queue for the title goes straight to that hold,
 * reading in transit or held rather than available. These are the readings the return leg completes on.
 */
@DcbTest
@TestInstance(PER_CLASS)
class PolarisSupplierCheckInTests {
	private static final String HOST_LMS_CODE = "polaris-supplier-check-in";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private HostLmsClient client;

	@BeforeAll
	void beforeAll() {
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, "key", "secret",
			"https://polaris-supplier-check-in.example.org", "TEST", "key", "secret");

		client = hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void copyRoutedToAHoldAfterTheLoanFilledDcbsHoldWasCheckedIn() {
		assertThat(client.showsCheckInAtSupplier(
			supplierRequest(HostLmsItem.ITEM_TRANSIT, HostLmsRequest.HOLD_MISSING)), is(true));
	}

	@Test
	void copyHeldForALocalHoldAfterTheLoanFilledDcbsHoldWasCheckedIn() {
		assertThat(client.showsCheckInAtSupplier(
			supplierRequest(HostLmsItem.ITEM_ON_HOLDSHELF, HostLmsRequest.HOLD_MISSING)), is(true));
	}

	@Test
	void copyInTransitWhileDcbsHoldRemainsWasNeverCheckedOut() {
		// The virtual checkout failed, so the copy is still on its outbound trip
		assertThat(client.showsCheckInAtSupplier(
			supplierRequest(HostLmsItem.ITEM_TRANSIT, HostLmsRequest.HOLD_CONFIRMED)), is(false));
	}

	@Test
	void copyStillOutToTheVirtualPatronIsNotBack() {
		assertThat(client.showsCheckInAtSupplier(
			supplierRequest(HostLmsItem.ITEM_LOANED, HostLmsRequest.HOLD_MISSING)), is(false));
	}

	@Test
	void copyBackOnTheShelfIsBackWhateverTheHoldSays() {
		assertThat(client.showsCheckInAtSupplier(
			supplierRequest(HostLmsItem.ITEM_AVAILABLE, HostLmsRequest.HOLD_CONFIRMED)), is(true));
	}

	@Test
	void copyItsLibraryWithdrewAfterTheLoanIsBack() {
		// Two MOBIUS requests sat in RETURN_TRANSIT for weeks on exactly this reading
		assertThat(client.showsCheckInAtSupplier(supplierRequest("Withdrawn", HostLmsRequest.HOLD_MISSING)), is(true));
	}

	@Test
	void copySentForRepairWhicheverWayPolarisSpellsItIsBack() {
		assertThat(client.showsCheckInAtSupplier(supplierRequest("In-Repair", HostLmsRequest.HOLD_MISSING)), is(true));
		assertThat(client.showsCheckInAtSupplier(supplierRequest("In Repair", HostLmsRequest.HOLD_MISSING)), is(true));
	}

	@Test
	void copyMadeLibraryUseOnlyIsBack() {
		assertThat(client.showsCheckInAtSupplier(
			supplierRequest(HostLmsItem.LIBRARY_USE_ONLY, HostLmsRequest.HOLD_MISSING)), is(true));
	}

	@Test
	void copyWithAStatusPolarisDoesNotDocumentIsNotBack() {
		assertThat(client.showsCheckInAtSupplier(supplierRequest("Mended", HostLmsRequest.HOLD_MISSING)), is(false));
	}

	@Test
	void copyWithNoReadingIsNotBack() {
		assertThat(client.showsCheckInAtSupplier(
			supplierRequest(null, HostLmsRequest.HOLD_MISSING)), is(false));
	}

	private static SupplierRequest supplierRequest(String itemStatus, String holdStatus) {
		return SupplierRequest.builder()
			.id(UUID.randomUUID())
			.hostLmsCode(HOST_LMS_CODE)
			.localItemStatus(itemStatus)
			.localStatus(holdStatus)
			.build();
	}
}
