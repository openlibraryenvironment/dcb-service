package org.olf.reshare.dcb.request.resolution;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.request.resolution.fake.FakeSharedIndexService;
import org.olf.reshare.dcb.test.DcbTest;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@DcbTest
class FakeSharedIndexTests {

	@Inject
	PatronRequestResolutionService patronRequestResolutionService;

	@Inject
	FakeSharedIndexService sharedIndex;

	@Test
	void shouldResolveToFirstItemWhenMultipleBibsWithSingleHoldingsAndSingleItem() {
		// create a shared index with bib
		final var bibClusterId1 = UUID.randomUUID();
		final var bibClusterId2 = UUID.randomUUID();
		final var itemId1 = UUID.randomUUID();
		final var itemId2 = UUID.randomUUID();

		final var clusteredBib1 = new ClusteredBib(bibClusterId1,
			List.of(new Holdings(new Holdings.Agency("agency1"),
				List.of(new Holdings.Item(itemId1))
			)));

		final var clusteredBib2 = new ClusteredBib(bibClusterId2,
			List.of(new Holdings(new Holdings.Agency("agency2"),
				List.of(new Holdings.Item(itemId2))
			)));

		sharedIndex.addClusteredBib(clusteredBib1);
		sharedIndex.addClusteredBib(clusteredBib2);

		// A test patron request
		final var patronRequest = new PatronRequest(UUID.randomUUID(),
			"patronId", "patronAgencyCode",
			bibClusterId1, "pickupLocationCode");

		// resolve patron request to supplier request
		final var supplierRequestRecord = patronRequestResolutionService
			.resolvePatronRequest(patronRequest).block();

		assertThat(supplierRequestRecord, is(notNullValue()));

		// check supplier request has the item we expected
		assertThat(supplierRequestRecord.item().id(), is(itemId1));
		assertThat(supplierRequestRecord.agency(),
			is(new Holdings.Agency("agency1")));
	}

}
