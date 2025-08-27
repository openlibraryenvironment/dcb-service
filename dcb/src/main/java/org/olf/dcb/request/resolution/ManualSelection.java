package org.olf.dcb.request.resolution;

import java.util.List;

import org.olf.dcb.core.model.Item;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ManualSelection {
	public Item chooseItem(List<Item> items, ManualItemSelection itemSelection) {
		itemSelection.validate();

		log.info("chooseItem(array of size {})", items.size());

		return items.stream()
			.peek(item -> log.debug("Consider item {} @ {} against selection: {}",
				item.getLocalId(), item.getLocation(), itemSelection))
			.filter(itemSelection::matches)
			.findFirst()
			.orElse(null);
	}
}
