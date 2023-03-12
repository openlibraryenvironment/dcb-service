package org.olf.reshare.dcb.request.resolution;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.SUBMITTED_TO_DCB;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;

import reactor.core.publisher.Mono;

class PatronRequestResolutionServiceTests {
	private final SharedIndexService mockSharedIndex = mock(SharedIndexService.class);

	private final PatronRequestResolutionService resolutionService
		= new PatronRequestResolutionService(mockSharedIndex);

	@Test
	void shouldResolveToFirstItemWhenSingleHoldingsWithSingleItem() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();
		final var itemId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(new Holdings(new Holdings.Agency("agency"),
			List.of(new Holdings.Item(itemId)))));

		when(mockSharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// resolve patron request to supplier request
		final var supplierRequestRecord = resolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.getHoldingsItemId(), is(itemId));
		assertThat(supplierRequestRecord.getHoldingsAgencyCode(), is("agency"));

		// check patron request has the expected status
		assertThat(supplierRequestRecord.getPatronRequest(), is(notNullValue()));
		assertThat(supplierRequestRecord.getPatronRequest().getStatusCode(), is(RESOLVED));
	}
	@Test
	void shouldResolveToFirstItemWhenSingleHoldingsWithMultipleItems() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();
		final var itemId1 = UUID.randomUUID();
		final var itemId2 = UUID.randomUUID();
		final var itemId3 = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(new Holdings(new Holdings.Agency("agency"),
				List.of(new Holdings.Item(itemId1),
					new Holdings.Item(itemId2),
					new Holdings.Item(itemId3)))));

		when(mockSharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// resolve patron request to supplier request
		final var supplierRequestRecord = resolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.getHoldingsItemId(), is(itemId1));
		assertThat(supplierRequestRecord.getHoldingsAgencyCode(), is("agency"));

		// check patron request has the expected status
		assertThat(supplierRequestRecord.getPatronRequest(), is(notNullValue()));
		assertThat(supplierRequestRecord.getPatronRequest().getStatusCode(), is(RESOLVED));
	}

	@Test
	void shouldResolveToFirstItemWhenMultipleHoldingsWithMultipleItems() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();
		final var itemId1 = UUID.randomUUID();
		final var itemId2 = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(
				new Holdings(new Holdings.Agency("agency 1"),
					List.of(new Holdings.Item(itemId1))),
				new Holdings(new Holdings.Agency("agency 2"),
					List.of(new Holdings.Item(itemId2)))));

		when(mockSharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// resolve patron request to supplier request
		final var supplierRequestRecord = resolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.getHoldingsItemId(), is(itemId1));
		assertThat(supplierRequestRecord.getHoldingsAgencyCode(), is("agency 1"));

		// check patron request has the expected status
		assertThat(supplierRequestRecord.getPatronRequest(), is(notNullValue()));
		assertThat(supplierRequestRecord.getPatronRequest().getStatusCode(), is(RESOLVED));
	}

	@Test
	void shouldFailResolveRequestWhenThereAreNoHoldings() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId, List.of());

		when(mockSharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolveHoldings.class,
			() -> resolutionService.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No holdings in clustered bib", responseMsg);
	}

	@Test
	void shouldFailToResolveRequestWhenHoldingsIsNull() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId, null);

		when(mockSharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolveHoldings.class,
			() -> resolutionService.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No holdings in clustered bib", responseMsg);
	}

	@Test
	void shouldFailToResolveRequestWhenThereAreNoItemsForOnlyHoldings() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(new Holdings(new Holdings.Agency("agency 1"), List.of())));

		when(mockSharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolveAnItem.class,
			() -> resolutionService.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No Items in holdings", responseMsg);
	}

	@Test
	void shouldFailToResolveRequestWhenItemsForOnlyHoldingsIsNull() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(new Holdings(new Holdings.Agency("agency 1"), null)));

		when(mockSharedIndex.findClusteredBib(bibClusterId))
			.thenReturn(Mono.just(clusteredBib));

		// A test patron request
		final var patronRequest = createPatronRequest(bibClusterId);

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolveAnItem.class,
			() -> resolutionService.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No Items in holdings", responseMsg);
	}

	private static PatronRequest createPatronRequest(UUID bibClusterId) {
		return new PatronRequest(UUID.randomUUID(), null, null,
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode", SUBMITTED_TO_DCB);
	}
}
