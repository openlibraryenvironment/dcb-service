package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.test.DcbTest;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.items.Location;
import services.k_int.interaction.sierra.items.SierraItem;

@DcbTest
class SierraItemMapperTests {
	@Inject
	private SierraItemMapper mapper;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@Test
	void shouldTolerateNoItemStatus() {

		referenceValueMappingFixture.defineLocalToCanonicalItemTypeRangeMapping(
			"sierra-host-lms", 1, 1, "loanable-item");

		final var item = mapItem(SierraItem.builder()
			.location(Location.builder().code("some-code").build())
			.fixedFields(Map.of(61, FixedField.builder().value("1").build()))
			.build());

		assertThat(item, is(notNullValue()));
	}

	@Nullable
	private Item mapItem(SierraItem item) {
		return mapper.mapResultToItem(item, "sierra-host-lms", "134523", Optional.empty()).block();
	}
}
