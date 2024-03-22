package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class GeoDistanceResolutionStrategyTests {
	@Inject
	private GeoDistanceResolutionStrategy resolutionStrategy;

	@Test
	void shouldChooseNoItemWhenNoItemsAreProvided() {
		// Act
		final var chosenItem = chooseItem(emptyList(), randomUUID());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	private Item chooseItem(List<Item> items, UUID clusterRecordId) {
		return singleValueFrom(resolutionStrategy.chooseItem(items, clusterRecordId,
			PatronRequest.builder()
				.pickupLocationCode(UUID.randomUUID().toString())
				.build()));
	}
}
