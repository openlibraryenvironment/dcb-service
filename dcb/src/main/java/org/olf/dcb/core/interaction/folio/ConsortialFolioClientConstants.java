package org.olf.dcb.core.interaction.folio;

import lombok.experimental.UtilityClass;

@UtilityClass
final class ConsortialFolioClientConstants {

	// DCB transaction roles
	static final String ROLE_LENDER = "LENDER";
	static final String ROLE_BORROWER = "BORROWER";
	static final String ROLE_BORROWING_PICKUP = "BORROWING-PICKUP";
	static final String ROLE_PICKUP = "PICKUP";

	// Generic operation result strings
	static final String RESULT_OK = "OK";
	static final String RESULT_OK_CLOSED = "OK_CLOSED";
	static final String RESULT_OK_NOT_RESOLVED = "OK_NOT_RESOLVED";

	// Request / item status
	static final String STATUS_MISSING = "MISSING";

	// Patron authentication profile
	static final String AUTH_PROFILE_BASIC_BARCODE_PIN = "BASIC/BARCODE+PIN";

	// Reference-value mapping category / context strings
	static final String MAPPING_ITEM_TYPE = "ItemType";
	static final String MAPPING_PATRON_TYPE = "patronType";
	static final String MAPPING_DCB = "DCB";

	// FOLIO-specific prefixes
	static final String FOLIO_SERVICE_POINT_PREFIX = "FolioServicePoint:";

	// FOLIO inventory item status strings
	static final String FOLIO_INVENTORY_STATUS_AVAILABLE = "Available";
	static final String FOLIO_INVENTORY_STATUS_CHECKED_OUT = "Checked out";
	static final String FOLIO_INVENTORY_STATUS_IN_TRANSIT = "In transit";
	static final String FOLIO_INVENTORY_STATUS_AWAITING_PICKUP = "Awaiting pickup";
	static final String FOLIO_INVENTORY_STATUS_MISSING = "Missing";
	static final String FOLIO_INVENTORY_STATUS_DECLARED_LOST = "Declared lost";

	// Response statuses
	static final String PING_STATUS_OK = "OK";
	static final String PING_STATUS_ERROR = "ERROR";

	// API path templates
	static final String PATH_RTAC = "/rtac";
	static final String PATH_DCB_TRANSACTION = "/dcbService/transactions/%s";
	static final String PATH_DCB_TRANSACTION_STATUS = "/dcbService/transactions/%s/status";
	static final String PATH_DCB_TRANSACTION_RENEW = "/dcbService/transactions/%s/renew";
	static final String PATH_DCB_TRANSACTION_BLOCK_RENEWAL = "/dcbService/transactions/%s/block-renewal";
	static final String PATH_USERS = "/users/users";
	static final String PATH_PATRON_PIN_VERIFY = "/users/patron-pin/verify";
	static final String PATH_INVENTORY_ITEMS = "/inventory/items";
	static final String PATH_INVENTORY_INSTANCES = "/inventory/instances";
	static final String PATH_PROXY_HEALTH = "/_/proxy/health";
}
