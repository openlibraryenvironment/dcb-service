package org.olf.reshare.dcb.item.availability;

import java.util.List;
import java.util.stream.Collectors;

import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.core.model.ItemStatus;
import org.olf.reshare.dcb.core.model.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class RequestableItemService {
	private static final Logger log = LoggerFactory.getLogger(RequestableItemService.class);

	private final List<String> requestableLocationCodes;
	private final Boolean locationfiltering;

	public RequestableItemService(
		@Value("${dcb.requestability.location.codes.allowed:}") List<String> requestableLocationCodes,
		@Value("${dcb.requestability.location.filtering:false}") Boolean locationfiltering) {

		this.requestableLocationCodes = requestableLocationCodes;
		this.locationfiltering = locationfiltering;
	}

	public List<Item> determineRequestable(List<Item> items) {
		log.debug("determineRequestable({})", items);

		return items.stream()
			.map(this::toRequestableItem)
			.collect(Collectors.toList());
	}

	private Item toRequestableItem(Item item) {
		log.debug("toRequestableItem({})", requestableLocationCodes);

		final var allowedLocation = isAllowedLocation(item);
		final var availability = item.isAvailable();

		final var requestability = isRequestable(allowedLocation, availability);

		return requestableItem(item, requestability);
	}

	private Boolean isRequestable(Boolean isAllowedLocation, Boolean isAvailable) {
		return isAllowedLocation && isAvailable;
	}

	private Boolean isAllowedLocation(Item item) {
		final var itemLocationCode = item.getLocation().getCode();

		log.debug("isAllowedLocation({})", itemLocationCode);

		// location filtering only evaluated if value set to true in config
		// if not set, all locations are allowed
		if (locationfiltering) return requestableLocationCodes.contains(itemLocationCode);
		return true;
	}

	private static Item requestableItem(Item item, Boolean requestability) {
		return new Item(item.getId(),
			new ItemStatus(item.getStatus().getCode()), item.getDueDate(),
			Location.builder()
				.code(item.getLocation().getCode())
				.name(item.getLocation().getName())
				.build(),
			item.getBarcode(), item.getCallNumber(),
			item.getHostLmsCode(), requestability);
	}
}
