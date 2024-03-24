package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.LocationRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class GeoDistanceResolutionStrategy implements ResolutionStrategy {
	private final LocationRepository locationRepository;
	private final AgencyRepository agencyRepository;

	public GeoDistanceResolutionStrategy(LocationRepository locationRepository,
		AgencyRepository agencyRepository) {

		this.locationRepository = locationRepository;
		this.agencyRepository = agencyRepository;
	}

	@Override
	public String getCode() {
		return "Geo";
	}

	@Override
	public Mono<Item> chooseItem(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		log.debug("chooseItem(array of size {},{},{})", items.size(), clusterRecordId, patronRequest);

		if (patronRequest.getPickupLocationCode() == null) {
			log.error("The patron request has no pickup location code");
			return Mono.error(new RuntimeException("No pickup location code"));
		}

		final var pickupLocationId = UUID.fromString(patronRequest.getPickupLocationCode());

		// Look up location by code
		return Mono.from(locationRepository.findById(pickupLocationId))
			// Create an ItemWithDistance for each item that calculates the distance to pickup_location
			.flatMapMany(pickup_location ->
				Flux.fromIterable(items)
					.filter(item -> (item.getIsRequestable() && item.hasNoHolds() && (item.getAgencyCode() != null)))
					.map (item ->
						ItemWithDistance.builder()
							.item(item)
							.pickupLocation(pickup_location)
							.build())
					// Look up the items holding agency
					.flatMap(this::decorateWithAgency)
					// Calculate the distance from the pickup location to the holding agency
					.flatMap(this::calculateDistance))
			// Reduce to the closest one
			.reduce((o1, o2) -> o1.getDistance() < o2.getDistance() ? o1 : o2)
			.map(ItemWithDistance::getItem);
	}

	// Decorate the ItemWithDistance with the agency that holds the item (And hence, the location of that agency)
  private Mono<ItemWithDistance> decorateWithAgency(ItemWithDistance iwd) {
		log.debug("decorateWithAgency({})", iwd.getItem().getAgencyCode());

		return Mono.from(agencyRepository.findOneByCode(iwd.getItem().getAgencyCode()))
			.map(iwd::setItemAgency);
	}

	// Do the actual distance calculation
  private Mono<ItemWithDistance> calculateDistance(ItemWithDistance iwd) {
		log.debug("calculateDistance({},{})", iwd.getItemAgency(), iwd.getPickupLocation());

		if ((iwd.getItemAgency() != null) &&
			(iwd.getItemAgency().getLatitude() != null) &&
			(iwd.getItemAgency().getLongitude() != null) &&
			(iwd.getPickupLocation() != null) &&
			(iwd.getPickupLocation().getLatitude() != null) &&
			(iwd.getPickupLocation().getLongitude() != null)) {
			// Item has a geo location so calculate distance for real
			iwd.setDistance(distance(
				iwd.getItemAgency().getLatitude().doubleValue(),
				iwd.getItemAgency().getLongitude().doubleValue(),
				iwd.getPickupLocation().getLatitude().doubleValue(),
				iwd.getPickupLocation().getLongitude().doubleValue(), "K"));
		} else {
			// No distance available - put to the back of the queue
			log.warn("Agency has no location data... Unable to calculate");
			iwd.setDistance(10000000);
		}
		log.debug("Distance:{}", iwd.getDistance());

		return Mono.just(iwd);
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
