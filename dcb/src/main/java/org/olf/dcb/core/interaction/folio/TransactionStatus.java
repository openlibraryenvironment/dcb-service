package org.olf.dcb.core.interaction.folio;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import io.micronaut.data.annotation.Transient;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Serdeable
class TransactionStatus {
	final static String CREATED = "CREATED";
	final static String OPEN = "OPEN";
	final static String AWAITING_PICKUP = "AWAITING_PICKUP";
	final static String ITEM_CHECKED_OUT = "ITEM_CHECKED_OUT";
	final static String ITEM_CHECKED_IN = "ITEM_CHECKED_IN";
	final static String CLOSED = "CLOSED";
	final static String CANCELLED = "CANCELLED";
	final static String ERROR = "ERROR";

	String status;
	Item item;

	@Transient
	Integer getHoldCount() {
		return getValue(item, Item::getHoldCount, 0 );
	}

	@Transient
	Integer getRenewalCount() {
		return getValue(item, Item::getRenewalInfo, RenewalInformation::getRenewalCount, 0);
	}

	@Transient
	Boolean getRenewable() {
		return getValue(item, Item::getRenewalInfo, RenewalInformation::getRenewable, Boolean.FALSE);
	}

  // https://github.com/folio-org/edge-dcb/blob/master/src/main/resources/swagger.api/schemas/dcbItem.yaml

	@Data
	@Builder
	@Serdeable
	static class Item {
		RenewalInformation renewalInfo;
		Integer holdCount;
	}

	@Data
	@Builder
	@Serdeable
	static class RenewalInformation {
		Integer renewalCount;
		Integer renewalMaxCount;
		Boolean renewable;
	}
}
