package org.olf.dcb.request.resolution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.workflow.PresentableItem;

import reactor.core.publisher.Mono;

/**
 * What the audit says about how resolution chose, or why it chose nothing. Three empty lists
 * cannot distinguish a Host LMS that answered with no items from one that could not be reached,
 * or say which of six filters emptied the list.
 */
@TestInstance(PER_CLASS)
class ResolutionEvidenceTests {
	private PatronRequestAuditService auditService;
	private ResolutionAuditService resolutionAuditService;

	@BeforeEach
	void beforeEach() {
		auditService = mock(PatronRequestAuditService.class);
		when(auditService.addAuditEntry(any(PatronRequest.class), any(String.class), any()))
			.thenReturn(Mono.empty());

		resolutionAuditService = new ResolutionAuditService(auditService);
	}

	@Test
	void shouldRecordWhatAvailabilityCouldNotAnswer() {
		final var resolution = Resolution.forParameters(parameters())
			.trackAllItems(List.of())
			.trackAvailabilityErrors(List.of(AvailabilityReport.Error.builder()
				.message("ALMA-LIB did not answer within PT30S for bib 991234")
				.build()));

		final var auditData = auditDataFor(resolution);

		assertThat(auditData, hasKey("availabilityErrors"));
		assertThat((List<?>) auditData.get("availabilityErrors"),
			contains("ALMA-LIB did not answer within PT30S for bib 991234"));
	}

	@Test
	void shouldRecordWhichFilterRemovedAnItemAndWhereItWas() {
		final var excluded = item()
			.toBuilder()
			.decisionLogEntry("Excluded by IsRequestableItemFilter")
			.build();

		final var resolution = Resolution.forParameters(parameters())
			.trackAllItems(List.of(excluded))
			.trackFilteredItems(List.of())
			.trackExcludedItems(List.of(excluded));

		final var auditData = auditDataFor(resolution);

		final var excludedItems = (List<?>) auditData.get("excludedItems");
		final var presented = (PresentableItem) excludedItems.get(0);

		assertThat(presented.getDecisionLog(), containsString("IsRequestableItemFilter"));
		// The location is what a location-to-agency mapping is keyed on
		assertThat(presented.getLocationCode(), is("MAIN"));
	}

	@Test
	void shouldKeepTheEvidenceWhenAnotherItemIsSelected() {
		final var chosen = item();

		final var excluded = item()
			.toBuilder()
			.localId("item-2")
			.decisionLogEntry("Excluded by IsRequestableItemFilter")
			.build();

		final var resolution = Resolution.forParameters(parameters())
			.trackAllItems(List.of(chosen, excluded))
			.trackAvailabilityErrors(List.of(AvailabilityReport.Error.builder()
				.message("OTHER-LIB did not answer within PT30S for bib 991234")
				.build()))
			.trackExcludedItems(List.of(excluded))
			.trackFilteredItems(List.of(chosen))
			.trackSortedItems(List.of(chosen))
			.selectItem(chosen);

		final var auditData = auditDataFor(resolution);

		assertThat(auditData, hasKey("selectedItem"));
		assertThat((List<?>) auditData.get("excludedItems"), hasSize(1));
		assertThat((List<?>) auditData.get("availabilityErrors"),
			contains("OTHER-LIB did not answer within PT30S for bib 991234"));
	}

	@Test
	void shouldStillSayItCouldNotSelectAnItem() {
		auditDataFor(Resolution.forParameters(parameters()));

		verify(auditService).addAuditEntry(any(PatronRequest.class),
			eq("Resolution could not select an item"), any());
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> auditDataFor(Resolution resolution) {
		final var patronRequest = PatronRequest.builder().id(UUID.randomUUID()).build();

		resolutionAuditService.auditResolution(resolution, patronRequest, "Resolution").block();

		final var captor = ArgumentCaptor.forClass(Map.class);

		verify(auditService).addAuditEntry(any(PatronRequest.class), any(String.class), captor.capture());

		return (Map<String, Object>) captor.getValue();
	}

	private static ResolutionParameters parameters() {
		return ResolutionParameters.builder()
			.bibClusterId(UUID.randomUUID())
			.borrowingAgencyCode("BORROWER")
			.pickupAgencyCode("BORROWER")
			.build();
	}

	private static Item item() {
		return Item.builder()
			.localId("item-1")
			.status(new ItemStatus(ItemStatusCode.UNKNOWN))
			.location(Location.builder().code("MAIN").build())
			.build();
	}
}
