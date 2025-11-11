package org.olf.dcb.core.interaction.polaris;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.DerivedLoanPolicy.GENERAL;
import static org.olf.dcb.core.model.DerivedLoanPolicy.SHORT_LOAN;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasDerivedLoanPolicy;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.core.interaction.polaris.PAPIClient.ItemGetRow;
import org.olf.dcb.core.interaction.polaris.PolarisConfig.ItemConfig;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@DcbTest
@TestInstance(PER_CLASS)
@Slf4j
class PolarisItemMapperTests {
	/**
	 * The codes used here come from the descriptions from
	 * <a href="https://stlouis-training.polarislibrary.com/polaris.applicationservices/help/itemstatuses/get_item_statuses">this API</a>
	 */

	private static final String HOST_LMS_CODE = "polaris-item-mapper-host-lms";
	private static final String DEFAULT_AGENCY_CODE = "default-agency";

	@Inject
	private PolarisItemMapper mapper;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private AgencyFixture agencyFixture;

	@BeforeAll
	void beforeAll() {
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		final var polarisHostLms = hostLmsFixture.createPolarisHostLms(
			HOST_LMS_CODE, "", "", "", "", "",
			"", DEFAULT_AGENCY_CODE);

		agencyFixture.defineAgency(DEFAULT_AGENCY_CODE, "Default Agency", polarisHostLms);
	}

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

	@Test
	void shouldMapLocationToPolicyWhenMappingDefined() {
		// Arrange
		final var locationName = "Mapped Location";

		final var expectedPolicy = SHORT_LOAN;

		final var config = locationToPolicyMapConfig(
			Map.of(locationName, expectedPolicy.name()));

		// Act
		final var polarisItem = ItemGetRow.builder()
			.ShelfLocation(locationName)
			.build();

		final var item = mapItem(polarisItem, config);

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasDerivedLoanPolicy(expectedPolicy)
		));
	}

	@Test
	void shouldDefaultPolicyWhenNoMappingDefined() {
		// Arrange
		final var config = locationToPolicyMapConfig(Map.of());

		// Act
		final var polarisItem = ItemGetRow.builder()
			.ShelfLocation("Unmapped Location")
			.build();

		final var item = mapItem(polarisItem, config);

		// Assert
		assertThat(item, allOf(
			notNullValue(),
			hasDerivedLoanPolicy(GENERAL)
		));
	}

	@Nullable
	private ItemStatus mapPolarisStatus(String statusCode) {
		return singleValueFrom(mapper.mapStatus(statusCode, HOST_LMS_CODE));
	}

	private Item mapItem(ItemGetRow polarisItem, PolarisConfig config) {
		return singleValueFrom(mapper.mapItemGetRowToItem(polarisItem,
			HOST_LMS_CODE, "567346463", Optional.empty(), config));
	}

	private static PolarisConfig locationToPolicyMapConfig(Map<String, String> mapping) {
		return PolarisConfig.builder()
			.shelfLocationPolicyMap(mapping )
			.item(ItemConfig.builder()
				// This is duplicated here due to the builder using a constructor that overwrites the field default
				.itemAgencyResolutionMethod("Legacy")
				.build())
			.build();
	}
}
