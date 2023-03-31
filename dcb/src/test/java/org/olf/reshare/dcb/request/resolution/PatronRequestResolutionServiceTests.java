package org.olf.reshare.dcb.request.resolution;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;

import org.olf.reshare.dcb.item.availability.LiveAvailability;
import reactor.core.publisher.Mono;

class PatronRequestResolutionServiceTests {
	private final SharedIndexService sharedIndex = mock(SharedIndexService.class);
	private final LiveAvailability liveAvailability = mock(LiveAvailability.class);

	private final PatronRequestResolutionService resolutionService
		= new PatronRequestResolutionService(sharedIndex, liveAvailability);

	@Test
	void shouldResolveToFirstItemWithSingleBibAndSingleBib() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();
		org.olf.reshare.dcb.item.availability.Item item1 = createFakeItem("id0");

		when(sharedIndex.findClusteredBib(any()))
			.thenReturn(Mono.just(new ClusteredBib(UUID.randomUUID(), List.of(createFakeBib()))));

		when(liveAvailability.getAvailableItems(any(), any()))
			.thenReturn(Mono.just(List.of(item1)));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// resolve patron request to supplier request
		final var supplierRequestRecord = resolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.getItemId(), is("id0"));
		assertThat(supplierRequestRecord.getHostLmsCode(), is("hostLmsCode"));

		// check patron request has the expected status
		assertThat(supplierRequestRecord.getPatronRequest(), is(notNullValue()));
		assertThat(supplierRequestRecord.getPatronRequest().getStatusCode(), is(RESOLVED));
	}
	@Test
	void shouldResolveToFirstItemWhenSingleBibWithMultipleItems() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();
		org.olf.reshare.dcb.item.availability.Item item1 = createFakeItem("id0");
		org.olf.reshare.dcb.item.availability.Item item2 = createFakeItem("id1");
		org.olf.reshare.dcb.item.availability.Item item3 = createFakeItem("id2");

		when(sharedIndex.findClusteredBib(any()))
			.thenReturn(Mono.just(new ClusteredBib(UUID.randomUUID(), List.of(createFakeBib()))));

		when(liveAvailability.getAvailableItems(any(), any()))
			.thenReturn(Mono.just(List.of(item1, item2, item3)));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// resolve patron request to supplier request
		final var supplierRequestRecord = resolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.getItemId(), is("id0"));
		assertThat(supplierRequestRecord.getHostLmsCode(), is("hostLmsCode"));

		// check patron request has the expected status
		assertThat(supplierRequestRecord.getPatronRequest(), is(notNullValue()));
		assertThat(supplierRequestRecord.getPatronRequest().getStatusCode(), is(RESOLVED));
	}

	@Test
	void shouldResolveToFirstItemWhenMultipleBibsWithMultipleItems() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();
		org.olf.reshare.dcb.item.availability.Item item1 = createFakeItem("id0");
		org.olf.reshare.dcb.item.availability.Item item2 = createFakeItem("id1");
		org.olf.reshare.dcb.item.availability.Item item3 = createFakeItem("id2");

		when(sharedIndex.findClusteredBib(any()))
			.thenReturn(Mono.just(new ClusteredBib(UUID.randomUUID(),
				List.of(createFakeBib(), createFakeBib(), createFakeBib()))));

		when(liveAvailability.getAvailableItems(any(), any()))
			.thenReturn(Mono.just(List.of(item1, item2, item3)));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// resolve patron request to supplier request
		final var supplierRequestRecord = resolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.getItemId(), is("id0"));
		assertThat(supplierRequestRecord.getHostLmsCode(), is("hostLmsCode"));

		// check patron request has the expected status
		assertThat(supplierRequestRecord.getPatronRequest(), is(notNullValue()));
		assertThat(supplierRequestRecord.getPatronRequest().getStatusCode(), is(RESOLVED));
	}

	@Test
	void shouldFailToResolveRequestWhenBibsIsEmpty() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId, List.of());

		when(sharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolutionService.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No bibs in clustered bib", responseMsg);
	}

	@Test
	void shouldFailToResolveRequestWhenBibsIsNull() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId, null);

		when(sharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolutionService.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No bibs in clustered bib", responseMsg);
	}

	@Test
	void shouldFailToResolveRequestWhenItemListIsEmpty() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(createFakeBib()));

		when(sharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		when(liveAvailability.getAvailableItems(any(), any()))
			.thenReturn(Mono.just(List.of()));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolvePatronRequest.class,
			() -> resolutionService.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No items in bib", responseMsg);
	}

	private static PatronRequest createPatronRequest(UUID bibClusterId) {
		return new PatronRequest(UUID.randomUUID(), null, null,
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode", SUBMITTED_TO_DCB);
	}

	private static org.olf.reshare.dcb.item.availability.Item createFakeItem(String id) {
		return new org.olf.reshare.dcb.item.availability.Item(id,
			new org.olf.reshare.dcb.item.availability.Status("code", "displayText", "dueDate"),
			new org.olf.reshare.dcb.item.availability.Location("code","name"),
			"barcode",
			"callNumber",
			"hostLmsCode");
	}
	private static Bib createFakeBib(){
		return new Bib(UUID.randomUUID(), "bibRecordId", "hostLmsCode");
	}
}
