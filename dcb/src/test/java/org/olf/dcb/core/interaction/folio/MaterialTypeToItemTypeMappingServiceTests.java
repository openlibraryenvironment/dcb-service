package org.olf.dcb.core.interaction.folio;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCanonicalItemType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
class MaterialTypeToItemTypeMappingServiceTests {
	@Inject
	private MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldEnrichItemWithItemTypeMappedFromMaterialTypeName() {
		// Arrange
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeMapping("folio-host-lms",
			"book", "canonical-book");

		// Act
		final var item = exampleItem("book");

		final var enrichedItem = enrichItemWithItemType(item);

		// Assert
		assertThat(enrichedItem, hasCanonicalItemType("canonical-book"));
	}

	@Test
	void shouldMapNullLocalItemTypeCodeToUnknown() {
		// Act
		final var item = exampleItem(null);

		final var enrichedItem = enrichItemWithItemType(item);

		// Assert
		assertThat(enrichedItem, hasCanonicalItemType("UNKNOWN - NULL localItemTypeCode"));
	}

	@Test
	void shouldTolerateUnmappedLocalItemTypeCodeToUnknown() {
		// Act
		final var item = exampleItem("unmapped-type");

		final var enrichedItem = enrichItemWithItemType(item);

		// Assert
		assertThat(enrichedItem, hasCanonicalItemType("UNKNOWN - No mapping found"));
	}

	@Test
	void shouldNotUseMappingForDifferentHostLms() {
		// Arrange
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeMapping("another-folio-host-lms",
			"book", "canonical-book");

		// Act
		final var item = exampleItem("unmapped-type");

		final var enrichedItem = enrichItemWithItemType(item);

		// Assert
		assertThat(enrichedItem, hasCanonicalItemType("UNKNOWN - No mapping found"));
	}

	private static Item exampleItem(String materialTypeName) {
		return Item.builder()
			.localItemTypeCode(materialTypeName)
			.owningContext("folio-host-lms")
			.build();
	}

	private Item enrichItemWithItemType(Item item) {
		return materialTypeToItemTypeMappingService.enrichItemWithMappedItemType(item)
			.block();
	}
}
