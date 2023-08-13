package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;

public interface ResolutionStrategy {
	Item chooseItem(List<Item> items, UUID clusterRecordId);
}
