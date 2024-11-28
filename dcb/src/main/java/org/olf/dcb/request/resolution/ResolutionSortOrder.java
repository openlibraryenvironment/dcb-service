package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import reactor.core.publisher.Mono;

public interface ResolutionSortOrder {

	public static final String CODE_AVAILABILITY_DATE = "AvailabilityDate";
	public static final String CODE_GEO_DISTANCE = "Geo";

	// Resolution Strategies must return a code which can be used to select
	// an implementation based on config
	String getCode();

	Mono<List<Item>> sortItems(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest);
}
