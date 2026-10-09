package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LocationFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import lombok.Builder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * What DCB's own records prove. The Sierra systems here can look their virtual items up by barcode, so
 * a recorded virtual item is left to that lookup; a mocked client stands for a system that cannot.
 */
@DcbTest
class ExcludeCopyPromisedToBorrowerItemFilterTests {
	private static final String SUPPLIER = "PROMISED-SUPPLIER";
	private static final String BORROWER = "PROMISED-BORROWER";
	private static final String OTHER_BORROWER = "PROMISED-OTHER-BORROWER";
	private static final String BORROWER_AGENCY = "promised-borrower-agency";
	private static final String COPY_ID = "promised-copy-1";

	@Inject
	HostLmsFixture hostLmsFixture;
	@Inject
	AgencyFixture agencyFixture;
	@Inject
	LocationFixture locationFixture;
	@Inject
	PatronFixture patronFixture;
	@Inject
	PatronRequestsFixture patronRequestsFixture;
	@Inject
	SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	PatronRequestRepository patronRequestRepository;
	@Inject
	ExcludeCopyPromisedToBorrowerItemFilter filter;

	private Location borrowerPickupLocation;

	@BeforeAll
	void beforeAll() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		locationFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(SUPPLIER, "key", "secret", "https://supplier.example.com", "item");
		final var borrower = hostLmsFixture.createSierraHostLms(BORROWER, "key", "secret",
			"https://borrower.example.com", "item");
		hostLmsFixture.createSierraHostLms(OTHER_BORROWER, "key", "secret", "https://other.example.com", "item");

		borrowerPickupLocation = locationFixture.createPickupLocation(
			agencyFixture.defineAgency(BORROWER_AGENCY, "Borrower", borrower));
	}

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
	}

	@Test
	void shouldExcludeACopyAnEarlierRequestIsAboutToCreateAVirtualItemForAtTheSameBorrower() {
		requestFor(BORROWER, CONFIRMED, request -> request);

		assertThat(evaluate(filter, BORROWER, null), is(false));
	}

	@Test
	void shouldIncludeTheCopyWhenTheEarlierRequestIsStuck() {
		requestFor(BORROWER, CONFIRMED, request -> request.isTooLong(true));

		assertThat(evaluate(filter, BORROWER, null), is(true));
	}

	@Test
	void shouldLeaveARecordedVirtualItemToTheLookupWhereTheBorrowerCanLookItUp() {
		requestFor(BORROWER, PICKUP_TRANSIT, request -> request.localItemId("virtual-1"));

		assertThat(evaluate(filter, BORROWER, null), is(true));
	}

	@Test
	void shouldExcludeACopyWithARecordedVirtualItemWhereTheBorrowerCannotLookItUp() {
		requestFor(BORROWER, PICKUP_TRANSIT, request -> request.localItemId("virtual-1"));

		assertThat(evaluate(filterFor(clientThat(false, false)), BORROWER, null), is(false));
	}

	@Test
	void shouldIncludeTheCopyForAnErroredRequestThatRecordedNoVirtualItem() {
		requestFor(BORROWER, ERROR, request -> request);

		assertThat(evaluate(filterFor(clientThat(false, false)), BORROWER, null), is(true));
	}

	@Test
	void shouldIncludeTheCopyOnceTheEarlierRequestIsFinalised() {
		requestFor(BORROWER, FINALISED, request -> request.localItemId("virtual-1"));

		assertThat(evaluate(filterFor(clientThat(false, false)), BORROWER, null), is(true));
	}

	@Test
	void shouldIncludeTheCopyForAnotherBorrowingSystem() {
		requestFor(BORROWER, CONFIRMED, request -> request);

		assertThat(evaluate(filter, OTHER_BORROWER, null), is(true));
	}

	@Test
	void shouldIncludeTheCopyForABorrowerThatCanHoldTwoVirtualItemsForIt() {
		requestFor(BORROWER, CONFIRMED, request -> request);

		assertThat(evaluate(filterFor(clientThat(true, false)), BORROWER, null), is(true));
	}

	@Test
	void shouldIncludeACopyFromTheBorrowersOwnSystem() {
		requestFor(SUPPLIER, CONFIRMED, request -> request);

		assertThat(evaluate(filter, SUPPLIER, null), is(true));
	}

	@Test
	void shouldExcludeACopyAnEarlierRequestIsAboutToCollectAtThisBorrowersSystem() {
		requestFor(OTHER_BORROWER, REQUEST_PLACED_AT_BORROWING_AGENCY,
			request -> request.pickupLocationCode(borrowerPickupLocation.getId().toString()));

		assertThat(evaluate(filter, BORROWER, null), is(false));
	}

	@Test
	void shouldExcludeACopyPromisedToThisRequestsPickupSystem() {
		requestFor(BORROWER, CONFIRMED, request -> request);

		assertThat(evaluate(filter, OTHER_BORROWER, BORROWER_AGENCY), is(false));
	}

	@Test
	void shouldCheckAPickupSystemThatIsTheBorrowingSystemOnlyOnce() {
		requestFor(BORROWER, CONFIRMED, request -> request);

		final var hostLmsService = hostLmsServiceWith(clientThat(true, false));

		assertThat(evaluate(new ExcludeCopyPromisedToBorrowerItemFilter(patronRequestRepository, hostLmsService),
			BORROWER, BORROWER_AGENCY), is(true));

		verify(hostLmsService, times(1)).getClientFor(BORROWER);
	}

	@Test
	void shouldIncludeTheCopyWhenTheRecordsCannotBeRead() {
		final var unreadable = mock(PatronRequestRepository.class);
		when(unreadable.findSystemsAwaitingVirtualItemForSupplierCopy(any(), any(), any(), any(), any(), any()))
			.thenReturn(Flux.error(new RuntimeException("database unavailable")));
		when(unreadable.findSystemsWithRecordedVirtualItemForSupplierCopy(any(), any(), any(), any()))
			.thenReturn(Flux.empty());

		final var filterOverUnreadableRecords = new ExcludeCopyPromisedToBorrowerItemFilter(unreadable,
			hostLmsServiceWith(clientThat(false, false)));

		assertThat(evaluate(filterOverUnreadableRecords, BORROWER, null), is(true));
	}

	private static HostLmsClient clientThat(boolean canHoldTwo, boolean canSeeVirtualItems) {
		final var client = mock(HostLmsClient.class);
		when(client.canHoldTwoVirtualItemsForOneCopy()).thenReturn(canHoldTwo);
		when(client.canSeeVirtualItemsByBarcode()).thenReturn(canSeeVirtualItems);
		return client;
	}

	private static HostLmsService hostLmsServiceWith(HostLmsClient borrowerClient) {
		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(BORROWER)).thenReturn(Mono.just(borrowerClient));
		return hostLmsService;
	}

	private ExcludeCopyPromisedToBorrowerItemFilter filterFor(HostLmsClient borrowerClient) {
		return new ExcludeCopyPromisedToBorrowerItemFilter(patronRequestRepository,
			hostLmsServiceWith(borrowerClient));
	}

	private void requestFor(String borrowingHostLmsCode, PatronRequest.Status status,
		UnaryOperator<PatronRequest.PatronRequestBuilder> details) {

		final var patronRequest = patronRequestsFixture.savePatronRequest(details.apply(PatronRequest.builder()
				.id(randomUUID())
				.patron(patronFixture.savePatron("home-library"))
				.patronHostlmsCode(borrowingHostLmsCode)
				.status(status))
			.build());

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode(SUPPLIER)
			.localItemId(COPY_ID)
			.isActive(true)
			.build());
	}

	private boolean evaluate(ItemFilter itemFilter, String borrowingHostLmsCode, String pickupAgencyCode) {
		final var parameters = Parameters.builder()
			.borrowingHostLmsCode(borrowingHostLmsCode)
			.pickupAgencyCode(pickupAgencyCode)
			.build();

		return singleValueFrom(itemFilter.filterItem(parameters).apply(copy()));
	}

	private Item copy() {
		return Item.builder()
			.localId(COPY_ID)
			.agency(DataAgency.builder()
				.code(SUPPLIER + "-agency")
				.hostLms(hostLmsFixture.findByCode(SUPPLIER))
				.build())
			.build();
	}

	@Builder
	record Parameters(List<String> excludedSupplyingAgencyCodes,
		String borrowingAgencyCode, String borrowingHostLmsCode, Boolean isExpeditedCheckout,
		String pickupAgencyCode) implements ItemFilterParameters { }
}
