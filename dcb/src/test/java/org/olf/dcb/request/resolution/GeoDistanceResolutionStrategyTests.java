package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LocationFixture;

import jakarta.inject.Inject;

@DcbTest
class GeoDistanceResolutionStrategyTests {
	@Inject
	private GeoDistanceResolutionStrategy resolutionStrategy;

	@Inject
	private LocationFixture locationFixture;

	@BeforeEach
	void beforeEach() {
		locationFixture.deleteAll();
	}

	@Test
	void shouldChooseNoItemWhenNoItemsAreProvided() {
		// Arrange
		final var pickupLocationId = locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location").getId();

		// Act
		final var chosenItem = chooseItem(emptyList(), pickupLocationId);

		// Assert
		assertThat(chosenItem, nullValue());
	}

	private Item chooseItem(List<Item> items,
		UUID pickupLocationId) {

		return singleValueFrom(resolutionStrategy.chooseItem(items, randomUUID(),
			PatronRequest.builder()
				.pickupLocationCode(pickupLocationId.toString())
				.build()));
	}
}
