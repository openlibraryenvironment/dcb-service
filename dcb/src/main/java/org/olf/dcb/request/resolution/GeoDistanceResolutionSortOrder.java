package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.LocationRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class GeoDistanceResolutionSortOrder implements ResolutionSortOrder {
	private final LocationRepository locationRepository;

	public GeoDistanceResolutionSortOrder(LocationRepository locationRepository) {
		this.locationRepository = locationRepository;
	}

	@Override
	public String getCode() {
		return CODE_GEO_DISTANCE;
	}

	@Override
	public Mono<List<Item>> sortItems(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		log.debug("sortItems(array of size {},{},{})", items.size(), clusterRecordId, patronRequest);

		if (patronRequest.getPickupLocationCode() == null) {
			log.error("The patron request has no pickup location code");
			return Mono.error(new RuntimeException("No pickup location code"));
		}

		final var pickupLocationId = UUID.fromString(patronRequest.getPickupLocationCode());

		// Look up location by code
		return Mono.from(locationRepository.findById(pickupLocationId))
			// Create an SupplyCandidateItem for each item that calculates the distance to pickupLocation
			.flatMapMany(pickupLocation ->
				Flux.fromIterable(items)
					.filter(Item::hasAgency)
					.map (item ->
						SupplyCandidateItem.builder()
							.item(item)
							.pickupLocation(pickupLocation)
							.build())
					.map(this::calculateDistanceFromPickupLocation))
			.sort(new SupplyCandidateItemComparator())
			.map(SupplyCandidateItem::getItem)
			.collectList();
	}

	private SupplyCandidateItem calculateDistanceFromPickupLocation(SupplyCandidateItem iwd) {
		final var item = getValueOrNull(iwd, SupplyCandidateItem::getItem);

		final var agency = getValueOrNull(item, Item::getAgency);
		final var pickupLocation = getValueOrNull(iwd, SupplyCandidateItem::getPickupLocation);

		iwd.setDistance(calculateDistance(agency, pickupLocation));
		log.debug("Distance:{}", iwd.getDistance());

		return iwd;
	}

	private static double calculateDistance(DataAgency agency, Location pickupLocation) {
		log.debug("calculateDistance({}, {})", agency, pickupLocation);

		final var itemLatitude = getValueOrNull(agency, DataAgency::getLatitude);
		final var itemLongitude = getValueOrNull(agency, DataAgency::getLongitude);

		final var pickupLocationLatitude = getValueOrNull(pickupLocation, Location::getLatitude);
		final var pickupLocationLongitude = getValueOrNull(pickupLocation, Location::getLongitude);

		if ((itemLatitude != null) &&
			(itemLongitude != null) &&
			(pickupLocationLatitude != null) &&
			(pickupLocationLongitude != null)) {

			// Item and pickup location have geo location so calculate distance for real
			return distance(itemLatitude, itemLongitude, pickupLocationLatitude,
				pickupLocationLongitude, "K");
			
		} else {
			// No distance available - put to the back of the queue
			log.warn("Agency has no location data... Unable to calculate");
			return 10000000;
		}
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
