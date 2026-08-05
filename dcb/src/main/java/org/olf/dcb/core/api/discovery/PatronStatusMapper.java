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
	 * Turns what the query selected into what the API returns: adds the coarse
	 * patron-facing status and its prose, and falls back to the raw pickup location
	 * code when the code resolves to no known location.
	 * The raw DCB {@code status} is carried through untouched — discovery services
	 * that want to do their own mapping still get the real state machine value.
	 */
	public static PatronRequestSummary enrich(PatronRequestSummaryProjection projection) {
		final var discoveryStatus = toDisplayStatus(parseStatus(projection.status()));

		return new PatronRequestSummary(
			projection.id(),
			projection.status(),
			discoveryStatus,
			discoveryStatus.getFriendlyDescription(),
			projection.outcome(),
			projection.nextExpectedStatus(),
			projection.timeInState(),
			projection.errorMessage(),
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

			case CANCELLED -> PatronRequestDiscoveryStatus.CANCELLED;

			case ERROR -> PatronRequestDiscoveryStatus.ERROR_SCENARIO;

			case NO_ITEMS_SELECTABLE_AT_ANY_AGENCY -> PatronRequestDiscoveryStatus.COULD_NOT_SUPPLY;

			case HANDED_OFF_AS_LOCAL -> PatronRequestDiscoveryStatus.LOCAL;

			case NOT_SUPPLIED_CURRENT_SUPPLIER -> PatronRequestDiscoveryStatus.SEARCHING_FOR_NEW_SUPPLIER;
		};
	}
}
