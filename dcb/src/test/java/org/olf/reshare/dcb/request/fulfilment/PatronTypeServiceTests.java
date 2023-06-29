package org.olf.reshare.dcb.request.fulfilment;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.test.DcbTest;
import org.olf.reshare.dcb.test.PatronFixture;
import org.olf.reshare.dcb.test.ReferenceValueMappingFixture;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DcbTest
class PatronTypeServiceTests {
	@Inject
	private PatronTypeService patronTypeService;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@Inject
	private PatronFixture patronFixture;

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAllReferenceValueMappings();
	}

	@Test
	void shouldDeterminePatronTypeBasedUponHostLms() {
		// Arrange

		// We set up a mapping HOSTA.1 -> DCB.DCB_UG -> 15
		referenceValueMappingFixture.saveReferenceValueMapping(
			patronFixture.createPatronTypeMapping("HOSTA", "1", "DCB", "DCB_UG"));

		// Mapping from DCB::DCB_UG to EXAMPLE-CODE:15
		referenceValueMappingFixture.saveReferenceValueMapping(
			patronFixture.createPatronTypeMapping("DCB", "DCB_UG", "EXAMPLE-CODE", "15"));

		// Act
                // patronTypeService.determinePatronType(TARGET-CONTEXT,ORIGIN-CONTEXT,ORIGIN-VALUE)
		final var patronType = patronTypeService.determinePatronType("EXAMPLE-CODE","HOSTA","1").block();

		// Assert
		assertThat(patronType, is("15"));
	}

	@Test
	void shouldThrowExceptionWhenNoMappingFromSuppliedPatronTypeToSpinePatronType() {
		// Arrange
		referenceValueMappingFixture.saveReferenceValueMapping(
			patronFixture.createPatronTypeMapping("DCB", "DCB_UG", "EXAMPLE-CODE", "15"));

		// Act
		final var exception = assertThrows(PatronTypeMappingNotFound.class,
			() -> patronTypeService.determinePatronType(
				"EXAMPLE-CODE", "DCB","DCB_UG").block());

		// Assert
		assertNotNull(exception);
		assertThat(exception.getMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE"));
		assertThat(exception.getLocalizedMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE"));
	}

	@Test
	void shouldThrowExceptionWhenNoMappingFromSpinePatronTypeToBorrowingPatronType() {
		// Arrange
		referenceValueMappingFixture.saveReferenceValueMapping(
			patronFixture.createPatronTypeMapping("HOSTA", "1", "DCB", "DCB_UG"));

		// Act
		final var exception = assertThrows(PatronTypeMappingNotFound.class,
			() -> patronTypeService.determinePatronType(
				"EXAMPLE-CODE", "DCB","DCB_UG").block());

		// Assert
		assertNotNull(exception);
		assertThat(exception.getMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE"));
		assertThat(exception.getLocalizedMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE"));
	}

	@Test
	void shouldThrowExceptionWhenNoPatronTypeMappingFound() {

		// Act
		final var exception = assertThrows(PatronTypeMappingNotFound.class,
			() -> patronTypeService.determinePatronType(
				"EXAMPLE-CODE", "DCB","DCB_UG").block());

		// Assert
		assertNotNull(exception);
		assertThat(exception.getMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE"));
		assertThat(exception.getLocalizedMessage(), is("No mapping found from ptype DCB:DCB_UG to EXAMPLE-CODE"));
	}
}
