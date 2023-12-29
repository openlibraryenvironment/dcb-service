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
		log.debug("about to perform isRequestable check for {}", item);

		if ( !isInAllowedLocation(item) ) {
			log.debug("Item is NOT in an allowed location - reject");
			return false;
		}

		if ( ! item.isAvailable() ) {
			log.debug("Item is NOT available - reject");
			return false;
		}

		if ( item.getCanonicalItemType() == null ) {	
			log.debug("Item has no canonical type - reject");
			return false;
		}

		if ( item.getCanonicalItemType().equals(NONCIRC) ) {
			log.debug("Item is NON-CIRCULATING - reject");
			return false;
		}

		log.debug("isRequestable passed");

		return true;
	}

	private Boolean isInAllowedLocation(Item item) {

		final var locationCode = item.getLocationCode();


		// location filtering only evaluated if value set to true in config
		// if not set, all locations are allowed
		if (!locationFilteringEnabled) return true;

		log.debug("location filtering is enabled - isInAllowedLocation({})", locationCode);

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
