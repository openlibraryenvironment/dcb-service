package org.olf.dcb.request.lifecycle.placement;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.lifecycle.LifecycleRole;

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
	boolean createdVirtualItem,
	LifecycleRole role,
	String protocol,
	String correlationId,
	String remoteRequestId,
	String status,
	String rawStatus,
	String rawMessageReference) {

	public BorrowingAgencyRequestResult(
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

		this(
			patronRequest,
			hostLmsCode,
			localRequestId,
			localRequestStatus,
			rawLocalRequestStatus,
			localBibId,
			localItemId,
			localItemStatus,
			createdVirtualBib,
			createdVirtualItem,
			LifecycleRole.BORROWER,
			null,
			null,
			localRequestId,
			localRequestStatus,
			rawLocalRequestStatus,
			null);
	}

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
