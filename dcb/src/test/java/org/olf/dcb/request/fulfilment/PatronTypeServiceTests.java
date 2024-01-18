package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
class PatronTypeServiceTests {
	@Inject
	private PatronTypeService patronTypeService;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldDeterminePatronTypeBasedUponHostLms() {
		// Arrange

		// We set up a mapping HOSTA.1 -> DCB.DCB_UG -> 15
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping("HOSTA", 1,
			1, "DCB", "DCB_UG");

		// Mapping from DCB::DCB_UG to EXAMPLE-CODE:15
		referenceValueMappingFixture.definePatronTypeMapping("DCB", "DCB_UG", "EXAMPLE-CODE", "15");

		// Act
		final var patronType = singleValueFrom(patronTypeService.determinePatronType(
			"EXAMPLE-CODE","HOSTA","1", null));

		// Assert
		assertThat(patronType, is("15"));
	}

	@Test
	void shouldThrowExceptionWhenNoMappingFromSuppliedPatronTypeToSpinePatronType() {
		// Arrange
		referenceValueMappingFixture.definePatronTypeMapping("DCB", "DCB_UG", "EXAMPLE-CODE", "15");

		// Act
		final var exception = assertThrows(PatronTypeMappingNotFound.class,
			() -> patronTypeService.determinePatronType(
				"EXAMPLE-CODE", "DCB","DCB_UG", null).block());

		// Assert
		assertNotNull(exception);
		assertThat(exception.getMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE because Unable to convert DCB_UG into number For input string: \"DCB_UG\""));
		assertThat(exception.getLocalizedMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE because Unable to convert DCB_UG into number For input string: \"DCB_UG\""));
	}

	@Test
	void shouldThrowExceptionWhenNoMappingFromSpinePatronTypeToBorrowingPatronType() {
		// Arrange
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			"HOSTA", 1, 1, "DCB", "DCB_UG");

		// Act
		final var exception = assertThrows(PatronTypeMappingNotFound.class,
			() -> patronTypeService.determinePatronType(
				"EXAMPLE-CODE", "DCB","DCB_UG", null).block());

		// Assert
		assertNotNull(exception);
		assertThat(exception.getMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE because Unable to convert DCB_UG into number For input string: \"DCB_UG\""));
		assertThat(exception.getLocalizedMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE because Unable to convert DCB_UG into number For input string: \"DCB_UG\""));
	}

	@Test
	void shouldThrowExceptionWhenNoPatronTypeMappingFound() {

		// Act
		final var exception = assertThrows(PatronTypeMappingNotFound.class,
			() -> patronTypeService.determinePatronType(
				"EXAMPLE-CODE", "DCB","DCB_UG", null).block());

		// Assert
		assertNotNull(exception);
		assertThat(exception.getMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE because Unable to convert DCB_UG into number For input string: \"DCB_UG\""));
		assertThat(exception.getLocalizedMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE because Unable to convert DCB_UG into number For input string: \"DCB_UG\""));
	}
}
