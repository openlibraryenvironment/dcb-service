package org.olf.dcb.core.interaction.ncip;

/**
 * Single source of NCIP v2.02 protocol constants shared by every NCIP caller -
 * the imperative Foundation adaptor and the declarative request transport alike.
 *
 * Message names live here so the two callers cannot drift apart on the wire
 * vocabulary. Payload construction legitimately differs between imperative and
 * declarative callers and is NOT shared.
 */
public final class NcipProtocol {
	public static final String PROTOCOL = "ncip-v202";

	// Placement / lifecycle messages (used by the declarative transport).
	public static final String REQUEST_ITEM = "RequestItem";
	public static final String ACCEPT_ITEM = "AcceptItem";
	public static final String REQUEST_ITEM_RESPONSE = "RequestItemResponse";
	public static final String ACCEPT_ITEM_RESPONSE = "AcceptItemResponse";
	public static final String ITEM_REQUESTED = "ItemRequested";
	public static final String ITEM_REQUESTED_RESPONSE = "ItemRequestedResponse";
	public static final String CANCEL_REQUEST_ITEM = "CancelRequestItem";
	public static final String CANCEL_REQUEST_ITEM_RESPONSE = "CancelRequestItemResponse";
	public static final String ITEM_SHIPPED = "ItemShipped";
	public static final String ITEM_SHIPPED_RESPONSE = "ItemShippedResponse";
	public static final String ITEM_RECEIVED = "ItemReceived";
	public static final String ITEM_RECEIVED_RESPONSE = "ItemReceivedResponse";
	public static final String ITEM_CHECKED_IN = "ItemCheckedIn";
	public static final String ITEM_CHECKED_IN_RESPONSE = "ItemCheckedInResponse";
	public static final String ITEM_CHECKED_OUT = "ItemCheckedOut";
	public static final String ITEM_CHECKED_OUT_RESPONSE = "ItemCheckedOutResponse";

	// Imperative primitive messages (used by the Foundation NcipAdaptor).
	public static final String LOOKUP_USER = "LookupUser";
	public static final String LOOKUP_USER_RESPONSE = "LookupUserResponse";
	public static final String LOOKUP_ITEM = "LookupItem";
	public static final String LOOKUP_VERSION = "LookupVersion";
	public static final String LOOKUP_AGENCY = "LookupAgency";
	public static final String CREATE_USER = "CreateUser";
	public static final String CHECK_OUT_ITEM = "CheckOutItem";
	public static final String CHECK_IN_ITEM = "CheckInItem";

	private NcipProtocol() {
	}
}
