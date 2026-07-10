package org.olf.dcb.request.lifecycle.placement;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.lifecycle.LifecycleRole;

public record SupplyingAgencyRequestResult(
	PatronRequest patronRequest,
	SupplierRequest supplierRequest,
	String hostLmsCode,
	String localRequestId,
	String localRequestStatus,
	String rawLocalRequestStatus,
	String localItemId,
	String localItemBarcode,
	LifecycleRole role,
	String protocol,
	String correlationId,
	String remoteRequestId,
	String status,
	String rawStatus,
	String rawMessageReference) {

	public SupplyingAgencyRequestResult(
		PatronRequest patronRequest,
		SupplierRequest supplierRequest,
		String hostLmsCode,
		String localRequestId,
		String localRequestStatus,
		String rawLocalRequestStatus,
		String localItemId,
		String localItemBarcode) {

		this(
			patronRequest,
			supplierRequest,
			hostLmsCode,
			localRequestId,
			localRequestStatus,
			rawLocalRequestStatus,
			localItemId,
			localItemBarcode,
			LifecycleRole.SUPPLIER,
			null,
			null,
			localRequestId,
			localRequestStatus,
			rawLocalRequestStatus,
			null);
	}

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
