package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;

@Singleton
public class GeoDistanceResolutionStrategy implements ResolutionStrategy {
	private static final Logger log = LoggerFactory.getLogger(GeoDistanceResolutionStrategy.class);

	public static final String GEO_DISTANCE_STRATEGY = "Geo";

	@Override
	public String getCode() {
		return GEO_DISTANCE_STRATEGY;
	}


	@Override
	public Item chooseItem(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		log.debug("chooseItem(array of size {},{},{})", items.size(),clusterRecordId,patronRequest);

		long distanceFromPickupKM = 10000000;
		Item selectedItem = null;
                for ( Item item : items ) {
			if ( item.getIsRequestable() && item.hasNoHolds() ) {
				// Item is requestable and has no holds....
				log.debug("Attempt to calc distance to {}",item);
				if ( ( item.getLocation() != null ) &&
				     ( item.getLocation().getLatitude() != null ) &&
				     ( item.getLocation().getLongitude() != null ) ) {
					log.debug("Item has geo propeties");	
					// Calculate distance from pickup location to this agency
					long distance = (long) 0; // distance...
					if ( distance < distanceFromPickupKM ) {
						log.debug("We have a new closest item");
						selectedItem = item;
					}
				}
				else {
					if ( selectedItem == null ) {
						log.debug("Selecting the first item even though it doesn't have geo props");
						selectedItem = item;
					}
				}
			}
		}

		if ( selectedItem == null )
			throw new NoItemsRequestableAtAnyAgency(clusterRecordId);

		return selectedItem;
	}


	private static double distance(double lat1, double lon1, double lat2, double lon2, String unit) {
		if ((lat1 == lat2) && (lon1 == lon2)) {
			return 0;
		}
		else {
			double theta = lon1 - lon2;
			double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + 
				Math.cos(Math.toRadians(lat1)) * 
				Math.cos(Math.toRadians(lat2)) * 
				Math.cos(Math.toRadians(theta));
			dist = Math.acos(dist);
			dist = Math.toDegrees(dist);
			dist = dist * 60 * 1.1515;
			if (unit.equals("K")) {
				dist = dist * 1.609344;
			} else if (unit.equals("N")) {
				dist = dist * 0.8684;
			}
			return (dist);
		}
	}
}
