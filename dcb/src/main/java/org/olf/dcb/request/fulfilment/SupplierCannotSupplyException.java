package org.olf.dcb.request.fulfilment;

import org.olf.dcb.core.interaction.SupplierPreflight;

/** A copy staff chose cannot be supplied, because of a fault in its supplier's configuration. */
public class SupplierCannotSupplyException extends RuntimeException {
	public SupplierCannotSupplyException(String supplierHostLmsCode, SupplierPreflight preflight) {
		super("The chosen copy at %s cannot be supplied: %s. To fix: %s"
			.formatted(supplierHostLmsCode, preflight.reason(), preflight.remedy()));
	}
}
