package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.items.Status;

@DcbTest
class ItemStatusMapperTests {
	@Inject
	private ItemStatusMapper mapper;

	@Inject
	private ReferenceValueMappingRepository referenceValueMappingRepository;

	@BeforeEach
	public void beforeEach() {
		new DataAccess().deleteAll(referenceValueMappingRepository.queryAll(),
			mapping -> referenceValueMappingRepository.delete(mapping.getId()));
	}

	@Test
	void statusIsAvailableWhenCodeIsHyphenAndDueDateIsNotPresent() {
		final var mappedStatus = mapStatus(new Status("-", "AVAILABLE", null));

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsAvailableWhenCodeIsHyphenAndDueDateIsBlank() {
		final var mappedStatus = mapStatus(new Status("-", "AVAILABLE", ""));

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsCheckedOutWhenCodeIsHyphenAndDueDateIsPresent() {
		final var mappedStatus = mapStatus(new Status("-", "AVAILABLE", "2023-04-22T15:55:13Z"));

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(CHECKED_OUT));
	}

	/**
	 * These codes come from the
	 * <a href="https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html#item%20STATUS">
	 * this documentation</a> of standard codes
	 */
	@ParameterizedTest
	@CsvSource({"m,MISSING", "!,ON HOLDSHELF", "$,BILLED PAID", "n,BILLED NOTPAID",
		"z,CL RETURNED", "o,LIB USE ONLY", "t,IN TRANSIT"})
	void statusIsUnavailableWhenCodeAnythingOtherThanHyphen(String code,
		String displayText) {

		final var mappedStatus = mapStatus(new Status(code, displayText, null));

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNAVAILABLE));
	}

	@Test
	void statusIsUnknownWhenCodeIsEmpty() {
		final var mappedStatus = mapStatus(new Status("", "", ""));

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Test
	void statusIsUnknownWhenSierraStatusIsNull() {
		final var mappedStatus = mapStatus(null);

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Test
	void statusCheckedOutIsMappedWhenValidMappingPresent() {
		// Arrange
		saveReferenceValueMapping("-", "AVAILABLE");

		// Act
		final var mappedStatus = mapStatus(new Status("-", "AVAILABLE", "2023-04-22T15:55:13Z"));

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(CHECKED_OUT));
	}

	@Test
	void statusAvailableIsMappedWhenValidMappingPresent() {
		// Arrange
		saveReferenceValueMapping("-", "AVAILABLE");

		// Act
		final var mappedStatus = mapStatus(new Status("-", "AVAILABLE", null));

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusUnavailableIsMappedWhenValidMappingPresent() {
		// Arrange
		saveReferenceValueMapping("/", "UNAVAILABLE");

		// Act
		final var mappedStatus = mapStatus(new Status("/", "UNAVAILABLE", null));

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNAVAILABLE));
	}

	@Test
	void statusIsNotMappedToInvalidEnum() {
		// Arrange
		saveReferenceValueMapping("?", "INVALID");

		// Act
		final var exception = assertThrows(IllegalArgumentException.class, () ->
			mapStatus(new Status("?", "INVALID", "2023-04-22T15:55:13Z")));

		// Assert
		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(),
			is("No enum constant org.olf.dcb.core.model.ItemStatusCode.INVALID"));
	}

	private ItemStatus mapStatus(Status status) {
		return mapper.mapStatus(status, "test1").block();
	}

	private void saveReferenceValueMapping(String fromValue, String toValue) {
		final var mapping = ReferenceValueMapping.builder()
			.id(UUID.randomUUID())
			.fromCategory("itemStatus")
			.fromContext("test1")
			.fromValue(fromValue)
			.toCategory("itemStatus")
			.toContext("DCB")
			.toValue(toValue)
			.reciprocal(true)
			.build();
		singleValueFrom(referenceValueMappingRepository.save(mapping));
	}
}
