package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;
import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.olf.dcb.storage.LocationRepository;
import org.olf.dcb.storage.AgencyRepository;

@Singleton
public class GeoDistanceResolutionStrategy implements ResolutionStrategy {

	private static final Logger log = LoggerFactory.getLogger(GeoDistanceResolutionStrategy.class);

	public static final String GEO_DISTANCE_STRATEGY = "Geo";
	private LocationRepository locationRepository;
	private AgencyRepository agencyRepository;

	public GeoDistanceResolutionStrategy(
		LocationRepository locationRepository,
		AgencyRepository agencyRepository
	) {
		this.locationRepository = locationRepository;
		this.agencyRepository = agencyRepository;
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
					.map ( item -> {
						return ItemWithDistance.builder()
							.item(item)
							.build();
					})
					// Look up the items holding agency
					.flatMap( this::decorateWithAgency )
					// Calculate the distance from the pickup location to the holding agency
					.flatMap( this::calculateDistance ))
			// Reduce to the closest one
			.reduce((o1, o2) -> o1.getDistance() < o2.getDistance() ? o1 : o2 )
			.map( itemWithDistance -> itemWithDistance.getItem() );
	}

	// Decorate the ItemWithDistance with the agency that holds the item (And hence, the location of that agency)
        private Mono<ItemWithDistance> decorateWithAgency(ItemWithDistance iwd) {
		log.debug("decorateWithAgency({})",iwd.getItem().getAgencyCode());
		return Mono.from(agencyRepository.findOneByCode(iwd.getItem().getAgencyCode()))
			.map ( agency -> {
				return iwd.setItemAgency(agency);
			});
	}

	// Do the actual distance calculation
        private Mono<ItemWithDistance> calculateDistance(ItemWithDistance iwd) {
		log.debug("calculateDistance({})",iwd);

	// .map( itemWithDistance -> {
	// log.debug("Calculating distance {}",itemWithDistance);
	// double distance = 10000000;
	// if ( ( item.getLocation() != null ) &&
	// ( item.getLocation().getLatitude() != null ) &&
	// ( item.getLocation().getLongitude() != null ) ) {
	// // Item has a geo location so calculate distance for real
	// }
	// return itemWithDistance;
	// }))
		return Mono.just(iwd.setDistance(10000000));
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
