package org.olf.reshare.dcb.request.resolution;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.request.resolution.fake.FakeSharedIndexService;
import org.olf.reshare.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class PatronResolutionServiceTests {

	@Inject
	PatronRequestResolutionService patronRequestResolutionService;

	@Inject
	FakeSharedIndexService sharedIndex;

	@Test
	void shouldResolveToFirstItemWhenSingleHoldingsWithSingleItem() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();
		final var itemId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(new Holdings(new Holdings.Agency("agency"),
			List.of(new Holdings.Item(itemId))
		)));

		sharedIndex.addClusteredBib(clusteredBib);

		// A test patron request
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode");

		// resolve patron request to supplier request
		final var supplierRequestRecord = patronRequestResolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.item().id(), is(itemId));
		assertThat(supplierRequestRecord.agency(),
			is(new Holdings.Agency("agency")));
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
					new Holdings.Item(itemId3))
			)));

		sharedIndex.addClusteredBib(clusteredBib);

		// A test patron request
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode");

		// resolve patron request to supplier request
		final var supplierRequestRecord = patronRequestResolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.item().id(), is(itemId1));
		assertThat(supplierRequestRecord.agency(),
			is(new Holdings.Agency("agency")));
	}

	@Test
	void shouldResolveToFirstItemWhenMultipleHoldingsWithMultipleItems() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();
		final var itemId1 = UUID.randomUUID();
		final var itemId2 = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(
				new Holdings(new Holdings.Agency("agency 1"), List.of(new Holdings.Item(itemId1))),
				new Holdings(new Holdings.Agency("agency 2"), List.of(new Holdings.Item(itemId2)))
				));

		sharedIndex.addClusteredBib(clusteredBib);

		// A test patron request
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode");

		// resolve patron request to supplier request
		final var supplierRequestRecord = patronRequestResolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.item().id(), is(itemId1));
		assertThat(supplierRequestRecord.agency(),
			is(new Holdings.Agency("agency 1")));
	}
	@Test
	void shouldFailResolveRequestWhenThereAreNoHoldings() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of());

		sharedIndex.addClusteredBib(clusteredBib);

		// A test patron request
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode");

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolveHoldings.class,
			() -> patronRequestResolutionService
				.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No holdings in clustered bib", responseMsg);
	}

	@Test
	void shouldFailToResolveRequestWhenHoldingsIsNull() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId, null);

		sharedIndex.addClusteredBib(clusteredBib);

		// A test patron request
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode");

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolveHoldings.class,
			() -> patronRequestResolutionService
							.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No holdings in clustered bib", responseMsg);
	}

	@Test
	void shouldFailToResolveRequestWhenThereAreNoItemsForOnlyHoldings() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(new Holdings(new Holdings.Agency("agency 1"), List.of())));

		sharedIndex.addClusteredBib(clusteredBib);

		// A test patron request
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode");

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolveAnItem.class,
			() -> patronRequestResolutionService
				.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No Items in holdings", responseMsg);
	}

	@Test
	void shouldFailToResolveRequestWhenItemsForOnlyHoldingsIsNull() {
		// create a shared index with bib
		final var bibClusterId = UUID.randomUUID();

		final var clusteredBib = new ClusteredBib(bibClusterId,
			List.of(new Holdings(new Holdings.Agency("agency 1"), null)));

		sharedIndex.addClusteredBib(clusteredBib);

		// A test patron request
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId", "patronAgencyCode",
			bibClusterId, "pickupLocationCode");

		// check Exception thrown is what is expected
		final var e = assertThrows(UnableToResolveAnItem.class,
			() -> patronRequestResolutionService
							.resolvePatronRequest(patronRequest).block());

		final var responseMsg = e.getMessage();

		assertEquals("No Items in holdings", responseMsg);
	}
}
