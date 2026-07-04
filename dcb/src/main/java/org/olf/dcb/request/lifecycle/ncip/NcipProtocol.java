package org.olf.dcb.request.lifecycle.ncip;

public final class NcipProtocol {
	public static final String PROTOCOL = "ncip-v202";
	public static final String REQUEST_ITEM = "RequestItem";
	public static final String ACCEPT_ITEM = "AcceptItem";
	public static final String LOOKUP_USER = "LookupUser";
	public static final String REQUEST_ITEM_RESPONSE = "RequestItemResponse";
	public static final String ACCEPT_ITEM_RESPONSE = "AcceptItemResponse";
	public static final String LOOKUP_USER_RESPONSE = "LookupUserResponse";
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

	private NcipProtocol() {
	}
}
