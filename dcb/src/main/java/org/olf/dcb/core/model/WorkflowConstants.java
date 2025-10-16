package org.olf.dcb.core.model;

public class WorkflowConstants {
	/**
	 * It is useful to have a shorthand note of the specific workflow which is in force for the patron request - initially
	 * RET- RETURNABLE ITEMS
	 * RET-STD - We're placing a request at a remote system, but the patron will pick the item up from their local library (2 parties)
	 * RET-LOCAL - We're placing a request in a single system - the patron, pickup and lending roles are all within a single system (1 Party)
	 * RET-PUA - The Borrower, Patron and Pickup systems are all different (3 parties)
	 * RET-EXP - We're placing a request where the supplier and the pickup systems are the same, but the patron may be external. This results in an expedited checkout. (2 parties).
	 */
	public static final String STANDARD_WORKFLOW = "RET-STD";
	public static final String LOCAL_WORKFLOW = "RET-LOCAL";
	public static final String PICKUP_ANYWHERE_WORKFLOW = "RET-PUA";
	public static final String EXPEDITED_WORKFLOW = "RET-EXP";
}
