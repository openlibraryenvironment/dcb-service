package org.olf.dcb.tracking;

import java.util.Objects;

import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.SupplierRequest;

final class SupplierItemChanges {
	private SupplierItemChanges() { }

	/**
	 * A supplier item has changed when its status differs from the one DCB holds, or when the
	 * Host LMS reports a renewable flag, renewal count or hold count that differs. A value the
	 * Host LMS does not report is not a change: Sierra reports no renewable flag, and a stored
	 * null reads back as the entity's default, so comparing it would record a change every poll.
	 */
	static boolean hasChanged(SupplierRequest supplierRequest, HostLmsItem item) {
		return !Objects.equals(item.getStatus(), supplierRequest.getLocalItemStatus())
			|| isReportedAndDifferent(item.getRenewable(), supplierRequest.getLocalRenewable())
			|| isReportedAndDifferent(item.getRenewalCount(), supplierRequest.getLocalRenewalCount())
			|| isReportedAndDifferent(item.getHoldCount(), supplierRequest.getLocalHoldCount());
	}

	private static boolean isReportedAndDifferent(Object reported, Object held) {
		return reported != null && !reported.equals(held);
	}
}
