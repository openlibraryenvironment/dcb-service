package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;

public interface ResolutionStrategy {

	// Resolution Strategies must return a code which can be used to select
	// an implementation based on config
	String getCode();

	Item chooseItem(List<Item> items, UUID clusterRecordId);
}
