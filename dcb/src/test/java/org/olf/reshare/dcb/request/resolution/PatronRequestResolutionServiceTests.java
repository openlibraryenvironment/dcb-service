package org.olf.reshare.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;
import static org.olf.reshare.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.FakeHostLms;
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.core.model.ItemStatus;
import org.olf.reshare.dcb.core.model.ItemStatusCode;
import org.olf.reshare.dcb.core.model.Location;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.item.availability.AvailabilityReport;
import org.olf.reshare.dcb.item.availability.LiveAvailabilityService;

import reactor.core.publisher.Mono;

class PatronRequestResolutionServiceTests {
	private final SharedIndexService sharedIndexService = mock(SharedIndexService.class);
	private final LiveAvailabilityService liveAvailability = mock(LiveAvailabilityService.class);

	private final PatronRequestResolutionService resolutionService
		= new PatronRequestResolutionService(sharedIndexService, liveAvailability);

	@Test
	@DisplayName("Should resolve request to only item when single item found")
	void shouldResolveToOnlyItemWhenSingleItemFound() {
		// Arrange
		final var item = createFakeItem("78458456", AVAILABLE, true);

		final var hostLms = createHostLms();

		final var clusteredBib = createClusteredBib(List.of(createFakeBib("65767547", hostLms)));
		final var clusteredBibId = clusteredBib.getId();

		when(sharedIndexService.findClusteredBib(clusteredBibId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.just(AvailabilityReport.ofItems(item)));

		final var patronRequest = createPatronRequest(clusteredBibId);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, is(notNullValue()));

		assertThat(resolution.getOptionalSupplierRequest().isPresent(), is(true));

		final var supplierRequest = resolution.getOptionalSupplierRequest().get();

		assertThat("Supplier request should be pending",
			supplierRequest.getStatusCode(), is(PENDING));

		// check supplier request has the item we expected
		assertThat(supplierRequest.getLocalItemId(), is("78458456"));
		assertThat(supplierRequest.getHostLmsCode(), is("FAKE_HOST"));

		// check patron request has the expected status
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(), is(RESOLVED));

		verify(liveAvailability).getAvailableItems(clusteredBib);
		verifyNoMoreInteractions(liveAvailability);
	}

	@Test
	@DisplayName("Should resolve request to first requestable items when multiple items are found")
	void shouldResolveToFirstRequestableItemWhenMultipleItemsAreFound() {
		// Arrange
		final var unavailableItem = createFakeItem("23721346", UNAVAILABLE, false);
		final var unknownStatusItem = createFakeItem("54737664", UNKNOWN, false);
		final var checkedOutItem = createFakeItem("28375763", CHECKED_OUT, false);
		final var firstAvailableItem = createFakeItem("47463572", AVAILABLE, true);
		final var secondAvailableItem = createFakeItem("97848745", AVAILABLE, true);

		final var hostLms = createHostLms();

		final var clusteredBib = createClusteredBib(List.of(createFakeBib("65767547", hostLms)));
		final var clusteredBibId = clusteredBib.getId();

		when(sharedIndexService.findClusteredBib(clusteredBibId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.just(AvailabilityReport.ofItems(unavailableItem,
				unknownStatusItem, checkedOutItem, firstAvailableItem, secondAvailableItem)));

		final var patronRequest = createPatronRequest(clusteredBibId);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution, is(notNullValue()));

		assertThat(resolution.getOptionalSupplierRequest().isPresent(), is(true));

		final var supplierRequest = resolution.getOptionalSupplierRequest().get();

		assertThat("Supplier request should be pending",
			supplierRequest.getStatusCode(), is(PENDING));

		// check supplier request has the item we expected
		assertThat(supplierRequest.getLocalItemId(), is("47463572"));
		assertThat(supplierRequest.getHostLmsCode(), is("FAKE_HOST"));

		// check patron request has the expected status
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(), is(RESOLVED));

		verify(liveAvailability).getAvailableItems(clusteredBib);
		verifyNoMoreInteractions(liveAvailability);
	}

	@Test
	@DisplayName("Should resolve request to no available items when no requestable items are found")
	void shouldResolveToNoAvailableItemsWhenNoRequestableItemsAreFound() {
		// Arrange
		final var bibClusterId = randomUUID();

		final var unavailableItem = createFakeItem("23721346", UNAVAILABLE, false);
		final var unknownStatusItem = createFakeItem("54737664", UNKNOWN, false);
		final var checkedOutItem = createFakeItem("28375763", CHECKED_OUT, false);

		final var hostLms = createHostLms();

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			List.of(createFakeBib("56547675", hostLms)));

		when(sharedIndexService.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.just(AvailabilityReport.ofItems(unavailableItem,
				unknownStatusItem, checkedOutItem)));

		final var patronRequest = createPatronRequest(bibClusterId);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(),
			is(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY));

		assertThat(resolution.getOptionalSupplierRequest().isEmpty(), is(true));

		verify(liveAvailability).getAvailableItems(clusteredBib);
		verifyNoMoreInteractions(liveAvailability);
	}

	@Test
	@DisplayName("Should resolve request to no available items when no items are found")
	void shouldResolveToNoAvailableItemsWhenNoItemsAreFound() {
		// Arrange
		final var bibClusterId = randomUUID();

		final var hostLms = createHostLms();

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			List.of(createFakeBib("56547675", hostLms)));

		when(sharedIndexService.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.just(AvailabilityReport.emptyReport()));

		final var patronRequest = createPatronRequest(bibClusterId);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(),
			is(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY));

		assertThat(resolution.getOptionalSupplierRequest().isEmpty(), is(true));

		verify(liveAvailability).getAvailableItems(clusteredBib);
		verifyNoMoreInteractions(liveAvailability);
	}

	@Test
	@DisplayName("Should resolve request to no available items when items is null")
	void shouldResolveToNoAvailableItemsWhenItemsIsNull() {
		// Arrange
		final var bibClusterId = randomUUID();

		final var hostLms = createHostLms();

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			List.of(createFakeBib("56547675", hostLms)));

		when(sharedIndexService.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(clusteredBib))
			.thenReturn(Mono.empty());

		final var patronRequest = createPatronRequest(bibClusterId);

		// Act
		final var resolution = resolve(patronRequest);

		// Assert
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(),
			is(NO_ITEMS_AVAILABLE_AT_ANY_AGENCY));

		assertThat(resolution.getOptionalSupplierRequest().isEmpty(), is(true));

		verify(liveAvailability).getAvailableItems(clusteredBib);
		verifyNoMoreInteractions(liveAvailability);
	}

	@Test
	@DisplayName("Should fail to resolve request when cluster record has no bibs")
	void shouldFailToResolveRequestWhenBibsIsEmpty() {
		// Arrange
		final var bibClusterId = randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			List.of());

		when(sharedIndexService.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		final var patronRequest = createPatronRequest(bibClusterId);

		// Act
		final var exception = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolve(patronRequest));

		// Assert
		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No bibs in clustered bib"));

		verifyNoMoreInteractions(liveAvailability);
	}

	@Test
	@DisplayName("Should fail to resolve request when cluster record bibs is null")
	void shouldFailToResolveRequestWhenBibsIsNull() {
		// Arrange
		final var bibClusterId = randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId, "Brain of the Firm",
			null);

		when(sharedIndexService.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		final var patronRequest = createPatronRequest(bibClusterId);

		// Act
		final var exception = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolve(patronRequest));

		// Assert
		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No bibs in clustered bib"));

		verifyNoMoreInteractions(liveAvailability);
	}

	@Test
	@DisplayName("Should fail to resolve request when no clustered bib is found")
	void shouldFailWhenClusteredBibIsEmpty() {
		// Arrange
		final var bibClusterId = randomUUID();

		when(sharedIndexService.findClusteredBib(bibClusterId))
			.thenReturn(Mono.empty());

		final var patronRequest = createPatronRequest(bibClusterId);

		// Act
		final var exception = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolve(patronRequest));

		// Assert
		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(),
			is("Unable to find clustered record: " + bibClusterId));

		verifyNoMoreInteractions(liveAvailability);
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
		return PatronRequest.builder()
			.id(randomUUID())
			.patron(Patron.builder().build())
			.bibClusterId(bibClusterId)
			.pickupLocationCode("pickupLocationCode")
			.statusCode(SUBMITTED_TO_DCB)
			.build();
	}

	private static Item createFakeItem(String id,
		ItemStatusCode statusCode, Boolean requestable) {

		return Item.builder()
			.id(id)
			.status(new ItemStatus(statusCode))
			.location(Location.builder()
				.code("code")
				.name("name")
				.build())
			.barcode("barcode")
			.callNumber("callNumber")
			.hostLmsCode("FAKE_HOST")
			.isRequestable(requestable)
			.holdCount(0)
			.build();
	}

	private static Bib createFakeBib(String bibRecordId, FakeHostLms hostLms) {
		return Bib.builder()
			.id(randomUUID())
			.bibRecordId(bibRecordId)
			.hostLms(hostLms)
			.build();
	}

	private static FakeHostLms createHostLms() {
		return new FakeHostLms(randomUUID(), "FAKE_HOST", "Fake Host LMS",
			SierraLmsClient.class, Map.of());
	}
}
