package org.olf.dcb.request.resolution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.core.model.Item;

import lombok.Builder;

class RequestedVolumeItemFilterTests {
	private final RequestedVolumeItemFilter filter = new RequestedVolumeItemFilter();

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "  " })
	void shouldIncludeEveryItemWhenNoVolumeIsRequested(String requestedVolume) {
		final var parameters = requestingVolume(requestedVolume);

		assertThat(included(item("v.1", "v1"), parameters), is(true));
		assertThat(included(item("v.2", "v2"), parameters), is(true));
		assertThat(included(item(null, null), parameters), is(true));
	}

	@Test
	void shouldIncludeOnlyItemsOfTheRequestedVolume() {
		final var parameters = requestingVolume("v.2");

		assertThat(included(item("v.2", "v2"), parameters), is(true));
		assertThat(included(item("v.1", "v1"), parameters), is(false));
		assertThat(included(item("v.2 pt.1", "v2p1"), parameters), is(false));
		assertThat(included(item("v.12", "v12"), parameters), is(false));
	}

	@Test
	void shouldExcludeItemWithoutVolumeStatementWhenVolumeIsRequested() {
		final var parameters = requestingVolume("v.2");

		assertThat(included(item(null, null), parameters), is(false));
		assertThat(included(item("", ""), parameters), is(false));
	}

	@Test
	void shouldMatchTheSameVolumeWrittenInEachSystemsStyle() {
		final var parameters = requestingVolume("v.2");

		assertThat("Polaris style", included(item("Vol [ 2 ]", null), parameters), is(true));
		assertThat("Sierra style", included(item("Vol. 2", null), parameters), is(true));
		assertThat("Already parsed", included(item(null, "v2"), parameters), is(true));
	}

	@Test
	void shouldIncludeACopyThatAddsTheYearOfTheRequestedVolume() {
		final var parameters = requestingVolume("v.22");

		assertThat(included(item("Vol [ 22].(1990)", "v22y1990"), parameters), is(true));
		assertThat(included(item("Vol [ 23].(1991)", "v23y1991"), parameters), is(false));
	}

	@Test
	void shouldPreferParsedStatementOverRawStatement() {
		final var parameters = requestingVolume("v.2");

		assertThat(included(item("unparseable 2", "v2"), parameters), is(true));
		assertThat(included(item("v.2", "v1"), parameters), is(false));
	}

	@Test
	void shouldIncludeManuallySelectedItemWhateverItsVolume() {
		final var resolution = Resolution.forParameters(ResolutionParameters.builder()
			.requestedVolumeDesignation("v.2")
			.manualItemSelection(ManualItemSelection.builder()
				.isManuallySelected(true)
				.localItemId("chosen-item")
				.hostLmsCode("supplying-system")
				.agencyCode("supplying-agency")
				.build())
			.build());

		assertThat(included(item("v.1", "v1"), resolution), is(true));
		assertThat(included(item(null, null), resolution), is(true));
	}

	@Test
	void shouldApplyRequestedVolumeFromResolutionWithoutManualSelection() {
		final var resolution = Resolution.forParameters(ResolutionParameters.builder()
			.requestedVolumeDesignation("v.2")
			.build());

		assertThat(included(item("v.2", "v2"), resolution), is(true));
		assertThat(included(item("v.1", "v1"), resolution), is(false));
	}

	private boolean included(Item item, ItemFilterParameters parameters) {
		return singleValueFrom(filter.filterItem(parameters).apply(item));
	}

	private static Parameters requestingVolume(String requestedVolume) {
		return Parameters.builder()
			.requestedVolumeDesignation(requestedVolume)
			.build();
	}

	private static Item item(String rawVolumeStatement, String parsedVolumeStatement) {
		return Item.builder()
			.rawVolumeStatement(rawVolumeStatement)
			.parsedVolumeStatement(parsedVolumeStatement)
			.build();
	}

	@Builder
	record Parameters(List<String> excludedSupplyingAgencyCodes,
		String borrowingAgencyCode, String borrowingHostLmsCode, Boolean isExpeditedCheckout,
		String pickupAgencyCode, String requestedVolumeDesignation) implements ItemFilterParameters { }
}
