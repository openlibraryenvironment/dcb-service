package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.core.interaction.polaris.ItemMapper.polarisFallback;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.items.Status;

@DcbTest
class PolarisItemStatusMapperTests {
	/**
	 * The codes used here come from the descriptions from
	 * <a href="https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/itemstatuses/get_item_statuses">this API</a>
	 */

	private static final String HOST_LMS_CODE = "polaris-host-lms";

	@Inject
	private ItemStatusMapper mapper;

	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	@BeforeEach
	public void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void statusIsAvailableWhenCodeIsIn() {
		final var mappedStatus = mapPolarisStatus("In");

		assertThat(mappedStatus, is(notNullValue()));
		assertThat(mappedStatus.getCode(), is(AVAILABLE));
	}

	@Test
	void statusIsAvailableWhenCodeIsInEvenWhenDueDateIsProvided() {
		final var mappedStatus = mapper.mapStatus(Status.builder()
					.code("In")
					.duedate("2023-10-05T23:59:59Z")
					.build(),
				HOST_LMS_CODE, false, polarisFallback())
			.block();

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
		"Out",
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

	@Nullable
	private ItemStatus mapPolarisStatus(String statusCode) {
		return mapper.mapStatus(Status.builder()
					.code(statusCode)
					.build(),
				HOST_LMS_CODE, true, polarisFallback())
			.block();
	}
}
