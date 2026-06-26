package org.olf.dcb.request.lifecycle.placement;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;

public record SupplyingAgencyRequestResult(
	PatronRequest patronRequest,
	SupplierRequest supplierRequest,
	String hostLmsCode,
	String localRequestId,
	String localRequestStatus,
	String rawLocalRequestStatus,
	String localItemId,
	String localItemBarcode) {

	public static SupplyingAgencyRequestResult from(
		PatronRequest patronRequest,
		SupplierRequest supplierRequest) {

		return new SupplyingAgencyRequestResult(
			patronRequest,
			supplierRequest,
			supplierRequest != null ? supplierRequest.getHostLmsCode() : null,
			supplierRequest != null ? supplierRequest.getLocalId() : null,
			supplierRequest != null ? supplierRequest.getLocalStatus() : null,
			supplierRequest != null ? supplierRequest.getRawLocalStatus() : null,
			supplierRequest != null ? supplierRequest.getLocalItemId() : null,
			supplierRequest != null ? supplierRequest.getLocalItemBarcode() : null);
	}
}
