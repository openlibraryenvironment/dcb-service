package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@DcbTest
public class SierraItemStatusMappingTests {
	private static final String HOST_LMS_CODE = "sierra-host-lms";

	@Inject
	private SierraItemMapper mapper;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void statusIsAvailableWhenCodeIsHyphenAndDueDateIsNotPresent() {
		final var mappedStatus = mapStatus("-", null);

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsAvailableWhenCodeIsHyphenAndDueDateIsBlank() {
		final var mappedStatus = mapStatus("-", "");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsCheckedOutWhenCodeIsHyphenAndDueDateIsPresent() {
		final var mappedStatus = mapStatus("-", "2023-04-22T15:55:13Z");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(CHECKED_OUT));
	}

	/**
	 * These codes come from the
	 * <a href="https://documentation.iii.com/sierrahelp/Content/sril/sril_records_fixed_field_types_item.html#item%20STATUS">
	 * this documentation</a> of standard codes
	 */
	@ParameterizedTest
	@ValueSource(strings = {"m", "!", "$", "n", "z", "o", "t"})
	void statusIsUnavailableWhenCodeAnythingOtherThanHyphen(String code) {
		final var mappedStatus = mapStatus(code, null);

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNAVAILABLE));
	}

	@Test
	void statusIsUnknownWhenCodeIsEmpty() {
		final var mappedStatus = mapStatus("", "");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Test
	void statusIsUnknownWhenSierraStatusIsNull() {
		final var mappedStatus = mapStatus(null, null);

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Nullable
	private ItemStatus mapStatus(String statusCode, String dueDate) {
		return mapper.mapStatus(statusCode, dueDate);
	}
}
