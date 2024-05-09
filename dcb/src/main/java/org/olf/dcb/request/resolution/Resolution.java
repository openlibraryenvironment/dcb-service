package org.olf.dcb.request.resolution;

import static lombok.AccessLevel.PRIVATE;

import java.util.Optional;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;

import lombok.Builder;
import lombok.Value;

@Builder(access = PRIVATE)
@Value
public class Resolution {
	PatronRequest patronRequest;
	@Builder.Default Optional<Item> chosenItem = Optional.empty();

	static Resolution resolveToNoItemsSelectable(PatronRequest patronRequest) {
		return builder()
			.patronRequest(patronRequest)
			.build();
	}

	static Resolution resolveToChosenItem(PatronRequest patronRequest, Item item) {
		return builder()
			.patronRequest(patronRequest)
			.chosenItem(Optional.of(item))
			.build();
	}
}
