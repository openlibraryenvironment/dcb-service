package org.olf.dcb.core.interaction.polaris;

class PolarisConstants {
	public static final String UUID5_PREFIX = "ingest-source:polaris-lms";
	public static final String DEFAULT_CLIENT_BASE_URL = "base-url";
	public static final String OVERRIDE_CLIENT_BASE_URL = "base-url-application-services";
	public static final String MAX_BIBS = "page-size";
	public static final String DOMAIN_ID = "domain-id";
	public static final String STAFF_USERNAME = "staff-username";
	public static final String STAFF_PASSWORD = "staff-password";
	public static final String ACCESS_ID = "access-id";
	public static final String ACCESS_KEY = "access-key";
	public static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
	public static final String LOGON_BRANCH_ID = "logon-branch-id";
	public static final String LOGON_USER_ID = "logon-user-id";

	public static final String BORROWER_LENDING_FLOW = "borrower-lending-flow"; // can be set to DCB or ILL

	// PAPIService
	public static final String PAPI = "papi";
	public static final String PAPI_VERSION = "papi-version";
	public static final String PAPI_LANG_ID = "lang-id";
	public static final String PAPI_APP_ID = "app-id";
	public static final String PAPI_ORG_ID = "org-id";
	// polaris.applicationservices
	public static final String SERVICES = "services";
	public static final String SERVICES_VERSION = "services-version";
	public static final String SERVICES_LANGUAGE = "language";
	public static final String SERVICES_PRODUCT_ID = "product-id";
	public static final String SERVICES_SITE_DOMAIN = "site-domain";
	public static final String SERVICES_ORG_ID = "organisation-id";
	public static final String SERVICES_WORKSTATION_ID = "workstation-id";
	public static final String PATRON_BARCODE_PREFIX = "patron-barcode-prefix";

	// item
	public static final String ITEM = "item";
	public static final String RENEW_LIMIT = "renewal-limit";
	public static final String FINE_CODE_ID = "fine-code-id";
	public static final String HISTORY_ACTION_ID = "history-action-id";
	public static final String LOAN_PERIOD_CODE_ID = "loan-period-code-id";
	public static final String SHELVING_SCHEME_ID = "shelving-scheme-id";
	public static final String BARCODE_PREFIX = "barcode-prefix";
	public static final String ILL_LOCATION_ID = "ill-location-id";

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
}
