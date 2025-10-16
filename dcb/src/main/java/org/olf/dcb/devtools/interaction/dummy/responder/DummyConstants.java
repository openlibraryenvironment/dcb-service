package org.olf.dcb.devtools.interaction.dummy.responder;

import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;

public class DummyConstants {
	public final static String SUPPLIER_ROLE = "supplier";
	public final static String PICKUP_ROLE = "pickup";
	public final static String BORROWER_ROLE = "borrower";

	public static final String RET_STD_RESPONSE_TYPE = STANDARD_WORKFLOW;
	public static final String RET_PUA_RESPONSE_TYPE = PICKUP_ANYWHERE_WORKFLOW;
	public static final String CUSTOM_RESPONSE_TYPE = "CUSTOM";

	public static final String RECEIVED_AT_PICKUP_STATE = "RECEIVED_AT_PICKUP";
	public static final String READY_FOR_PICKUP_STATE = "READY_FOR_PICKUP";
	public static final String LOANED_STATE = "LOANED";
	public static final String RETURN_TRANSIT_STATE = "RETURN_TRANSIT";
	public static final String COMPLETED_STATE = "COMPLETED";
}
