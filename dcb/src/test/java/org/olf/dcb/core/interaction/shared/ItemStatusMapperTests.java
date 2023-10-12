package org.olf.dcb.core.interaction.shared;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper.unknownStatusFallback;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;

@DcbTest
class ItemStatusMapperTests {
	private static final String HOST_LMS_CODE = "any-host-lms";

	@Inject
	private ItemStatusMapper mapper;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void statusCheckedOutIsMappedWhenValidMappingPresentAndDueDateIsPresent() {
		// Arrange
		defineStatusMapping("-", "AVAILABLE");

		// Act
		final var mappedStatus = mapStatus("-", "2023-04-22T15:55:13Z");

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(CHECKED_OUT));
	}

	@Test
	void statusAvailableIsMappedWhenValidMappingPresentAndNoDueDateIsPresent() {
		// Arrange
		defineStatusMapping("-", "AVAILABLE");

		// Act
		final var mappedStatus = mapStatus("-", null);

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusUnavailableIsMappedWhenValidMappingPresent() {
		// Arrange
		defineStatusMapping("/", "UNAVAILABLE");

		// Act
		final var mappedStatus = mapStatus("/", null);

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNAVAILABLE));
	}

	@Test
	void statusIsNotMappedToInvalidEnum() {
		// Arrange
		defineStatusMapping("?", "INVALID");

		// Act
		final var exception = assertThrows(IllegalArgumentException.class, () ->
			mapStatus("?", "2023-04-22T15:55:13Z"));

		// Assert
		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(),
			is("No enum constant org.olf.dcb.core.model.ItemStatusCode.INVALID"));
	}

	@Test
	void statusIsUnknownWhenNoMatchingMappingIsDefined() {
		// Arrange
		defineStatusMapping("?", "INVALID");

		// Act
		final var mappedStatus = mapStatus("NOT_MATCHED", null);

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	private void defineStatusMapping(String fromValue, String toValue) {
		referenceValueMappingFixture.defineItemStatusMapping(HOST_LMS_CODE, fromValue, toValue);
	}

	@Nullable
	private ItemStatus mapStatus(String statusCode, String dueDate) {
		return mapper.mapStatus(statusCode, dueDate, HOST_LMS_CODE, true, unknownStatusFallback())
			.block();
	}
}
