package org.olf.dcb.core.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record PatronRequestAdminView(UUID id, Citation citation,
	PickupLocation pickupLocation, Requestor requestor,
	List<SupplierRequest> supplierRequests, Status status,
	LocalRequest localRequest, List<Audit> audits) {

	private static final Logger log = LoggerFactory.getLogger(PatronRequestAdminView.class);

	static PatronRequestAdminView from(PatronRequest patronRequest,
		List<org.olf.dcb.core.model.SupplierRequest> supplierRequests,
		List<PatronRequestAudit> audits) {

		// log.debug("Mapping patron request to view: {}", patronRequest);
		// log.debug("Mapping supplier requests to view: {}", supplierRequests);

		final var patron = patronRequest.getPatron();

		return new PatronRequestAdminView(patronRequest.getId(),
			new Citation(patronRequest.getBibClusterId(), patronRequest.getRequestedVolumeDesignation()),
			new PickupLocation(patronRequest.getPickupLocationCode()),
			new Requestor(patron.getId().toString(), patron.getHomeLibraryCode(),
				Identity.fromList(patron.getPatronIdentities())),
			SupplierRequest.fromList(supplierRequests),
			new Status(patronRequest.getStatus(), patronRequest.getErrorMessage()),
			new LocalRequest(patronRequest.getLocalRequestId(),
				patronRequest.getLocalRequestStatus(),
				patronRequest.getLocalItemId(),
				patronRequest.getLocalBibId()),
			Audit.fromList(audits));
	}

	@Serdeable
	record PickupLocation(String code) { }

	@Serdeable
	record Citation(UUID bibClusterId, String volumeDesignator) { }

	@Serdeable
	record Requestor(String id, String homeLibraryCode,
		@Nullable List<Identity> identities) { }

	@Serdeable
	record Identity(String localId, String hostLmsCode, Boolean homeIdentity) {

		private static Identity from(PatronIdentity patronIdentity) {
			return new Identity(patronIdentity.getLocalId(), patronIdentity.getHostLms().code,
				patronIdentity.getHomeIdentity());
		}
		public static List<Identity> fromList(List<PatronIdentity> patronIdentities) {
			return patronIdentities.stream()
				.map(Identity::from)
				.collect(Collectors.toList());
		}
	}

	@Serdeable
	record SupplierRequest(UUID id, Item item, String hostLmsCode,
		String status, String localHoldId, String localHoldStatus) {
		private static SupplierRequest from(
			org.olf.dcb.core.model.SupplierRequest supplierRequest) {

			return new SupplierRequest(supplierRequest.getId(),
				new Item(supplierRequest.getLocalItemId(),
					supplierRequest.getLocalItemBarcode(),
					supplierRequest.getLocalItemLocationCode()),
				supplierRequest.getHostLmsCode(),
				supplierRequest.getStatusCode().getDisplayName(),
				supplierRequest.getLocalId(),
				supplierRequest.getLocalStatus());
		}

		private static List<SupplierRequest> fromList(
			List<org.olf.dcb.core.model.SupplierRequest> supplierRequests) {

			return supplierRequests.stream()
				.map(SupplierRequest::from)
				.collect(Collectors.toList());
		}
	}

	@Serdeable
	record Audit(String id, String patronRequestId,
		@Nullable String date, @Nullable String description,
		@Nullable org.olf.dcb.core.model.PatronRequest.Status fromStatus, @Nullable org.olf.dcb.core.model.PatronRequest.Status toStatus,
		@Nullable Map<String, Object> data) {
		public static List<Audit> fromList(List<PatronRequestAudit> audits) {

			return audits.stream()
				.map(Audit::from)
				.collect(Collectors.toList());
		}

		static Audit from(PatronRequestAudit patronRequestAudit) {

			return new Audit(patronRequestAudit.getId().toString(),
				patronRequestAudit.getPatronRequest().getId().toString(),
				patronRequestAudit.getAuditDate().toString(), patronRequestAudit.getBriefDescription(),
				patronRequestAudit.getFromStatus(), patronRequestAudit.getToStatus(),
				patronRequestAudit.getAuditData());
		}
	}

	@Serdeable
	record Item(String id, String localItemBarcode, String localItemLocationCode) {}

	@Serdeable
	record Status(org.olf.dcb.core.model.PatronRequest.Status code, String errorMessage) {}

	@Serdeable
	record LocalRequest(String id, String status, String itemId, String bibId) {}
}
