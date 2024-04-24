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
	private final static String HOST_LMS_CODE = "sierra-patron-mapping";
	private final static int MAPPED_LOCAL_PATRON_TYPE = 23;
	private final String MAPPED_CANONICAL_PATRON_TYPE = "UNDERGRAD";

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@Inject
	private SierraPatronMapper sierraPatronMapper;

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,
			MAPPED_LOCAL_PATRON_TYPE, MAPPED_LOCAL_PATRON_TYPE, "DCB",
			MAPPED_CANONICAL_PATRON_TYPE);
	}

	@Test
	void shouldMapPatron() {
		// Arrange
		final var localPatronId = "583634";
		final var barcode = "5472792742";

		// Act
		final var sierraPatron = SierraPatronRecord.builder()
			.id(parseInt(localPatronId))
			.barcodes(List.of(barcode))
			.names(List.of("first name", "middle name", "last name"))
			.patronType(MAPPED_LOCAL_PATRON_TYPE)
			.homeLibraryCode("home-library")
			.build();

		final var patron = mapToPatron(sierraPatron);

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			hasLocalIds(localPatronId),
			hasLocalNames("first name", "middle name", "last name"),
			hasLocalBarcodes(barcode),
			hasLocalPatronType(MAPPED_LOCAL_PATRON_TYPE),
			hasCanonicalPatronType(MAPPED_CANONICAL_PATRON_TYPE),
			hasHomeLibraryCode("home-library"),
			isNotBlocked(),
			isNotDeleted()
		));
	}

	@Test
	void shouldMapManuallyBlockedPatron() {
		// Act
		final var sierraPatron = SierraPatronRecord.builder()
			.id(parseInt("583634"))
			.barcodes(List.of("5472792742"))
			.names(List.of("first name", "middle name", "last name"))
			.patronType(MAPPED_LOCAL_PATRON_TYPE)
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
		// Act
		final var sierraPatron = patronWithAutomaticBlock("blocked");

		final var patron = mapToPatron(sierraPatron);

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			isBlocked()
		));
	}

	@Test
	void shouldIgnoreHyphenBlockCode() {
		// Act
		final var sierraPatron = patronWithAutomaticBlock("-");

		final var patron = mapToPatron(sierraPatron);

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			isNotBlocked()
		));
	}
	
	private static SierraPatronRecord patronWithAutomaticBlock(String blockCode) {
		return SierraPatronRecord.builder()
			.id(5837526)
			.barcodes(List.of("3725562155"))
			.names(List.of("first name", "middle name", "last name"))
			.patronType(MAPPED_LOCAL_PATRON_TYPE)
			.homeLibraryCode("home-library")
			.autoBlockInfo(Block.builder()
				.code(blockCode)
				.build())
			.build();
	}

	private Patron mapToPatron(SierraPatronRecord sierraPatron) {
		return singleValueFrom(sierraPatronMapper.sierraPatronToHostLmsPatron(
			sierraPatron, HOST_LMS_CODE));
	}
}
