package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.olf.dcb.storage.LocationRepository;

@Singleton
public class GeoDistanceResolutionStrategy implements ResolutionStrategy {

	private static final Logger log = LoggerFactory.getLogger(GeoDistanceResolutionStrategy.class);

	public static final String GEO_DISTANCE_STRATEGY = "Geo";
	private LocationRepository locationRepository;

	private static class ItemWithDistance {
		public ItemWithDistance(Item item, double distance) { this.item = item; this.distance = distance; };
		public Item item;
		public double distance;
	};


	public GeoDistanceResolutionStrategy(LocationRepository locationRepository) {
		this.locationRepository = locationRepository;
	}

	@Override
	public String getCode() {
		return GEO_DISTANCE_STRATEGY;
	}


	@Override
	public Mono<Item> chooseItem(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {

		log.debug("chooseItem(array of size {},{},{})", items.size(),clusterRecordId,patronRequest);

		if ( patronRequest.getPickupLocationCode() == null )
			return Mono.error(new RuntimeException("No pickup location code"));

		// Look up location by code
		return Mono.from(locationRepository.findOneByCode(patronRequest.getPickupLocationCode()))
			// Create an ItemWithDistance for each item that calculates the distance to pickup_location
			.flatMapMany(pickup_location ->
				Flux.fromIterable(items)
					.filter( item -> ( item.getIsRequestable() && item.hasNoHolds() ) )
					.map( item -> {
						log.debug("Calculating distance from {} to {}",pickup_location,item);
						double distance = 10000000;
						if ( ( item.getLocation() != null ) &&
				  	 	     ( item.getLocation().getLatitude() != null ) &&
				    	 	     ( item.getLocation().getLongitude() != null ) ) {
						// Item has a geo location so calculate distance for real
						}
						return new ItemWithDistance(item, distance);
					}))
			// Reduce to the closest one
			.reduce((o1, o2) -> o1.distance < o2.distance ? o1 : o2 )
			.map( itemWithDistance -> itemWithDistance.item );
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
