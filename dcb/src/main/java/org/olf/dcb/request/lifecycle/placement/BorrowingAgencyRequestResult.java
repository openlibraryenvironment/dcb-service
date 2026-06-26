package org.olf.dcb.request.lifecycle.placement;

import org.olf.dcb.core.model.PatronRequest;

public record BorrowingAgencyRequestResult(
	PatronRequest patronRequest,
	String hostLmsCode,
	String localRequestId,
	String localRequestStatus,
	String rawLocalRequestStatus,
	String localBibId,
	String localItemId,
	String localItemStatus,
	boolean createdVirtualBib,
	boolean createdVirtualItem) {

	public static BorrowingAgencyRequestResult from(PatronRequest patronRequest) {
		return new BorrowingAgencyRequestResult(
			patronRequest,
			patronRequest.getPatronHostlmsCode(),
			patronRequest.getLocalRequestId(),
			patronRequest.getLocalRequestStatus(),
			patronRequest.getRawLocalRequestStatus(),
			patronRequest.getLocalBibId(),
			patronRequest.getLocalItemId(),
			patronRequest.getLocalItemStatus(),
			patronRequest.getLocalBibId() != null,
			patronRequest.getLocalItemId() != null);
	}
}
