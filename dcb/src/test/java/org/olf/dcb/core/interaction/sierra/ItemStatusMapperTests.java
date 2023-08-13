package org.olf.dcb.core.interaction.sierra;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.olf.dcb.core.interaction.sierra.SierraItemStatusMapper;
import org.olf.dcb.core.model.ReferenceValueMapping;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.olf.dcb.test.DataAccess;
import org.olf.dcb.test.DcbTest;

import services.k_int.interaction.sierra.items.Status;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.ItemStatusCode.*;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

@DcbTest
class ItemStatusMapperTests {
	@Inject
	private SierraItemStatusMapper mapper;

	@Inject
	private ReferenceValueMappingRepository referenceValueMappingRepository;

	@BeforeEach
	public void beforeEach() {
		new DataAccess().deleteAll(referenceValueMappingRepository.findAll(),
			mapping -> referenceValueMappingRepository.delete(mapping.getId()));
	}

	@Test
	void statusIsAvailableWhenCodeIsHyphenAndDueDateIsNotPresent() {
		final var mappedStatus = mapper.mapStatus(
			new Status("-", "AVAILABLE", null), "test1").block();

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsAvailableWhenCodeIsHyphenAndDueDateIsBlank() {
		final var mappedStatus = mapper.mapStatus(
			new Status("-", "AVAILABLE", ""), "test1").block();

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsCheckedOutWhenCodeIsHyphenAndDueDateIsPresent() {
		final var mappedStatus = mapper.mapStatus(
			new Status("-", "AVAILABLE", "2023-04-22T15:55:13Z"), "test1").block();

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

		final var mappedStatus = mapper.mapStatus(new Status(code, displayText, null), "test1").block();

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNAVAILABLE));
	}

	@Test
	void statusIsUnknownWhenCodeIsEmpty() {
		final var mappedStatus = mapper.mapStatus(
			new Status("", "", ""), "test1").block();

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Test
	void statusIsUnknownWhenSierraStatusIsNull() {
		final var mappedStatus = mapper.mapStatus(null, "test1").block();

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Test
	void statusCheckedOutIsMappedWhenValidMappingPresent() {
		// Arrange
		saveReferenceValueMapping("-", "AVAILABLE");

		// Act
		final var mappedStatus = mapper.mapStatus(
			new Status("-", "AVAILABLE", "2023-04-22T15:55:13Z"), "test1").block();

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(CHECKED_OUT));
	}

	@Test
	void statusAvailableIsMappedWhenValidMappingPresent() {
		// Arrange
		saveReferenceValueMapping("-", "AVAILABLE");

		// Act
		final var mappedStatus = mapper.mapStatus(
			new Status("-", "AVAILABLE", null), "test1").block();

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusUnavailableIsMappedWhenValidMappingPresent() {
		// Arrange
		saveReferenceValueMapping("/", "UNAVAILABLE");

		// Act
		final var mappedStatus = mapper.mapStatus(
			new Status("/", "UNAVAILABLE", null), "test1").block();

		// Assert
		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNAVAILABLE));
	}

	@Test
	void statusIsNotMappedToInvalidEnum() {
		// Arrange
		saveReferenceValueMapping("?", "INVALID");

		// Act
		final var exception = assertThrows(IllegalArgumentException.class, () -> mapper.mapStatus(
				new Status("?", "INVALID", "2023-04-22T15:55:13Z"), "test1").block());

		// Assert
		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(),
			is("No enum constant org.olf.dcb.core.model.ItemStatusCode.INVALID"));
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
