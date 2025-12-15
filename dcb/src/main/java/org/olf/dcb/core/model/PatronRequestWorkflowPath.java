package org.olf.dcb.core.model;

import lombok.Getter;

import static org.olf.dcb.core.model.PatronRequest.Status.*;
import static org.olf.dcb.core.model.PatronRequest.Status;
import static org.olf.dcb.core.model.WorkflowConstants.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.function.Function;
import java.util.stream.Collectors;
// When defining a new workflow in DCB, please add your paths here.
// Or you will notice that "next expected status" doesn't work, and tracking may not be able to auto-progress requests

public enum PatronRequestWorkflowPath {

	STANDARD(STANDARD_WORKFLOW, transitions -> {
		transitions.put(SUBMITTED_TO_DCB, PATRON_VERIFIED);
		transitions.put(PATRON_VERIFIED, RESOLVED);
		transitions.put(RESOLVED, REQUEST_PLACED_AT_SUPPLYING_AGENCY);
		transitions.put(NOT_SUPPLIED_CURRENT_SUPPLIER, NOT_SUPPLIED_CURRENT_SUPPLIER);
		transitions.put(NO_ITEMS_SELECTABLE_AT_ANY_AGENCY, NO_ITEMS_SELECTABLE_AT_ANY_AGENCY);
		transitions.put(REQUEST_PLACED_AT_SUPPLYING_AGENCY, CONFIRMED);
		transitions.put(CONFIRMED, REQUEST_PLACED_AT_BORROWING_AGENCY);
		transitions.put(REQUEST_PLACED_AT_BORROWING_AGENCY, PICKUP_TRANSIT);
		transitions.put(PICKUP_TRANSIT, RECEIVED_AT_PICKUP);
		transitions.put(RECEIVED_AT_PICKUP, READY_FOR_PICKUP);
		transitions.put(READY_FOR_PICKUP, LOANED);
		transitions.put(LOANED, RETURN_TRANSIT);
		transitions.put(RETURN_TRANSIT, COMPLETED);
		transitions.put(CANCELLED, CANCELLED);
		transitions.put(COMPLETED, FINALISED);
		transitions.put(FINALISED, FINALISED);
		transitions.put(ERROR, ERROR);
	}),

	PICKUP_ANYWHERE(PICKUP_ANYWHERE_WORKFLOW, transitions -> {
		// PUA has the standard paths first
		transitions.putAll(STANDARD.getTransitions());
		// Then we just need to add the overrides
		transitions.put(REQUEST_PLACED_AT_BORROWING_AGENCY, REQUEST_PLACED_AT_PICKUP_AGENCY);
		transitions.put(REQUEST_PLACED_AT_PICKUP_AGENCY, PICKUP_TRANSIT);
	}),

	EXPEDITED(EXPEDITED_WORKFLOW, transitions -> {
		transitions.putAll(STANDARD.getTransitions());
		// expedited workflow skips the transit statuses
		// And you can't do PUA expedited requests - they are same supplier and pickup only
		transitions.remove(REQUEST_PLACED_AT_BORROWING_AGENCY, PICKUP_TRANSIT);
		transitions.remove(PICKUP_TRANSIT, RECEIVED_AT_PICKUP);
		transitions.remove(RECEIVED_AT_PICKUP, READY_FOR_PICKUP);
		transitions.remove(READY_FOR_PICKUP, LOANED);
		transitions.put(REQUEST_PLACED_AT_BORROWING_AGENCY, LOANED); // Make sure we catch the jump straight to LOANED.
		// Should be the same as standard from LOANED onwards.
	}),

	LOCAL(LOCAL_WORKFLOW, transitions -> {
		// Local is comparatively simple.
		transitions.put(SUBMITTED_TO_DCB, PATRON_VERIFIED);
		transitions.put(PATRON_VERIFIED, RESOLVED);
		transitions.put(RESOLVED, REQUEST_PLACED_AT_SUPPLYING_AGENCY);
		transitions.put(REQUEST_PLACED_AT_SUPPLYING_AGENCY, HANDED_OFF_AS_LOCAL);
	});

	private final String code;
	@Getter
	private final Map<PatronRequest.Status, PatronRequest.Status> transitions;

	private static final Map<String, PatronRequestWorkflowPath> CODE_MAP = Stream.of(values())
		.collect(Collectors.toMap(w -> w.code, Function.identity()));

	PatronRequestWorkflowPath(String code, java.util.function.Consumer<Map<Status, Status>> builder) {
		this.code = code;
		this.transitions = new EnumMap<>(PatronRequest.Status.class);
		builder.accept(this.transitions);
	}

	public PatronRequest.Status getNextStatus(PatronRequest.Status current) {
		return transitions.get(current);
	}

	public static PatronRequestWorkflowPath fromCode(String code) {
		// Standard remains the default if we get an acive workflow code we do not recognise.
		return CODE_MAP.getOrDefault(code, STANDARD);
	}
}
