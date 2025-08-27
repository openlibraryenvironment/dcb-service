package org.olf.dcb.request.resolution;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;

import lombok.Builder;
import lombok.Value;

class AgencyExclusionItemFilterTests {
	private final AgencyExclusionItemFilter filter = new AgencyExclusionItemFilter();

	@Test
	void shouldIncludeItemFromOtherAgency() {
		// Arrange
		final var parameters = excludedAgencyParameters(List.of("excludedAgencyCode"));

		final var item = itemFromAgency("includedAgencyCode");

		// Act / Assert
		assertThat("Item from not excluded agency should not be filtered out",
			filter.filterItem(item, parameters), is(true));
	}

	@Test
	void shouldExcludeItemFromOnlyExcludedAgency() {
		// Arrange
		final var excludedAgencyCode = "excludedAgencyCode";

		final var parameters = excludedAgencyParameters(List.of(excludedAgencyCode));

		final var item = itemFromAgency(excludedAgencyCode);

		// Act / Assert
		assertThat("Item from only excluded agency should be filtered out",
			filter.filterItem(item, parameters), is(false));
	}

	@Test
	void shouldExcludeItemFromBothExcludedAgencies() {
		// Arrange
		final var firstExcludedAgencyCode = "firstExcludedAgencyCode";
		final var secondExcludedAgencyCode = "secondExcludedAgencyCode";

		final var parameters = excludedAgencyParameters(List.of(firstExcludedAgencyCode, secondExcludedAgencyCode));

		final var itemFromFirstExcludedAgency = itemFromAgency(firstExcludedAgencyCode);
		final var itemFromSecondExcludedAgency = itemFromAgency(secondExcludedAgencyCode);

		// Act / Assert
		assertThat("Item from first excluded agency should be filtered out",
			filter.filterItem(itemFromFirstExcludedAgency, parameters), is(false));

		assertThat("Item from second excluded agency should be filtered out",
			filter.filterItem(itemFromSecondExcludedAgency, parameters), is(false));
	}

	@Test
	void shouldTolerateDuplicateExcludedAgencyCodes() {
		// Arrange
		final var excludedAgencyCode = "excludedAgencyCode";

		final var parameters = excludedAgencyParameters(List.of(excludedAgencyCode, excludedAgencyCode));

		final var item = itemFromAgency(excludedAgencyCode);

		// Act / Assert
		assertThat("Item from only excluded agency should be filtered out",
			filter.filterItem(item, parameters), is(false));
	}

	private static Parameters excludedAgencyParameters(List<String> excludedAgencyCodes) {
		return Parameters.builder()
			.excludedAgencyCodes(excludedAgencyCodes)
			.build();
	}

	private static Item itemFromAgency(String agencyCode) {
		return Item.builder()
			.agency(DataAgency.builder()
				.code(agencyCode)
				.build())
			.build();
	}

	@Builder
	@Value
	static class Parameters implements ItemFilterParameters {
		List<String> excludedAgencyCodes;
		String borrowingAgencyCode;
		String borrowingHostLmsCode;
	}
}
