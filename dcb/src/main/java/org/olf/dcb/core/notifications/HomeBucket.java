package org.olf.dcb.core.notifications;

import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.READY_FOR_PICKUP;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_PICKUP_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RETURN_TRANSIT;

import java.util.Set;

import org.olf.dcb.core.model.PatronRequest;

/**
 * The Home counts a notification can be about, named and filtered as DCB Admin for Libraries'
 * Home tiles are (its src/helpers/home.ts), so the bell's link opens the same grid view. A
 * bucket with a window counts requests updated within the last day; the others count what is
 * waiting now.
 */
public enum HomeBucket {
	READY_FOR_PICKUP_BUCKET("readyForPickup", StaffNotificationKind.REQUESTS_NEED_ACTION, Scope.BORROWED,
		Set.of(READY_FOR_PICKUP), false),
	NOT_YET_SENT("notYetSent", StaffNotificationKind.REQUESTS_NEED_ACTION, Scope.SUPPLIED,
		Set.of(REQUEST_PLACED_AT_SUPPLYING_AGENCY, CONFIRMED, REQUEST_PLACED_AT_BORROWING_AGENCY,
			REQUEST_PLACED_AT_PICKUP_AGENCY), false),
	RETURNING("returning", StaffNotificationKind.REQUESTS_NEED_ACTION, Scope.SUPPLIED,
		Set.of(RETURN_TRANSIT), false),
	FAILED_BORROWED("failedBorrowed", StaffNotificationKind.REQUEST_EXCEPTIONS, Scope.BORROWED,
		Set.of(ERROR), true),
	FAILED_SUPPLIED("failedSupplied", StaffNotificationKind.REQUEST_EXCEPTIONS, Scope.SUPPLIED,
		Set.of(ERROR), true),
	UNFILLED("unfilled", StaffNotificationKind.REQUEST_EXCEPTIONS, Scope.BORROWED,
		Set.of(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY), true);

	public enum Scope {
		BORROWED,
		SUPPLIED
	}

	private final String id;
	private final StaffNotificationKind kind;
	private final Scope scope;
	private final Set<PatronRequest.Status> statuses;
	private final boolean windowed;

	HomeBucket(String id, StaffNotificationKind kind, Scope scope, Set<PatronRequest.Status> statuses,
		boolean windowed) {

		this.id = id;
		this.kind = kind;
		this.scope = scope;
		this.statuses = statuses;
		this.windowed = windowed;
	}

	/** The Home count id the admin app links from. */
	public String id() {
		return id;
	}

	public StaffNotificationKind kind() {
		return kind;
	}

	public Scope scope() {
		return scope;
	}

	public Set<PatronRequest.Status> statuses() {
		return statuses;
	}

	public boolean windowed() {
		return windowed;
	}
}
