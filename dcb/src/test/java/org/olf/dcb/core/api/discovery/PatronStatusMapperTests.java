package org.olf.dcb.core.api.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.olf.dcb.core.api.serde.PatronRequestSummaryProjection;
import org.olf.dcb.core.model.PatronRequest;

/**
 * The patron-facing projection of a request. Two things matter here: patrons see a
 * status they can act on, and patrons do NOT see internal error text.
 */
class PatronStatusMapperTests {

	private static final String INTERNAL_ERROR =
		"Failed to place hold at sierra-prod-01.library.example.org: 500 {\"code\":132,\"name\":\"XCirc error\"}";

	private static PatronRequestSummaryProjection projection(String status, String errorMessage) {
		return new PatronRequestSummaryProjection(
			UUID.randomUUID(), status, "OUTCOME", "NEXT", 42L, errorMessage,
			"The Title", "PICKUP-CODE", "Pickup Library Name", "RET-STD",
			UUID.randomUUID(), Instant.now(), Instant.now());
	}

	/**
	 * error_message is determineMessage(throwable) from PatronRequestWorkflowService —
	 * raw exception text carrying LMS hostnames and API response bodies. It reached
	 * patrons through the old single enrich() path.
	 */
	@Test
	void thePatronShapeDropsInternalErrorText() {
		final var summary = PatronStatusMapper.enrichForPatron(projection("ERROR", INTERNAL_ERROR));

		assertNull(summary.errorMessage());
		// ...and the patron still learns what to do about it.
		assertEquals(PatronRequestDiscoveryStatus.ERROR_SCENARIO, summary.discoveryStatus());
		assertEquals("This request encountered an error. Please see a librarian",
			summary.statusDescription());
	}

	@Test
	void theStaffShapeKeepsInternalErrorText() {
		final var summary = PatronStatusMapper.enrichForStaff(projection("ERROR", INTERNAL_ERROR));

		assertEquals(INTERNAL_ERROR, summary.errorMessage());
	}

	@Test
	void theRawStateMachineValueSurvivesAlongsideThePatronFacingOne() {
		// Discovery services that want to do their own mapping are not forced into ours.
		final var summary = PatronStatusMapper.enrichForPatron(projection("READY_FOR_PICKUP", null));

		assertEquals("READY_FOR_PICKUP", summary.status());
		assertEquals(PatronRequestDiscoveryStatus.READY_FOR_PICKUP, summary.discoveryStatus());
		assertEquals("Ready for pickup!", summary.statusDescription());
		assertEquals("RET-STD", summary.activeWorkflow());
	}

	@Test
	void theLocationNameIsPreferredButTheCodeIsTheFallback() {
		assertEquals("Pickup Library Name",
			PatronStatusMapper.enrichForPatron(projection("LOANED", null)).pickupLocationName());

		final var unresolved = new PatronRequestSummaryProjection(
			UUID.randomUUID(), "LOANED", null, null, null, null, null,
			"PICKUP-CODE", null, null, null, null, null);

		assertEquals("PICKUP-CODE",
			PatronStatusMapper.enrichForPatron(unresolved).pickupLocationName());
	}

	/**
	 * AWAITING_RETURN_TO_SUPPLIER is a cancellation parked until the item is back at the supplier
	 * (DCB-2193), and it releases to CANCELLED. Showing it as COMPLETED would tell the patron
	 * "Returned and completed" about an item they never received, and would then flip backwards to
	 * CANCELLED when the park released.
	 */
	@Test
	void aCancellationWaitingOnTheItemStillReadsAsCancelled() {
		final var summary = PatronStatusMapper.enrichForPatron(
			projection("AWAITING_RETURN_TO_SUPPLIER", null));

		assertEquals(PatronRequestDiscoveryStatus.CANCELLED, summary.discoveryStatus());
		assertEquals("This request was cancelled.", summary.statusDescription());
	}

	/** A corrupt status_code is an error, not a new state to guess a meaning for. */
	@Test
	void anUnknownStatusBecomesTheErrorScenario() {
		final var summary = PatronStatusMapper.enrichForPatron(projection("NOT_A_REAL_STATUS", null));

		assertEquals(PatronRequestDiscoveryStatus.ERROR_SCENARIO, summary.discoveryStatus());
	}

	/**
	 * toDisplayStatus has no default branch so a new PatronRequest.Status breaks the
	 * build. This is the runtime counterpart: every status maps to something, and
	 * nothing maps to null prose a UI would render as "null".
	 */
	@ParameterizedTest
	@EnumSource(PatronRequest.Status.class)
	void everyInternalStatusHasPatronFacingProse(PatronRequest.Status status) {
		final var discoveryStatus = PatronStatusMapper.toDisplayStatus(status);

		assertFalse(discoveryStatus.getFriendlyDescription().isBlank(),
			status + " has no patron-facing description");
	}
}
