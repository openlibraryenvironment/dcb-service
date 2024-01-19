package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;

@DcbTest
class PatronTypeServiceTests {
	@Inject
	private PatronTypeService patronTypeService;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldDeterminePatronTypeBasedUponHostLms() {
		// Arrange
		final var requestingHostLms = hostLmsFixture.createSierraHostLms("requesting-host-lms");
		final var supplyingHostLms = hostLmsFixture.createSierraHostLms("supplying-host-lms");

		// requesting-host-lms:1 -> DCB:DCB_UG
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			requestingHostLms.getCode(), 1, 1, "DCB", "DCB_UG");

		// Mapping from DCB::DCB_UG to supplying-host-lms:15
		referenceValueMappingFixture.definePatronTypeMapping(
			"DCB", "DCB_UG", supplyingHostLms.getCode(), "15");

		// Act
		final var patronType = singleValueFrom(patronTypeService.determinePatronType(
			supplyingHostLms.getCode(), requestingHostLms.getCode(), "1", null));

		// Assert
		assertThat(patronType, is("15"));
	}

	@Test
	void shouldThrowExceptionWhenNoMappingFromSuppliedPatronTypeToSpinePatronType() {
		// Arrange
		final var requestingHostLms = hostLmsFixture.createSierraHostLms("requesting-host-lms");
		final var supplyingHostLms = hostLmsFixture.createSierraHostLms("supplying-host-lms");

		referenceValueMappingFixture.definePatronTypeMapping(
			"DCB", "DCB_UG", supplyingHostLms.getCode(), "15");

		// Act
		final var exception = assertThrows(PatronTypeMappingNotFound.class,
			() -> singleValueFrom(patronTypeService.determinePatronType(
				supplyingHostLms.getCode(), requestingHostLms.getCode(), "1", null)));

		// Assert
		assertThat(exception, hasMessage(
			"No mapping found from ptype requesting-host-lms:1 to supplying-host-lms because Unable to map patronType requesting-host-lms:1 To DCB context"));
	}

	@Test
	void shouldThrowExceptionWhenNoMappingFromSpinePatronTypeToBorrowingPatronType() {
		// Arrange
		final var requestingHostLms = hostLmsFixture.createSierraHostLms("requesting-host-lms");
		final var supplyingHostLms = hostLmsFixture.createSierraHostLms("supplying-host-lms");

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			requestingHostLms.getCode(), 1, 1, "DCB", "DCB_UG");

		// Act
		final var exception = assertThrows(PatronTypeMappingNotFound.class,
			() -> singleValueFrom(patronTypeService.determinePatronType(
				supplyingHostLms.getCode(), requestingHostLms.getCode(), "1", null)));

		// Assert
		assertThat(exception, hasMessage(
			"No mapping found from ptype requesting-host-lms:1 to supplying-host-lms because No mapping found from ptype requesting-host-lms:1 to supplying-host-lms"));
	}

	@Test
	void shouldThrowExceptionWhenNoPatronTypeMappingFound() {
		// Arrange
		final var requestingHostLms = hostLmsFixture.createSierraHostLms("requesting-host-lms");
		final var supplyingHostLms = hostLmsFixture.createSierraHostLms("supplying-host-lms");

		// Act
		final var exception = assertThrows(PatronTypeMappingNotFound.class,
			() -> singleValueFrom(patronTypeService.determinePatronType(
				supplyingHostLms.getCode(), requestingHostLms.getCode(),"1", null)));

		// Assert
		assertThat(exception,
			hasMessage("No mapping found from ptype requesting-host-lms:1 to supplying-host-lms because Unable to map patronType requesting-host-lms:1 To DCB context"));
	}
}
