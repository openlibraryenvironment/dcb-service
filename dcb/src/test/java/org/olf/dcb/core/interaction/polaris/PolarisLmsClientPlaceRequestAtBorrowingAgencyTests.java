package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasLocalStatus;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRequestedItemBarcode;
import static org.olf.dcb.test.matchers.LocalRequestMatchers.hasRequestedItemId;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.BibliographicRecord;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.ItemRecordFull;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.LibraryHold;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.RequestExtensionData;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.SysHoldRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PolarisLmsClientPlaceRequestAtBorrowingAgencyTests {
	private static final String HOST_LMS_CODE = "polaris-cataloguing";
	private static final int ILL_LOCATION_ID = 50;

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockServerClient mockServerClient;
	private MockPolarisFixture mockPolarisFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		final var HOST = "polaris-place-borrowing-tests.com";
		final String BASE_URL = "https://" + HOST;
		final String KEY = "test-key";
		final String SECRET = "test-secret";
		final String DOMAIN = "TEST";

		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, KEY, SECRET, BASE_URL,
			DOMAIN, KEY, SECRET, "default-agency-code", ILL_LOCATION_ID);

		mockPolarisFixture = new MockPolarisFixture(HOST,
			mockServerClient, testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();

		mockPolarisFixture.mockPapiStaffAuthentication();
		mockPolarisFixture.mockAppServicesStaffAuthentication();

		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldBeAbleToPlaceRequestAtBorrowingAndPickupAgency() {
		// Arrange
		final Integer itemId = 453545;
		final Integer bibId = 563645;

		mockPolarisFixture.mockGetItem(itemId,
			ItemRecordFull.builder()
				.itemRecordID(itemId)
				.bibInfo(ApplicationServicesClient.BibInfo.builder()
					.bibliographicRecordID(bibId)
					.build())
				.build());

		mockPolarisFixture.mockGetBib(bibId,
			BibliographicRecord.builder()
				.bibliographicRecordID(bibId)
				.build());

		mockPolarisFixture.mockPlaceHold();

		final Integer holdId = 6747554;
		final var matchingNote = "DCB Testing PACDisplayNotes";

		final Integer patronId = 573734;

		mockPolarisFixture.mockListPatronLocalHolds(patronId,
			SysHoldRequest.builder()
				.sysHoldRequestID(holdId)
				.bibliographicRecordID(bibId)
				.pacDisplayNotes(matchingNote)
				.build());

		final var itemBarcode = "785574212";
		final var localHoldStatus = "In Processing";

		mockPolarisFixture.mockGetHold(holdId.toString(),
			LibraryHold.builder()
				.sysHoldStatus(localHoldStatus)
				.itemRecordID(itemId)
				.itemBarcode(itemBarcode)
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final Integer pickupBranchId = 3721593;

		final var localRequest = singleValueFrom(client.placeHoldRequestAtBorrowingAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId(patronId.toString())
				.localBibId(bibId.toString())
				.localItemId(itemId.toString())
				.pickupLocationCode(pickupBranchId.toString())
				.note(matchingNote)
				.patronRequestId(UUID.randomUUID().toString())
				.activeWorkflow(STANDARD_WORKFLOW)
				.build()
		));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(holdId),
			hasLocalStatus(localHoldStatus),
			hasRequestedItemId(itemId.toString()),
			hasRequestedItemBarcode(itemBarcode)
		));

		mockPolarisFixture.verifyPlaceHold(RequestExtensionData.builder()
			.itemRecordID(itemId)
			.patronID(patronId)
			.pickupBranchID(pickupBranchId)
			.origin(2)
			.bibliographicRecordID(bibId)
			.itemLevelHold(true)
			.build());
	}

	@Test
	void shouldBeAbleToPlaceRequestAtBorrowingOnlyAgency() {
		// Arrange
		final Integer itemId = 274725;
		final Integer bibId = 684556;

		mockPolarisFixture.mockGetItem(itemId,
			ItemRecordFull.builder()
				.itemRecordID(itemId)
				.bibInfo(ApplicationServicesClient.BibInfo.builder()
					.bibliographicRecordID(bibId)
					.build())
				.build());

		mockPolarisFixture.mockGetBib(bibId,
			BibliographicRecord.builder()
				.bibliographicRecordID(bibId)
				.build());

		mockPolarisFixture.mockPlaceHold();

		final Integer holdId = 476522;
		final var matchingNote = "DCB Testing PACDisplayNotes";

		final Integer patronId = 538539;

		mockPolarisFixture.mockListPatronLocalHolds(patronId,
			SysHoldRequest.builder()
				.sysHoldRequestID(holdId)
				.bibliographicRecordID(bibId)
				.pacDisplayNotes(matchingNote)
				.build());

		final var itemBarcode = "785574212";
		final var localHoldStatus = "In Processing";

		mockPolarisFixture.mockGetHold(holdId.toString(),
			LibraryHold.builder()
				.sysHoldStatus(localHoldStatus)
				.itemRecordID(itemId)
				.itemBarcode(itemBarcode)
				.build());

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var localRequest = singleValueFrom(client.placeHoldRequestAtBorrowingAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId(patronId.toString())
				.localBibId(bibId.toString())
				.localItemId(itemId.toString())
				.pickupLocationCode("543875")
				.note(matchingNote)
				.patronRequestId(UUID.randomUUID().toString())
				.activeWorkflow(PICKUP_ANYWHERE_WORKFLOW)
				.build()
		));

		// Assert
		assertThat(localRequest, allOf(
			notNullValue(),
			hasLocalId(holdId),
			hasLocalStatus(localHoldStatus),
			hasRequestedItemId(itemId.toString()),
			hasRequestedItemBarcode(itemBarcode)
		));

		mockPolarisFixture.verifyPlaceHold(RequestExtensionData.builder()
			.itemRecordID(itemId)
			.patronID(patronId)
			.pickupBranchID(ILL_LOCATION_ID)
			.origin(2)
			.bibliographicRecordID(bibId)
			.itemLevelHold(true)
			.build());
	}
}
