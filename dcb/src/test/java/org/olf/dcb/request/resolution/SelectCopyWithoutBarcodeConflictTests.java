package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;

import reactor.core.publisher.Mono;

/** No database: which copy is chosen once the barcode check has answered. */
class SelectCopyWithoutBarcodeConflictTests {
	private final VirtualItemBarcodeCheck check = mock(VirtualItemBarcodeCheck.class);
	private final LiveAvailabilityService liveAvailabilityService = mock(LiveAvailabilityService.class);
	private final ManualSelection manualSelection = mock(ManualSelection.class);
	private final AllItemFilters itemFilters = mock(AllItemFilters.class);

	private final PatronRequestResolutionService service = new PatronRequestResolutionService(
		liveAvailabilityService, mock(RequestWorkflowContextHelper.class), null, List.of(),
		manualSelection, itemFilters, check, Duration.ofSeconds(30));

	@Test
	void shouldChooseTheNextCopyWhenTheFirstCopysBarcodeIsAlreadyThere() {
		final var first = copy("first");
		final var second = copy("second");

		alreadyThere(first, true);
		alreadyThere(second, false);

		assertThat(chosenFrom(List.of(first, second)), is(second));
	}

	@Test
	void shouldChooseNoCopyWhenEveryCopysBarcodeIsAlreadyThere() {
		final var first = copy("first");
		final var second = copy("second");

		alreadyThere(first, true);
		alreadyThere(second, true);

		assertThat(chosenFrom(List.of(first, second)), is(nullValue()));
	}

	@Test
	void shouldChooseTheSixthCopyUncheckedWhenTheFirstFiveAreAlreadyThere() {
		final var copies = IntStream.rangeClosed(1, 6).mapToObj(n -> copy("copy-" + n)).toList();

		final var checked = new ArrayList<Item>();
		when(check.barcodeAlreadyPresent(any(), any())).thenAnswer(invocation -> {
			checked.add(invocation.getArgument(0));
			return Mono.just(true);
		});

		assertThat(chosenFrom(copies), is(copies.get(5)));
		assertThat(checked, is(copies.subList(0, 5)));
	}

	@Test
	void shouldNotChooseAManuallySelectedCopyWhoseBarcodeIsAlreadyThere() {
		final var selected = copy("selected");

		alreadyThere(selected, true);

		assertThat(manuallyChosen(selected), is(nullValue()));
	}

	@Test
	void shouldChooseAManuallySelectedCopyWhoseBarcodeIsNotThere() {
		final var selected = copy("selected");

		alreadyThere(selected, false);

		assertThat(manuallyChosen(selected), is(selected));
	}

	private void alreadyThere(Item copy, boolean present) {
		when(check.barcodeAlreadyPresent(eq(copy), any())).thenReturn(Mono.just(present));
	}

	private Item chosenFrom(List<Item> copies) {
		final var resolution = Resolution.forParameters(ResolutionParameters.builder().build())
			.trackSortedItems(copies);

		return service.firstRequestableItem(resolution).block().getChosenItem();
	}

	private Item manuallyChosen(Item selected) {
		when(liveAvailabilityService.checkAvailability(any(), any()))
			.thenReturn(Mono.just(AvailabilityReport.ofItems(List.of(selected))));
		when(itemFilters.partition(any(), any()))
			.thenReturn(Mono.just(new AllItemFilters.FilterOutcome(List.of(selected), List.of())));
		when(manualSelection.chooseItem(any(), any())).thenReturn(selected);

		final var parameters = ResolutionParameters.builder()
			.bibClusterId(randomUUID())
			.borrowingAgencyCode("borrowing-agency")
			.pickupAgencyCode("pickup-agency")
			.manualItemSelection(ManualItemSelection.builder()
				.isManuallySelected(true)
				.localItemId(selected.getLocalId())
				.build())
			.build();

		return service.resolve(parameters).block().getChosenItem();
	}

	private static Item copy(String localId) {
		return Item.builder().localId(localId).build();
	}
}
