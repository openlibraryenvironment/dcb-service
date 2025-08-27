package org.olf.dcb.request.resolution;

import java.util.List;

import org.olf.dcb.core.model.Item;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import reactor.core.publisher.Mono;

public interface ResolutionSortOrder {
	String CODE_AVAILABILITY_DATE = "AvailabilityDate";
	String CODE_GEO_DISTANCE = "Geo";

	// Resolution Strategies must return a code which can be used to select
	// an implementation based on config
	String getCode();

	Mono<List<Item>> sortItems(Parameters parameters);

	@Value
	@Builder
	@ToString
	class Parameters {
		List<Item> items;
		String pickupLocationCode;
	}
}
