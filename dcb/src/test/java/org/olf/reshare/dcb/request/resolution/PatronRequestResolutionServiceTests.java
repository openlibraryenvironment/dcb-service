package org.olf.reshare.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.FakeHostLms;
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.core.model.ItemStatus;
import org.olf.reshare.dcb.core.model.ItemStatusCode;
import org.olf.reshare.dcb.core.model.Location;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.item.availability.LiveAvailability;

import reactor.core.publisher.Mono;

class PatronRequestResolutionServiceTests {
	private final ClusteredBibFinder clusteredBibFinder = mock(ClusteredBibFinder.class);
	private final LiveAvailability liveAvailability = mock(LiveAvailability.class);

	private final PatronRequestResolutionService resolutionService
		= new PatronRequestResolutionService(clusteredBibFinder, liveAvailability);

	@Test
	void shouldResolveToOnlyItemForSingleBibWithSingleRequestableItem() {
		final var item = createFakeItem("78458456", "FAKE_HOST");

		final var hostLms = createHostLms("FAKE_HOST");

		final var clusteredBib = createClusteredBib(List.of(createFakeBib("65767547", hostLms)));
		final var clusteredBibId = clusteredBib.getId();

		when(clusteredBibFinder.findClusteredBib(clusteredBibId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.just(List.of(item)));

		final var patronRequest = createPatronRequest(clusteredBibId);

		final var resolution = resolve(patronRequest);

		assertThat(resolution, is(notNullValue()));

		assertThat(resolution.getOptionalSupplierRequest().isPresent(), is(true));

		final var supplierRequest = resolution.getOptionalSupplierRequest().get();

		// check supplier request has the item we expected
		assertThat(supplierRequest.getItemId(), is("78458456"));
		assertThat(supplierRequest.getHostLmsCode(), is("FAKE_HOST"));

		// check patron request has the expected status
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(), is(RESOLVED));
	}

	@Test
	void shouldResolveToFirstItemForSingleBibWithMultipleItems() {

		final var item1 = createFakeItem("23721346", "FAKE_HOST", AVAILABLE, true);
		final var item2 = createFakeItem("54737664", "FAKE_HOST", AVAILABLE, true);
		final var item3 = createFakeItem("28375763", "FAKE_HOST", AVAILABLE, true);

		final var hostLms = createHostLms("FAKE_HOST");

		final var clusteredBib = createClusteredBib(List.of(createFakeBib("65767547", hostLms)));
		final var clusteredBibId = clusteredBib.getId();

		when(clusteredBibFinder.findClusteredBib(clusteredBibId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.just(List.of(item1, item2, item3)));

		final var patronRequest = createPatronRequest(clusteredBibId);

		final var resolution = resolve(patronRequest);

		assertThat(resolution, is(notNullValue()));

		assertThat(resolution.getOptionalSupplierRequest().isPresent(), is(true));

		final var supplierRequest = resolution.getOptionalSupplierRequest().get();

		// check supplier request has the item we expected
		assertThat(supplierRequest.getItemId(), is("23721346"));
		assertThat(supplierRequest.getHostLmsCode(), is("FAKE_HOST"));

		// check patron request has the expected status
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(), is(RESOLVED));
	}

	@Test
	void shouldResolveToFirstRequestableItemForSingleBibWithMultipleItems() {

		final var unavailableItem = createFakeItem("23721346", "FAKE_HOST", UNAVAILABLE, false);
		final var unknownStatusItem = createFakeItem("54737664", "FAKE_HOST", UNKNOWN, false);
		final var checkedOutItem = createFakeItem("28375763", "FAKE_HOST", CHECKED_OUT, false);
		final var firstAvailableItem = createFakeItem("47463572", "FAKE_HOST", AVAILABLE, true);
		final var secondAvailableItem = createFakeItem("97848745", "FAKE_HOST", AVAILABLE, true);


		final var hostLms = createHostLms("FAKE_HOST");

		final var clusteredBib = createClusteredBib(List.of(createFakeBib("65767547", hostLms)));
		final var clusteredBibId = clusteredBib.getId();

		when(clusteredBibFinder.findClusteredBib( clusteredBibId ))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems( clusteredBib ))
			.thenReturn(Mono.just(List.of(unavailableItem, unknownStatusItem, checkedOutItem,
				firstAvailableItem, secondAvailableItem)));

		final var patronRequest = createPatronRequest( clusteredBibId );

		final var resolution = resolve(patronRequest);

		assertThat(resolution, is(notNullValue()));

		assertThat(resolution.getOptionalSupplierRequest().isPresent(), is(true));

		final var supplierRequest = resolution.getOptionalSupplierRequest().get();

		// check supplier request has the item we expected
		assertThat(supplierRequest.getItemId(), is("47463572"));
		assertThat(supplierRequest.getHostLmsCode(), is("FAKE_HOST"));

		// check patron request has the expected status
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(), is(RESOLVED));
	}

	@Test
	void shouldResolveToFirstRequestableItemForMultipleBibsEachWithDifferentNumberOfItems() {

		final var item1 = createFakeItem("23721346", "FOO_HOST");
		final var item2 = createFakeItem("54737664", "BAR_HOST");
		final var item3 = createFakeItem("28375763", "SHOE_HOST");

		final var barHost = createHostLms("BAR_HOST");
		final var fooHost = createHostLms("FOO_HOST");
		final var shoeHost = createHostLms("SHOE_HOST");

		final var clusteredBib = createClusteredBib(List.of(
			createFakeBib("656845864", barHost),
			createFakeBib("454973743", fooHost),
			createFakeBib("293372649", shoeHost)));

		final var clusteredBibId = clusteredBib.getId();

		when(clusteredBibFinder.findClusteredBib( clusteredBibId ))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems( clusteredBib ))
			.thenReturn(Mono.just(List.of(item2)));

		when(liveAvailability.getAvailableItems( clusteredBib ))
			.thenReturn(Mono.just(List.of()));

		when(liveAvailability.getAvailableItems( clusteredBib ))
			.thenReturn(Mono.just(List.of(item1, item3)));

		final var patronRequest = createPatronRequest( clusteredBibId );

		final var resolution = resolve(patronRequest);

		assertThat(resolution, is(notNullValue()));

		assertThat(resolution.getOptionalSupplierRequest().isPresent(), is(true));

		final var supplierRequest = resolution.getOptionalSupplierRequest().get();

		// check supplier request has the item we expected
		assertThat(supplierRequest.getItemId(), is("23721346"));
		assertThat(supplierRequest.getHostLmsCode(), is("FOO_HOST"));

		// check patron request has the expected status
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(), is(RESOLVED));
	}

	@Test
	void shouldResolveRequestToNoAvailableItemsWhenNoAvailableItemsAreFound() {
		final var bibClusterId = randomUUID();

		final var unavailableItem = createFakeItem("23721346", "FAKE_HOST", UNAVAILABLE, false);
		final var unknownStatusItem = createFakeItem("54737664", "FAKE_HOST", UNKNOWN, false);
		final var checkedOutItem = createFakeItem("28375763", "FAKE_HOST", CHECKED_OUT, false);

		final var hostLms = createHostLms("FAKE_HOST");

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			List.of(createFakeBib("56547675", hostLms)));

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.just(List.of(unavailableItem, unknownStatusItem, checkedOutItem)));

		final var patronRequest = createPatronRequest(bibClusterId);

		final var resolution = resolve(patronRequest);

		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(),
			is(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY));

		assertThat(resolution.getOptionalSupplierRequest().isEmpty(), is(true));
	}

	@Test
	void shouldResolveRequestToNoAvailableItemsWhenNoItemsAreFound() {
		final var bibClusterId = randomUUID();

		final var hostLms = createHostLms("FAKE_HOST");

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			List.of(createFakeBib("56547675", hostLms)));

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.just(List.of()));

		final var patronRequest = createPatronRequest(bibClusterId);

		final var resolution = resolve(patronRequest);

		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(),
			is(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY));

		assertThat(resolution.getOptionalSupplierRequest().isEmpty(), is(true));
	}

	@Test
	void shouldResolveRequestToNoAvailableItemsWhenItemsIsNull() {
		final var bibClusterId = randomUUID();

		final var hostLms = createHostLms("FAKE_HOST");

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			List.of(createFakeBib("37436728", hostLms)));

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.just(List.of()));

		final var patronRequest = createPatronRequest(bibClusterId);

		final var resolution = resolve(patronRequest);

		// A quirk how resolution combines multiple checks for item availability
		// means they are combined into an empty list rather than null
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(),
			is(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY));

		assertThat(resolution.getOptionalSupplierRequest().isEmpty(), is(true));
	}

	@Test
	void shouldFailToResolveRequestWhenBibsIsEmpty() {
		final var bibClusterId = randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			List.of());

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		final var patronRequest = createPatronRequest(bibClusterId);

		// check exception thrown is what is expected
		final var exception = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolve(patronRequest));

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No bibs in clustered bib"));
	}

	@Test
	void shouldFailToResolveRequestWhenBibsIsNull() {
		final var bibClusterId = randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			null);

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		final var patronRequest = createPatronRequest(bibClusterId);

		// check exception thrown is what is expected
		final var exception = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolve(patronRequest));

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No bibs in clustered bib"));
	}

	@Test
	void failWhenClusteredBibIsEmpty() {
		final var bibClusterId = randomUUID();

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.empty());

		final var patronRequest = createPatronRequest(bibClusterId);

		final var exception = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolve(patronRequest));

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(),
			is("Unable to find clustered record: " + bibClusterId));
	}

	private Resolution resolve(PatronRequest patronRequest) {
		return resolutionService.resolvePatronRequest(patronRequest).block();
	}

	private static ClusteredBib createClusteredBib(List<Bib> bibs) {
		return ClusteredBib.builder()
			.id(randomUUID())
			.title("Brain of the Firm")
			.bibs(bibs)
			.build();
	}

	private static PatronRequest createPatronRequest(UUID bibClusterId) {
		return new PatronRequest(randomUUID(), null, null,
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode", SUBMITTED_TO_DCB);
	}

	private static Item createFakeItem(
		String id, String hostLmsCode) {
		return createFakeItem(id, hostLmsCode, AVAILABLE, true);
	}

	private static Item createFakeItem(
		String id, String hostLmsCode, ItemStatusCode statusCode, Boolean requestable) {

		return new Item(id,
			new ItemStatus(statusCode), null, Location.builder()
				.code("code")
				.name("name")
				.build(),
			"barcode", "callNumber",
			hostLmsCode, requestable,
			0);
	}

	private static Bib createFakeBib(String bibRecordId, FakeHostLms hostLms) {
		return new Bib(randomUUID(), bibRecordId, hostLms);
	}

	private static FakeHostLms createHostLms(String code) {
		return new FakeHostLms(randomUUID(), code, "Fake Host LMS",
			SierraLmsClient.class, Map.of());
	}
}
