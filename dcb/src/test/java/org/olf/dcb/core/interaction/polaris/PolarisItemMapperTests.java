package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.core.model.ItemStatusCode.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.test.DcbTest;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;

@DcbTest
class PolarisItemMapperTests {
	/**
	 * The codes used here come from the descriptions from
	 * <a href="https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/itemstatuses/get_item_statuses">this API</a>
	 */

	private static final String HOST_LMS_CODE = "polaris-host-lms";

	@Inject
	private PolarisItemMapper mapper;

	@Test
	void statusIsAvailableWhenCodeIsIn() {
		final var mappedStatus = mapPolarisStatus("In");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"Bindery",
		"Claim Missing Parts",
		"Claim Never Had",
		"Claim Returned",
		"EContent External Loan",
		"Held",
		"In-Process",
		"In-Repair",
		"In-Transit",
		"Lost",
		"Missing",
		"Non-circulating",
		"On-Order",
		"Out-ILL",
		"Returned-ILL",
		"Routed",
		"Shelving",
		"Transferred",
		"Unavailable",
		"Withdrawn"
	})
	void statusIsUnavailableWhenCodeAnythingElse(String statusCode) {
		final var mappedStatus = mapPolarisStatus(statusCode);

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNAVAILABLE));
	}

	@Test
	void statusIsUnknownWhenCodeIsNull() {
		final var mappedStatus = mapPolarisStatus(null);

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Test
	void statusIsUnknownWhenCodeIsEmptyString() {
		final var mappedStatus = mapPolarisStatus("");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(UNKNOWN));
	}

	@Test
	void statusIsCheckedOutWhenCodeIsOut() {
		final var mappedStatus = mapPolarisStatus("Out");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(CHECKED_OUT));
	}

	@Nullable
	private ItemStatus mapPolarisStatus(String statusCode) {
		return mapper.mapStatus(statusCode, HOST_LMS_CODE)
			.block();
	}
}
