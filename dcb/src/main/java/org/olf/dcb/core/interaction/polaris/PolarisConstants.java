package org.olf.dcb.core.interaction.polaris;

class PolarisConstants {
	public static final String UUID5_PREFIX = "ingest-source:polaris-lms";
	public static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

	// handled item statuses (using name)
	public static final String AVAILABLE = "In";
	public static final String TRANSFERRED = "Transferred";
	public static final String ON_HOLD_SHELF = "Held";
	public static final String CHECKED_OUT = "Out";
	public static final String IN_TRANSIT = "In-Transit";
	public static final String MISSING = "Missing";
	public static final String SHELVING = "Shelving";

	// Unhandled item statuses (using name)
	public static final String CLAIM_RETURNED = "Claim Returned";
	public static final String CLAIM_NEVER_HAD = "Claim Never Had";
	public static final String CLAIM_MISSING_PARTS = "Claim Missing Parts";
	public static final String LOST = "Lost";
	public static final String RETURNED_ILL = "Returned ILL";
	public static final String NON_CIRCULATING = "Non-Circulating";
	public static final String WITHDRAWN = "Withdrawn";
	public static final String IN_REPAIR = "In Repair";
	public static final String BINDERY = "Bindery";
	public static final String UNAVAILABLE = "Unavailable";
	public static final String IN_PROCESS = "In Process";
	public static final String ON_ORDER = "On-Order";
	public static final String ROUTED = "Routed";
	public static final String E_CONTENT_EXTERNAL_LOAN = "EContent External Loan";

	// virtual bib values
	public static final String VIRTUAL_BIB_BOOKS_LEADER = "LDR     cam  22      a 4500";
	public static final String VIRTUAL_BIB_AV_LEADER = "LDR     cgm  22      a 4500";
}
