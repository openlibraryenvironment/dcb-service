package org.olf.dcb.core.interaction.shared;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.shared.ItemStatusMapper.FallbackMapper;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.items.Status;

@DcbTest
class ItemStatusMapperTests {
	private static final String HOST_LMS_CODE = "test1";

	@Inject
	private ItemStatusMapper mapper;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Nested
	class ReferenceValueMappingTests {
		@Test
		void statusCheckedOutIsMappedWhenValidMappingPresent() {
			// Arrange
			defineStatusMapping("-", "AVAILABLE");

			// Act
			final var mappedStatus = mapStatus(new Status("-", "AVAILABLE", "2023-04-22T15:55:13Z"));

			// Assert
			assertThat(mappedStatus, is(notNullValue()));
			assertThat(mappedStatus.getCode(), is(CHECKED_OUT));
		}

		@Test
		void statusAvailableIsMappedWhenValidMappingPresent() {
			// Arrange
			defineStatusMapping("-", "AVAILABLE");

			// Act
			final var mappedStatus = mapStatus(new Status("-", "AVAILABLE", null));

			// Assert
			assertThat(mappedStatus, is(notNullValue()));
			assertThat(mappedStatus.getCode(), is(AVAILABLE));
		}

		@Test
		void statusUnavailableIsMappedWhenValidMappingPresent() {
			// Arrange
			defineStatusMapping("/", "UNAVAILABLE");

			// Act
			final var mappedStatus = mapStatus(new Status("/", "UNAVAILABLE", null));

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
				mapStatus(new Status("?", "INVALID", "2023-04-22T15:55:13Z")));

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
			final var mappedStatus = mapStatus(new Status("NOT_MATCHED", "", null));

			// Assert
			assertThat(mappedStatus, is(notNullValue()));
			assertThat(mappedStatus.getCode(), is(UNKNOWN));
		}

		private void defineStatusMapping(String fromValue, String toValue) {
			referenceValueMappingFixture.defineItemStatusMapping(HOST_LMS_CODE, fromValue, toValue);
		}
	}

	@Nullable
	private ItemStatus mapStatus(Status status) {
		return mapper.mapStatus(status, HOST_LMS_CODE, unknownStatusFallback())
			.block();
	}

	private static FallbackMapper unknownStatusFallback() {
		return statusCode -> UNKNOWN;
	}
}
