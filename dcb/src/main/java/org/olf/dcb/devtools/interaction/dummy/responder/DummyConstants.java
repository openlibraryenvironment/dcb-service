package org.olf.dcb.devtools.interaction.dummy.responder;

import java.util.Set;

public class DummyConstants {
	final static String SUPPLIER_ROLE = "supplier";
	final static String PICKUP_ROLE = "pickup";
	final static String BORROWER_ROLE = "borrower";
	static final Set<String> PUA_ROLES = Set.of("supplier", "pickup", "borrower");
	static final String RET_STD_RESPONSE_TYPE = "RET-STD";
	static final String RET_PUA_RESPONSE_TYPE = "RET-PUA";
	static final String CUSTOM_RESPONSE_TYPE = "CUSTOM";
}
