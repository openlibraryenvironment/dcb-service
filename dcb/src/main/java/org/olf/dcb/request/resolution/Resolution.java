package org.olf.dcb.request.resolution;

import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Resolution {
	PatronRequest patronRequest;
	@Builder.Default Optional<SupplierRequest> optionalSupplierRequest = Optional.empty();

	static Resolution resolveToNoItemsSelectable(PatronRequest patronRequest) {
		return builder()
			.patronRequest(patronRequest.resolveToNoItemsSelectable())
			.build();
	}

	static Resolution resolveToChosenItem(SupplierRequest supplierRequest) {
		return builder()
			.patronRequest(supplierRequest.getPatronRequest())
			.optionalSupplierRequest(Optional.of(supplierRequest))
			.build();
	}
}
