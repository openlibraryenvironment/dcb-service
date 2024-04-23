package org.olf.dcb.core.interaction.sierra;


import static java.lang.Integer.parseInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasCanonicalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasHomeLibraryCode;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalNames;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isBlocked;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotBlocked;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotDeleted;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.patrons.Block;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;

@MicronautTest
class SierraPatronMapperTests {
	private final String HOST_LMS_CODE = "sierra-patron-mapping";
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@Inject
	private SierraPatronMapper sierraPatronMapper;

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldMapPatron() {
		// Arrange
		final var localPatronId = "583634";
		final var localPatronType = 23;
		final var barcode = "5472792742";

		final var canonicalPatronType = "UNDERGRAD";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			HOST_LMS_CODE,
			localPatronType, localPatronType, "DCB", canonicalPatronType);

		// Act
		final var sierraPatron = SierraPatronRecord.builder()
			.id(parseInt(localPatronId))
			.barcodes(List.of(barcode))
			.names(List.of("first name", "middle name", "last name"))
			.patronType(localPatronType)
			.homeLibraryCode("home-library")
			.build();

		final var patron = mapToPatron(sierraPatron);

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			hasLocalIds(localPatronId),
			hasLocalNames("first name", "middle name", "last name"),
			hasLocalBarcodes(barcode),
			hasLocalPatronType(localPatronType),
			hasCanonicalPatronType(canonicalPatronType),
			hasHomeLibraryCode("home-library"),
			isNotBlocked(),
			isNotDeleted()
		));
	}

	@Test
	void shouldMapManuallyBlockedPatron() {
		// Arrange
		final var localPatronId = "583634";
		final var localPatronType = 23;
		final var barcode = "5472792742";

		final var canonicalPatronType = "UNDERGRAD";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,
			localPatronType, localPatronType, "DCB", canonicalPatronType);

		// Act
		final var sierraPatron = SierraPatronRecord.builder()
			.id(parseInt(localPatronId))
			.barcodes(List.of(barcode))
			.names(List.of("first name", "middle name", "last name"))
			.patronType(localPatronType)
			.homeLibraryCode("home-library")
			.blockInfo(Block.builder()
				.code("blocked")
				.build())
			.build();

		final var patron = mapToPatron(sierraPatron);

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			isBlocked()
		));
	}

	@Test
	void shouldMapAutomaticallyBlockedPatron() {
		// Arrange
		final var localPatronId = "583634";
		final var localPatronType = 23;
		final var barcode = "5472792742";

		final var canonicalPatronType = "UNDERGRAD";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,
			localPatronType, localPatronType, "DCB", canonicalPatronType);

		// Act
		final var sierraPatron = SierraPatronRecord.builder()
			.id(parseInt(localPatronId))
			.barcodes(List.of(barcode))
			.names(List.of("first name", "middle name", "last name"))
			.patronType(localPatronType)
			.homeLibraryCode("home-library")
			.autoBlockInfo(Block.builder()
				.code("blocked")
				.build())
			.build();

		final var patron = mapToPatron(sierraPatron);

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			isBlocked()
		));
	}

	private Patron mapToPatron(SierraPatronRecord sierraPatron) {
		return singleValueFrom(sierraPatronMapper.sierraPatronToHostLmsPatron(
			sierraPatron, HOST_LMS_CODE));
	}
}
