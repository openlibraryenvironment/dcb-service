package org.olf.dcb.item.availability;

import java.util.List;

import org.olf.dcb.core.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Value;

@Prototype
public class RequestableItemService {
	private static final Logger log = LoggerFactory.getLogger(RequestableItemService.class);

	private final List<String> requestableLocationCodes;
	private final Boolean locationFilteringEnabled;
	private static final String NONCIRC = "NONCIRC";

	public RequestableItemService(
		@Value("${dcb.requestability.location.codes.allowed:}") List<String> requestableLocationCodes,
		@Value("${dcb.requestability.location.filtering:false}") Boolean locationFilteringEnabled) {

		log.info("Location filtering enabled: {}", locationFilteringEnabled);
		log.info("Locations to allow: {}", requestableLocationCodes);

		this.requestableLocationCodes = requestableLocationCodes;
		this.locationFilteringEnabled = locationFilteringEnabled;
	}

	public boolean isRequestable(Item item) {
		log.debug("isRequestable({})", item);

		return isInAllowedLocation(item) 
			&& item.isAvailable()
			&& ( ( item.getCanonicalItemType() != null ) && ( ! item.getCanonicalItemType().equals(NONCIRC) ) )  ;
	}

	private Boolean isInAllowedLocation(Item item) {

		final var locationCode = item.getLocationCode();
		log.debug("isInAllowedLocation({})", locationCode);

		// location filtering only evaluated if value set to true in config
		// if not set, all locations are allowed
		if (!locationFilteringEnabled) return true;

		final var allowedLocation = requestableLocationCodes.contains(locationCode);

		if (allowedLocation) {
			log.info("{} is in the allowed location list", locationCode);
		}
		else {
			log.info("{} is NOT in the allowed location list", locationCode);
		}

		return allowedLocation;
	}
}
