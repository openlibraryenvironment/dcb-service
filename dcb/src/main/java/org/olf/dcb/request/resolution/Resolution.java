package org.olf.dcb.request.resolution;

import java.util.Optional;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class Resolution {
	PatronRequest patronRequest;
	@Builder.Default Optional<SupplierRequest> optionalSupplierRequest = Optional.empty();
	@Builder.Default Optional<Item> chosenItem = Optional.empty();

	static Resolution resolveToNoItemsSelectable(PatronRequest patronRequest) {
		return builder()
			.patronRequest(patronRequest.resolveToNoItemsSelectable())
			.build();
	}

	static Resolution resolveToChosenItem(SupplierRequest supplierRequest, Item item) {
		return builder()
			.patronRequest(supplierRequest.getPatronRequest())
			.optionalSupplierRequest(Optional.of(supplierRequest))
			.chosenItem(Optional.of(item))
			.build();
	}
}
