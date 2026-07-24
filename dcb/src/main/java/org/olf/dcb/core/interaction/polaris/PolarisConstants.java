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
	public static final String VIRTUAL_BIB_BOOKS_LEADER = "LDR    cam 22     a 4500";
	public static final String VIRTUAL_BIB_AV_LEADER = "LDR    cgm 22     a 4500";

	// Polaris refuses to circulate to a patron whose address check date has passed, and re-applies
	// the block whenever circulation is attempted, so deleting the block is not enough on its own.
	// Virtual patrons inherit whatever address check period the local rules dictate, which is
	// frequently already in the past. St Louis avoid this by stamping the date well into the future.
	public static final int VIRTUAL_PATRON_ADDRESS_CHECK_YEARS = 100;
	// Some systems have local rules that refuse a date that far out
	public static final int VIRTUAL_PATRON_ADDRESS_CHECK_FALLBACK_YEARS = 5;
	// A date inside this window will expire part way through a loan, so treat it as already blocking
	public static final int VIRTUAL_PATRON_ADDRESS_CHECK_SAFETY_DAYS = 30;

	public static final String ADDRESS_CHECK_DATE_WARNING =
		"Unable to move the address check date for virtual patron %s at %s into the future. "
			+ "Polaris will block the virtual checkout until library staff clear the address check on this patron.";
	// Polaris raises this block when a patron's address check date has passed. It is the block a
	// lapsed virtual patron address check date produces, and the one Polaris re-derives at
	// circulation time regardless of any earlier deletion.
	static final Integer VERIFY_PATRON_DATA_BLOCK = 3;
	static final Integer REGISTRATION_HAS_EXPIRED_BLOCK = 100;

}
