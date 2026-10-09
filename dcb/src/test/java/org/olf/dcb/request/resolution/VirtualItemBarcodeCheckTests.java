package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.storage.AgencyRepository;

import lombok.Builder;
import reactor.core.publisher.Mono;

/** No database: the decision is which systems are asked, and what counts as an answer. */
class VirtualItemBarcodeCheckTests {
	private static final String SUPPLIER = "SUPPLIER";
	private static final String BORROWER = "BORROWER";
	private static final String PICKUP = "PICKUP";
	private static final String PICKUP_AGENCY = "pickup-agency";
	private static final String BARCODE = "30000012345678";

	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final AgencyRepository agencyRepository = mock(AgencyRepository.class);
	private final VirtualItemBarcodeCheck check = new VirtualItemBarcodeCheck(hostLmsService, agencyRepository);

	private HostLmsClient supplier;
	private HostLmsClient borrower;
	private HostLmsClient pickup;

	@BeforeEach
	void beforeEach() {
		supplier = client(SUPPLIER, "https://supplier.example.com");
		borrower = client(BORROWER, "https://borrower.example.com");
		pickup = client(PICKUP, "https://pickup.example.com");

		final var pickupHostLmsId = randomUUID();

		when(hostLmsService.getClientFor(SUPPLIER)).thenReturn(Mono.just(supplier));
		when(hostLmsService.getClientFor(BORROWER)).thenReturn(Mono.just(borrower));
		when(hostLmsService.getClientFor(pickupHostLmsId)).thenReturn(Mono.just(pickup));
		when(agencyRepository.findOneByCode(PICKUP_AGENCY)).thenReturn(Mono.just(DataAgency.builder()
			.code(PICKUP_AGENCY)
			.hostLms(DataHostLms.builder().id(pickupHostLmsId).code(PICKUP).build())
			.build()));
	}

	@Test
	void shouldFindTheBarcodeAtTheBorrowingSystem() {
		when(borrower.getItemByBarcode(BARCODE)).thenReturn(Mono.just(HostLmsItem.builder().build()));

		assertThat(present(copy(BARCODE), BORROWER, null), is(true));
	}

	@Test
	void shouldNotFindABarcodeTheBorrowingSystemDoesNotHave() {
		when(borrower.getItemByBarcode(BARCODE)).thenReturn(Mono.empty());

		assertThat(present(copy(BARCODE), BORROWER, null), is(false));
	}

	@Test
	void shouldTreatAFailedLookupAsNoAnswer() {
		when(borrower.getItemByBarcode(BARCODE)).thenReturn(Mono.error(new RuntimeException("unreachable")));

		assertThat(present(copy(BARCODE), BORROWER, null), is(false));
	}

	@Test
	void shouldTreatASlowLookupAsNoAnswer() {
		when(borrower.getItemByBarcode(BARCODE)).thenReturn(Mono.never());

		assertThat(present(copy(BARCODE), BORROWER, null), is(false));
	}

	@Test
	void shouldNotAskTheSuppliersOwnSystem() {
		assertThat(present(copy(BARCODE), SUPPLIER, null), is(false));

		verify(supplier, never()).getItemByBarcode(anyString());
	}

	@Test
	void shouldNotAskASystemThatCanHoldTwoVirtualItemsForOneCopy() {
		when(borrower.canHoldTwoVirtualItemsForOneCopy()).thenReturn(true);

		assertThat(present(copy(BARCODE), BORROWER, null), is(false));

		verify(borrower, never()).getItemByBarcode(anyString());
	}

	@Test
	void shouldFindTheBarcodeAtAPickupSystemThatIsAnotherSystem() {
		when(borrower.getItemByBarcode(BARCODE)).thenReturn(Mono.empty());
		when(pickup.getItemByBarcode(BARCODE)).thenReturn(Mono.just(HostLmsItem.builder().build()));

		assertThat(present(copy(BARCODE), BORROWER, PICKUP_AGENCY), is(true));
	}

	@Test
	void shouldNotAskAPickupSystemOnTheSuppliersServer() {
		final var suppliersServer = supplier.getClientId();
		when(pickup.getClientId()).thenReturn(suppliersServer);
		when(borrower.getItemByBarcode(BARCODE)).thenReturn(Mono.empty());

		assertThat(present(copy(BARCODE), BORROWER, PICKUP_AGENCY), is(false));

		verify(pickup, never()).getItemByBarcode(anyString());
	}

	@Test
	void shouldNotAskAnySystemForACopyWithoutABarcode() {
		assertThat(present(copy(null), BORROWER, null), is(false));

		verify(borrower, never()).getItemByBarcode(any());
	}

	private boolean present(Item item, String borrowingHostLmsCode, String pickupAgencyCode) {
		return check.barcodeAlreadyPresent(item, Parameters.builder()
				.borrowingHostLmsCode(borrowingHostLmsCode)
				.pickupAgencyCode(pickupAgencyCode)
				.build())
			.block();
	}

	private static Item copy(String barcode) {
		return Item.builder()
			.localId("copy-1")
			.barcode(barcode)
			.agency(DataAgency.builder()
				.code("supplier-agency")
				.hostLms(DataHostLms.builder().id(randomUUID()).code(SUPPLIER).build())
				.build())
			.build();
	}

	private static HostLmsClient client(String hostLmsCode, String clientId) {
		final var client = mock(HostLmsClient.class);

		when(client.getHostLmsCode()).thenReturn(hostLmsCode);
		when(client.getClientId()).thenReturn(clientId);
		when(client.compareTo(any())).thenCallRealMethod();

		return client;
	}

	@Builder
	record Parameters(List<String> excludedSupplyingAgencyCodes,
		String borrowingAgencyCode, String borrowingHostLmsCode, Boolean isExpeditedCheckout,
		String pickupAgencyCode) implements ItemFilterParameters { }
}
