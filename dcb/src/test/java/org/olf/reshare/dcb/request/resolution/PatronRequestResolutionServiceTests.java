package org.olf.reshare.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.NO_ITEMS_AVAILABLE_AT_ANY_AGENCY;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.FakeHostLms;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.item.availability.LiveAvailability;
import org.olf.reshare.dcb.item.availability.Location;
import org.olf.reshare.dcb.item.availability.Status;

import reactor.core.publisher.Mono;

class PatronRequestResolutionServiceTests {
	private final ClusteredBibFinder clusteredBibFinder = mock(ClusteredBibFinder.class);
	private final LiveAvailability liveAvailability = mock(LiveAvailability.class);

	private final PatronRequestResolutionService resolutionService
		= new PatronRequestResolutionService(clusteredBibFinder, liveAvailability);

	@Test
	void shouldResolveToOnlyItemForSingleBibWithSingleItem() {
		final var bibClusterId = randomUUID();

		final var item = createFakeItem("78458456", "ONLY_HOST");

		final var hostLms = createHostLms("FAKE_HOST");

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(new ClusteredBib(randomUUID(), "Fake Title",
				List.of(createFakeBib("65767547", hostLms)))));

		when(liveAvailability.getAvailableItems("65767547", hostLms))
			.thenReturn(Mono.just(List.of(item)));

		final var patronRequest = createPatronRequest(bibClusterId);

		final var resolution = resolve(patronRequest);

		assertThat(resolution, is(notNullValue()));

		assertThat(resolution.getOptionalSupplierRequest().isPresent(), is(true));

		final var supplierRequest = resolution.getOptionalSupplierRequest().get();

		// check supplier request has the item we expected
		assertThat(supplierRequest.getItemId(), is("78458456"));
		assertThat(supplierRequest.getHostLmsCode(), is("ONLY_HOST"));

		// check patron request has the expected status
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(), is(RESOLVED));
	}
	@Test
	void shouldResolveToFirstItemForSingleBibWithMultipleItems() {
		final var bibClusterId = randomUUID();

		final var item1 = createFakeItem("23721346", "FOO_HOST");
		final var item2 = createFakeItem("54737664", "BAR_HOST");
		final var item3 = createFakeItem("28375763", "SHOE_HOST");

		final var hostLms = createHostLms("FAKE_HOST");

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(new ClusteredBib(randomUUID(), "Fake Title",
				List.of(createFakeBib("657476765", hostLms)))));

		when(liveAvailability.getAvailableItems("657476765", hostLms))
			.thenReturn(Mono.just(List.of(item1, item2, item3)));

		final var patronRequest = createPatronRequest(bibClusterId);

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
	void shouldResolveToFirstItemForMultipleBibsEachWithMixtureOfItems() {
		final var bibClusterId = randomUUID();

		final var item1 = createFakeItem("23721346", "FOO_HOST");
		final var item2 = createFakeItem("54737664", "BAR_HOST");
		final var item3 = createFakeItem("28375763", "SHOE_HOST");

		final var barHost = createHostLms("BAR_HOST");
		final var fooHost = createHostLms("FOO_HOST");
		final var shoeHost = createHostLms("SHOE_HOST");

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(new ClusteredBib(randomUUID(), "Fake Title", List.of(
				createFakeBib("656845864", barHost),
				createFakeBib("454973743", fooHost),
				createFakeBib("293372649", shoeHost)))));

		when(liveAvailability.getAvailableItems("656845864", barHost))
			.thenReturn(Mono.just(List.of(item2)));

		when(liveAvailability.getAvailableItems("454973743", fooHost))
			.thenReturn(Mono.just(List.of()));

		when(liveAvailability.getAvailableItems("293372649", shoeHost))
			.thenReturn(Mono.just(List.of(item3, item1)));

		final var patronRequest = createPatronRequest(bibClusterId);

		final var resolution = resolve(patronRequest);

		assertThat(resolution, is(notNullValue()));

		assertThat(resolution.getOptionalSupplierRequest().isPresent(), is(true));

		final var supplierRequest = resolution.getOptionalSupplierRequest().get();

		// check supplier request has the item we expected
		assertThat(supplierRequest.getItemId(), is("54737664"));
		assertThat(supplierRequest.getHostLmsCode(), is("BAR_HOST"));

		// check patron request has the expected status
		assertThat(resolution.getPatronRequest(), is(notNullValue()));
		assertThat(resolution.getPatronRequest().getStatusCode(), is(RESOLVED));
	}

	@Test
	void shouldResolveRequestToNoAvailableItemsWhenNoItemsAreFound() {
		final var bibClusterId = randomUUID();

		final var hostLms = createHostLms("FAKE_HOST");

		final var clusteredBib = new ClusteredBib(bibClusterId, "Fake Title",
			List.of(createFakeBib("56547675", hostLms)));

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems("56547675", hostLms))
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

		final var clusteredBib = new ClusteredBib(bibClusterId, "Fake Title",
			List.of(createFakeBib("37436728", hostLms)));

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems("37436728", hostLms))
			.thenReturn(Mono.empty());

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

		final var clusteredBib = new ClusteredBib(bibClusterId, "Fake Title", List.of());

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

		final var clusteredBib = new ClusteredBib(bibClusterId, null, null);

		when(clusteredBibFinder.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		final var patronRequest = createPatronRequest(bibClusterId);

		// check exception thrown is what is expected
		final var exception = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolve(patronRequest));

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("No bibs in clustered bib"));
	}

	private Resolution resolve(PatronRequest patronRequest) {
		return resolutionService.resolvePatronRequest(patronRequest).block();
	}

	private static PatronRequest createPatronRequest(UUID bibClusterId) {
		return new PatronRequest(randomUUID(), null, null,
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode", SUBMITTED_TO_DCB);
	}

	private static org.olf.reshare.dcb.item.availability.Item createFakeItem(
		String id, String hostLmsCode) {

		return new org.olf.reshare.dcb.item.availability.Item(id,
			new Status("code", "displayText", "dueDate"),
			new Location("code","name"),
			"barcode", "callNumber", hostLmsCode);
	}

	private static Bib createFakeBib(String bibRecordId, FakeHostLms hostLms) {
		return new Bib(randomUUID(), bibRecordId, hostLms);
	}

	private static FakeHostLms createHostLms(String code) {
		return new FakeHostLms(randomUUID(), code, "Fake Host LMS",
			SierraLmsClient.class, Map.of());
	}
}
