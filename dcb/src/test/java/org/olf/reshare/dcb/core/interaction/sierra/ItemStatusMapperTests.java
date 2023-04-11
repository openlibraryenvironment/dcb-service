package org.olf.reshare.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.UNKNOWN;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import services.k_int.interaction.sierra.items.Status;

class ItemStatusMapperTests {
	final ItemStatusMapper mapper = new ItemStatusMapper();

	@Test
	void statusIsAvailableWhenCodeIsHyphenAndDueDateIsNotPresent() {
		final var mappedStatus = mapper.mapStatus(
			new Status("-", "AVAILABLE", null));

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsAvailableWhenCodeIsHyphenAndDueDateIsBlank() {
		final var mappedStatus = mapper.mapStatus(
			new Status("-", "AVAILABLE", ""));

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsCheckedOutWhenCodeIsHyphenAndDueDateIsPresent() {
		final var mappedStatus = mapper.mapStatus(
			new Status("-", "AVAILABLE", "2023-04-22T15:55:13Z"));

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

		final var mappedStatus = mapper.mapStatus(new Status(code, displayText, null));

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNAVAILABLE));
	}

	@Test
	void statusIsUnknownWhenCodeIsEmpty() {
		final var mappedStatus = mapper.mapStatus(
			new Status("", "", ""));

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Test
	void statusIsUnknownWhenSierraStatusIsNull() {
		final var mappedStatus = mapper.mapStatus(null);

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}
}
