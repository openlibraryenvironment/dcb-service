package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

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
import reactor.core.publisher.Mono;

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
	void shouldExcludeACopyAnUnfinishedRequestBringsToTheSameBorrower() {
		requestFor(BORROWER, PICKUP_TRANSIT, null);

		assertThat(evaluate(filter, copy(), BORROWER, null), is(false));
	}

	@Test
	void shouldIncludeTheCopyForAnotherBorrowingSystem() {
		requestFor(BORROWER, PICKUP_TRANSIT, null);

		assertThat(evaluate(filter, copy(), OTHER_BORROWER, null), is(true));
	}

	@Test
	void shouldIncludeTheCopyOnceTheEarlierRequestIsFinalised() {
		requestFor(BORROWER, FINALISED, null);

		assertThat(evaluate(filter, copy(), BORROWER, null), is(true));
	}

	@Test
	void shouldIncludeTheCopyForABorrowerThatCanHoldTwoVirtualItemsForIt() {
		requestFor(BORROWER, PICKUP_TRANSIT, null);

		final var hostLmsService = capableHostLmsService();

		assertThat(evaluate(capableFilter(hostLmsService), copy(), BORROWER, null), is(true));
	}

	@Test
	void shouldIncludeACopyFromTheBorrowersOwnSystem() {
		requestFor(SUPPLIER, PICKUP_TRANSIT, null);

		assertThat(evaluate(filter, copy(), SUPPLIER, null), is(true));
	}

	@Test
	void shouldExcludeACopyAnUnfinishedRequestCollectsFromThisBorrowersSystem() {
		requestFor(OTHER_BORROWER, PICKUP_TRANSIT, borrowerPickupLocation);

		assertThat(evaluate(filter, copy(), BORROWER, null), is(false));
	}

	@Test
	void shouldExcludeACopyPromisedToThisRequestsPickupSystem() {
		requestFor(BORROWER, PICKUP_TRANSIT, null);

		assertThat(evaluate(filter, copy(), OTHER_BORROWER, BORROWER_AGENCY), is(false));
	}

	@Test
	void shouldCheckAPickupSystemThatIsTheBorrowingSystemOnlyOnce() {
		requestFor(BORROWER, PICKUP_TRANSIT, null);

		final var hostLmsService = capableHostLmsService();

		assertThat(evaluate(capableFilter(hostLmsService), copy(), BORROWER, BORROWER_AGENCY), is(true));

		verify(hostLmsService, times(1)).getClientFor(BORROWER);
	}

	private HostLmsService capableHostLmsService() {
		final var borrowerClient = mock(HostLmsClient.class);
		when(borrowerClient.canHoldTwoVirtualItemsForOneCopy()).thenReturn(true);

		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(BORROWER)).thenReturn(Mono.just(borrowerClient));

		return hostLmsService;
	}

	private ExcludeCopyPromisedToBorrowerItemFilter capableFilter(HostLmsService hostLmsService) {
		return new ExcludeCopyPromisedToBorrowerItemFilter(patronRequestRepository, hostLmsService);
	}

	private void requestFor(String borrowingHostLmsCode, PatronRequest.Status status,
		Location pickupLocation) {

		final var patronRequest = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patronFixture.savePatron("home-library"))
			.patronHostlmsCode(borrowingHostLmsCode)
			.pickupLocationCode(pickupLocation == null ? null : pickupLocation.getId().toString())
			.status(status)
			.build());

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode(SUPPLIER)
			.localItemId(COPY_ID)
			.isActive(true)
			.build());
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

	private static boolean evaluate(ItemFilter itemFilter, Item item, String borrowingHostLmsCode,
		String pickupAgencyCode) {

		final var parameters = Parameters.builder()
			.borrowingHostLmsCode(borrowingHostLmsCode)
			.pickupAgencyCode(pickupAgencyCode)
			.build();

		return singleValueFrom(itemFilter.filterItem(parameters).apply(item));
	}

	@Builder
	record Parameters(List<String> excludedSupplyingAgencyCodes,
		String borrowingAgencyCode, String borrowingHostLmsCode, Boolean isExpeditedCheckout,
		String pickupAgencyCode) implements ItemFilterParameters { }
}
