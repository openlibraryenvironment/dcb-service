package org.olf.dcb.core.api.discovery;

import org.olf.dcb.core.api.serde.PatronRequestSummary;
import org.olf.dcb.core.api.serde.PatronRequestSummaryProjection;
import org.olf.dcb.core.model.PatronRequest;

/**
 * The single enrichment path for the patron-facing request summary. Every endpoint
 * returning a {@link PatronRequestSummary} builds it through {@link #enrich} so that
 * discovery services see one consistent shape, whichever endpoint they call.
 */
public class PatronStatusMapper {

	private PatronStatusMapper() {}

	/**
	 * The PATRON-facing shape.
	 *
	 * {@code errorMessage} is deliberately DROPPED. It is populated from
	 * {@code determineMessage(throwable)} in PatronRequestWorkflowService — raw
	 * internal exception text, truncated to 255 chars, routinely carrying LMS
	 * hostnames, API response bodies and internal class names. A patron cannot act on
	 * any of it; {@code discoveryStatus == ERROR_SCENARIO} plus its prose ("please see
	 * a librarian") is the whole useful payload.
	 */
	public static PatronRequestSummary enrichForPatron(PatronRequestSummaryProjection projection) {
		return build(projection, null);
	}

	/**
	 * The STAFF shape. Same record, {@code errorMessage} retained — a librarian can
	 * act on it, and the endpoints using this are gated on staff roles.
	 */
	public static PatronRequestSummary enrichForStaff(PatronRequestSummaryProjection projection) {
		return build(projection, projection.errorMessage());
	}

	/**
	 * Turns what the query selected into what the API returns: adds the coarse
	 * patron-facing status and its prose, and falls back to the raw pickup location
	 * code when the code resolves to no known location.
	 * The raw DCB {@code status} is carried through untouched — discovery services
	 * that want to do their own mapping still get the real state machine value.
	 */
	private static PatronRequestSummary build(PatronRequestSummaryProjection projection,
		String errorMessage) {

		final var discoveryStatus = toDisplayStatus(parseStatus(projection.status()));

		return new PatronRequestSummary(
			projection.id(),
			projection.status(),
			discoveryStatus,
			discoveryStatus.getFriendlyDescription(),
			projection.outcome(),
			projection.nextExpectedStatus(),
			projection.timeInState(),
			errorMessage,
			projection.title(),
			projection.pickupLocationCode(),
			projection.pickupLocationName() != null
				? projection.pickupLocationName()
				: projection.pickupLocationCode(),
			projection.activeWorkflow(),
			projection.bibClusterId(),
			projection.dateCreated(),
			projection.dateUpdated());
	}

	// A status_code the enum does not know is a corrupt row, not a new state: surface it as an error
	// rather than guessing a friendlier meaning for it.
	private static PatronRequest.Status parseStatus(String rawStatus) {
		if (rawStatus == null) {
			return null;
		}

		try {
			return PatronRequest.Status.valueOf(rawStatus);
		} catch (IllegalArgumentException e) {
			return PatronRequest.Status.ERROR;
		}
	}

	public static PatronRequestDiscoveryStatus toDisplayStatus(PatronRequest.Status internalStatus) {
		if (internalStatus == null) {
			return PatronRequestDiscoveryStatus.REQUEST_RECEIVED;
		}

		// Deliberately exhaustive, with no default: a new PatronRequest.Status must break the build
		// here and force a conscious decision about what to tell the patron.
		return switch (internalStatus) {
			case SUBMITTED_TO_DCB,
					 PATRON_VERIFIED,
					 RESOLVED,
					 REQUEST_PLACED_AT_SUPPLYING_AGENCY,
					 CONFIRMED
				-> PatronRequestDiscoveryStatus.REQUEST_RECEIVED;

			case REQUEST_PLACED_AT_BORROWING_AGENCY, REQUEST_PLACED_AT_PICKUP_AGENCY
				-> PatronRequestDiscoveryStatus.REQUEST_PLACED;

			case PICKUP_TRANSIT, RECEIVED_AT_PICKUP -> PatronRequestDiscoveryStatus.IN_TRANSIT;

			case READY_FOR_PICKUP -> PatronRequestDiscoveryStatus.READY_FOR_PICKUP;

			case LOANED -> PatronRequestDiscoveryStatus.CHECKED_OUT;

			// For a patron, RETURN_TRANSIT is completed: there isn't anything more they need to do
			case COMPLETED,
					 FINALISED,
					 RETURN_TRANSIT,
					 ARCHIVED
				-> PatronRequestDiscoveryStatus.COMPLETED;

			// AWAITING_RETURN_TO_SUPPLIER is a cancellation parked until the item is back with the
			// supplier (DCB-2193), and it releases to CANCELLED. Nothing was supplied and nothing
			// was returned by the patron - who never received the item - so COMPLETED would both
			// read as false ("Returned and completed") and later flip backwards to CANCELLED.
			case CANCELLED, AWAITING_RETURN_TO_SUPPLIER -> PatronRequestDiscoveryStatus.CANCELLED;

			case ERROR -> PatronRequestDiscoveryStatus.ERROR_SCENARIO;

			case NO_ITEMS_SELECTABLE_AT_ANY_AGENCY -> PatronRequestDiscoveryStatus.COULD_NOT_SUPPLY;

			case HANDED_OFF_AS_LOCAL -> PatronRequestDiscoveryStatus.LOCAL;

			case NOT_SUPPLIED_CURRENT_SUPPLIER -> PatronRequestDiscoveryStatus.SEARCHING_FOR_NEW_SUPPLIER;
		};
	}
}
