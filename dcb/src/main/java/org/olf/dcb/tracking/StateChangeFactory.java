package org.olf.dcb.tracking;

import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.tracking.model.StateChange;

import java.util.HashMap;
import java.util.Map;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

/**
 * Factory class for building StateChange instances with consistent naming and encapsulated logic
 * for each event type. Used throughout tracking for event emission.
 */
@Slf4j
public class StateChangeFactory {

	private static final String PATRON_REQUEST_RESOURCE_TYPE = "PatronRequest";
	private static final String BORROWER_VIRTUAL_ITEM_RESOURCE_TYPE = "BorrowerVirtualItem";
	private static final String PICKUP_REQUEST_RESOURCE_TYPE = "PickupRequest";
	private static final String PICKUP_ITEM_RESOURCE_TYPE = "PickupItem";
	private static final String SUPPLIER_ITEM_RESOURCE_TYPE = "SupplierItem";
	private static final String SUPPLIER_REQUEST_RESOURCE_TYPE = "SupplierRequest";

	public static final String SUPPLIER_REQUEST_ERROR = "ERROR";

	// === PATRON REQUEST STATUS CHANGES ===

	public static StateChange patronRequestStatusChanged(PatronRequest pr, HostLmsRequest hold) {
		final var patronRequestId = getValueOrNull(pr, PatronRequest::getId);
		final var fromState = getValueOrNull(pr, PatronRequest::getLocalRequestStatus);
		final var holdStatus = getValueOrNull(hold, HostLmsRequest::getStatus);

		final var fromRawStatus = getValueOrNull(pr, PatronRequest::getRawLocalRequestStatus);
		final var toRawStatus = getValueOrNull(hold, HostLmsRequest::getRawStatus);

		final Map<String, Object> props = new HashMap<>();

		props.put("fromRawStatus", fromRawStatus);
		props.put("toRawStatus", toRawStatus);

		return StateChange.builder()
			.patronRequestId(patronRequestId)
			.resourceType(PATRON_REQUEST_RESOURCE_TYPE)
			.resourceId(patronRequestId.toString())
			.fromState(fromState)
			.toState(holdStatus)
			.resource(pr)
			.additionalProperties(props)
			.build();
	}

	// === BORROWER VIRTUAL ITEM CHANGES ===

	public static StateChange virtualItemStatusChanged(PatronRequest pr, HostLmsItem item) {
		final var prId = getValueOrNull(pr, PatronRequest::getId);
		final var fromState = getValueOrNull(pr, PatronRequest::getLocalItemStatus);
		final var toState = getValueOrNull(item, HostLmsItem::getStatus);
		final var fromRenewalCount = getValueOrNull(pr, PatronRequest::getLocalRenewalCount);
		final var toRenewalCount = getValueOrNull(item, HostLmsItem::getRenewalCount);

		final var fromRawStatus = getValueOrNull(pr, PatronRequest::getRawLocalItemStatus);
		final var toRawStatus = getValueOrNull(item, HostLmsItem::getRawStatus);

		final Map<String, Object> props = new HashMap<>();

		props.put("fromRawStatus", fromRawStatus);
		props.put("toRawStatus", toRawStatus);

		return StateChange.builder()
			.patronRequestId(prId)
			.resourceType(BORROWER_VIRTUAL_ITEM_RESOURCE_TYPE)
			.resourceId(prId.toString())
			.fromState(fromState)
			.toState(toState)
			.fromRenewalCount(fromRenewalCount)
			.toRenewalCount(toRenewalCount)
			.resource(pr)
			.additionalProperties(props)
			.build();
	}

	public static StateChange virtualItemRenewalCountChanged(PatronRequest pr, HostLmsItem item) {
		final var prId = getValueOrNull(pr, PatronRequest::getId);
		final var fromState = getValueOrNull(pr, PatronRequest::getLocalItemStatus);
		final var toState = getValueOrNull(item, HostLmsItem::getStatus);
		final var fromRenewalCount = getValueOrNull(pr, PatronRequest::getLocalRenewalCount);
		final var toRenewalCount = getValueOrNull(item, HostLmsItem::getRenewalCount);

		final var fromRawStatus = getValueOrNull(pr, PatronRequest::getRawLocalItemStatus);
		final var toRawStatus = getValueOrNull(item, HostLmsItem::getRawStatus);

		final Map<String, Object> props = new HashMap<>();

		props.put("fromRawStatus", fromRawStatus);
		props.put("toRawStatus", toRawStatus);

		return StateChange.builder()
			.patronRequestId(prId)
			.resourceType(BORROWER_VIRTUAL_ITEM_RESOURCE_TYPE)
			.resourceId(prId.toString())
			.fromState(fromState)
			.toState(toState)
			.fromRenewalCount(fromRenewalCount)
			.toRenewalCount(toRenewalCount)
			.resource(pr)
			.additionalProperties(props)
			.build();
	}

	// === PICKUP REQUEST / ITEM CHANGES ===

	public static StateChange pickupRequestStatusChanged(PatronRequest pr, HostLmsRequest hold) {
		final var prId = getValueOrNull(pr, PatronRequest::getId);
		final var fromState = getValueOrNull(pr, PatronRequest::getPickupRequestStatus);
		final var toState = getValueOrNull(hold, HostLmsRequest::getStatus);

		final var fromRawStatus = getValueOrNull(pr, PatronRequest::getRawPickupRequestStatus);
		final var toRawStatus = getValueOrNull(hold, HostLmsRequest::getRawStatus);

		final Map<String, Object> props = new HashMap<>();

		props.put("fromRawStatus", fromRawStatus);
		props.put("toRawStatus", toRawStatus);

		return StateChange.builder()
			.patronRequestId(prId)
			.resourceType(PICKUP_REQUEST_RESOURCE_TYPE)
			// because the pickup request is on the patron request we use the patron request id here
			.resourceId(prId.toString())
			.fromState(fromState)
			.toState(toState)
			.resource(pr)
			.additionalProperties(props)
			.build();
	}

	public static StateChange pickupItemStatusChanged(PatronRequest pr, HostLmsItem item) {
		final var prId = getValueOrNull(pr, PatronRequest::getId);
		final var fromState = getValueOrNull(pr, PatronRequest::getPickupItemStatus);
		final var toState = getValueOrNull(item, HostLmsItem::getStatus);

		final var fromRawStatus = getValueOrNull(pr, PatronRequest::getRawPickupItemStatus);
		final var toRawStatus = getValueOrNull(item, HostLmsItem::getRawStatus);

		final Map<String, Object> props = new HashMap<>();

		props.put("fromRawStatus", fromRawStatus);
		props.put("toRawStatus", toRawStatus);

		return StateChange.builder()
			.patronRequestId(prId)
			.resourceType(PICKUP_ITEM_RESOURCE_TYPE)
			.resourceId(prId.toString())
			.fromState(fromState)
			.toState(toState)
			.resource(pr)
			.additionalProperties(props)
			.build();
	}

	// === SUPPLIER ITEM CHANGES ===

	public static StateChange supplierItemStatusChanged(SupplierRequest sr, HostLmsItem item) {
		final var prId = getValueOrNull(sr.getPatronRequest(), PatronRequest::getId);
		final var srId = getValueOrNull(sr, SupplierRequest::getId);
		final var fromState = getValueOrNull(sr, SupplierRequest::getLocalItemStatus);
		final var toState = getValueOrNull(item, HostLmsItem::getStatus);
		final var fromRenewalCount = getValueOrNull(sr, SupplierRequest::getLocalRenewalCount);
		final var toRenewalCount = getValueOrNull(item, HostLmsItem::getRenewalCount);
		final var fromHoldCount = getValueOrNull(sr, SupplierRequest::getLocalHoldCount);
		final var toHoldCount = getValueOrNull(item, HostLmsItem::getHoldCount);

		final var fromRawStatus = getValueOrNull(sr, SupplierRequest::getRawLocalItemStatus);
		final var toRawStatus = getValueOrNull(item, HostLmsItem::getRawStatus);

		final Map<String, Object> props = new HashMap<>();

		props.put("fromRawStatus", fromRawStatus);
		props.put("toRawStatus", toRawStatus);

		return StateChange.builder()
			.patronRequestId(prId)
			.resourceType(SUPPLIER_ITEM_RESOURCE_TYPE)
			.resourceId(srId.toString())
			.fromState(fromState)
			.toState(toState)
			.fromRenewalCount(fromRenewalCount)
			.toRenewalCount(toRenewalCount)
			.fromHoldCount(fromHoldCount)
			.toHoldCount(toHoldCount)
			.resource(sr)
			.additionalProperties(props)
			.build();
	}

	public static StateChange supplierItemRenewalCountChanged(SupplierRequest sr, HostLmsItem item) {
		final var prId = getValueOrNull(sr.getPatronRequest(), PatronRequest::getId);
		final var srId = getValueOrNull(sr, SupplierRequest::getId);
		final var fromState = getValueOrNull(sr, SupplierRequest::getLocalItemStatus);
		final var toState = getValueOrNull(item, HostLmsItem::getStatus);
		final var fromRenewalCount = getValueOrNull(sr, SupplierRequest::getLocalRenewalCount);
		final var toRenewalCount = getValueOrNull(item, HostLmsItem::getRenewalCount);
		final var fromHoldCount = getValueOrNull(sr, SupplierRequest::getLocalHoldCount);
		final var toHoldCount = getValueOrNull(item, HostLmsItem::getHoldCount);

		final var fromRawStatus = getValueOrNull(sr, SupplierRequest::getRawLocalItemStatus);
		final var toRawStatus = getValueOrNull(item, HostLmsItem::getRawStatus);

		final Map<String, Object> props = new HashMap<>();

		props.put("fromRawStatus", fromRawStatus);
		props.put("toRawStatus", toRawStatus);

		return StateChange.builder()
			.patronRequestId(prId)
			.resourceType(SUPPLIER_ITEM_RESOURCE_TYPE)
			.resourceId(srId.toString())
			.fromState(fromState)
			.toState(toState)
			.fromRenewalCount(fromRenewalCount)
			.toRenewalCount(toRenewalCount)
			.fromHoldCount(fromHoldCount)
			.toHoldCount(toHoldCount)
			.resource(sr)
			.additionalProperties(props)
			.build();
	}

	// === SUPPLIER REQUEST STATUS CHANGES ===

	public static StateChange supplierRequestStatusChanged(SupplierRequest sr, HostLmsRequest hold) {
		final var prId = getValueOrNull(sr.getPatronRequest(), PatronRequest::getId);
		final var srId = getValueOrNull(sr, SupplierRequest::getId);
		final var fromState = getValueOrNull(sr, SupplierRequest::getLocalStatus);
		final var toState = getValueOrNull(hold, HostLmsRequest::getStatus);

		final var fromRawStatus = getValueOrNull(sr, SupplierRequest::getRawLocalStatus);
		final var toRawStatus = getValueOrNull(hold, HostLmsRequest::getRawStatus);

		final Map<String, Object> props = new HashMap<>();

		props.put("fromRawStatus", fromRawStatus);
		props.put("toRawStatus", toRawStatus);

		// If the hold has an item and/or a barcode attached, pass it along
		if (hold.getRequestedItemId() != null) {
			props.put("RequestedItemId", hold.getRequestedItemId());
		}

		return StateChange.builder()
			.patronRequestId(prId)
			.resourceType(SUPPLIER_REQUEST_RESOURCE_TYPE)
			.resourceId(srId.toString())
			.fromState(fromState)
			.toState(toState)
			.resource(sr)
			.additionalProperties(props)
			.build();
	}

	public static StateChange supplierRequestErrored(SupplierRequest sr, Throwable error) {
		final var prId = getValueOrNull(sr.getPatronRequest(), PatronRequest::getId);
		final var srId = getValueOrNull(sr, SupplierRequest::getId);
		final var fromState = getValueOrNull(sr, SupplierRequest::getLocalStatus);

		final var fromRawStatus = getValueOrNull(sr, SupplierRequest::getRawLocalStatus);

		var props = new HashMap<String, Object>();

		auditThrowable(props, "Throwable", error);
		props.put("fromRawStatus", fromRawStatus);

		return StateChange.builder()
			.patronRequestId(prId)
			.resourceType(SUPPLIER_REQUEST_RESOURCE_TYPE)
			.resourceId(srId.toString())
			.fromState(fromState)
			.toState(SUPPLIER_REQUEST_ERROR)
			.resource(sr)
			.additionalProperties(props)
			.build();
	}
}
