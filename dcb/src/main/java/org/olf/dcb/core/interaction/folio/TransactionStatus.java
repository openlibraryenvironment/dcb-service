package org.olf.dcb.core.interaction.folio;

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
}
