package org.olf.reshare.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.core.model.Item;

public interface ResolutionStrategy {
	Item chooseItem(List<Item> items, UUID clusterRecordId);
}
