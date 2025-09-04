package org.olf.dcb.core.api;

import java.util.List;

import org.olf.dcb.request.workflow.PresentableItem;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Serdeable
public class ResolutionPreview {
	Boolean itemWasSelected;
	PresentableItem selectedItem;
	List<PresentableItem> allItemsFromAvailability;
	List<PresentableItem> filteredItems;
	List<PresentableItem> sortedItems;
}
