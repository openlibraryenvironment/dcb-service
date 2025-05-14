package org.olf.dcb.core.interaction.folio;

import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import org.olf.dcb.core.model.DerivedLoanPolicy;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class ConsortialFolioItemMapper {
	private final LocationToAgencyMappingService locationToAgencyMappingService;
	private final MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService;

	public ConsortialFolioItemMapper(
		LocationToAgencyMappingService locationToAgencyMappingService,
		MaterialTypeToItemTypeMappingService materialTypeToItemTypeMappingService) {
		this.locationToAgencyMappingService = locationToAgencyMappingService;
		this.materialTypeToItemTypeMappingService = materialTypeToItemTypeMappingService;
	}

	public Mono<Item> mapHoldingToItem(Holding holding, String instanceId, String hostLmsCode) {
		log.debug("mapHoldingToItem({}, {}, {})", holding, instanceId, hostLmsCode);

		final var itemStatus = getValueOrNull(holding, Holding::getStatus);

		return mapStatus(itemStatus, hostLmsCode)
			.map(status -> buildItem(holding, instanceId, status))
			.flatMap(item -> locationToAgencyMappingService.enrichItemAgencyFromLocation(item, hostLmsCode))
			.flatMap(materialTypeToItemTypeMappingService::enrichItemWithMappedItemType)
			.doOnSuccess(item -> log.trace("Mapped holding to item: {}", item));
	}

	private Item buildItem(Holding holding, String instanceId, ItemStatus status) {
		final var dueDate = getValueOrNull(holding, Holding::getDueDate);

		return Item.builder()
			.localId(getValueOrNull(holding, Holding::getId))
			.localBibId(instanceId)
			.barcode(getValueOrNull(holding, Holding::getBarcode))
			.callNumber(getValueOrNull(holding, Holding::getCallNumber))
			.status(status)
			.dueDate(dueDate)
			.holdCount(getValueOrNull(holding, Holding::getTotalHoldRequests))
			.localItemType(getValue(holding, Holding::getMaterialType, MaterialType::getName, null))
			.localItemTypeCode(getValue(holding, Holding::getMaterialType, MaterialType::getName, null))
			.location( buildLocation(holding) )
			.rawVolumeStatement(getValueOrNull(holding, Holding::getVolume))
			.parsedVolumeStatement(getValueOrNull(holding, Holding::getVolume))
			.suppressed(getValueOrNull(holding, Holding::getSuppressFromDiscovery))
			.derivedLoanPolicy(DerivedLoanPolicy.GENERAL)
			.deleted(false)
			.build();
	}

	private Location buildLocation(Holding holding) {
		return Location.builder()
			.name(getValueOrNull(holding, Holding::getLocation))
			.code(getValueOrNull(holding, Holding::getLocationCode))
			.build();
	}

	public Mono<ItemStatus> mapStatus(String statusCode, String hostLmsCode) {
		log.debug("mapStatus(statusCode: {}, hostLmsCode: {})", statusCode, hostLmsCode);

		if (statusCode == null || (statusCode.isEmpty())) {
			return Mono.just(new ItemStatus(UNKNOWN));
		}

		return Mono.just( mapStatusCode(statusCode) )
			.map(ItemStatus::new);
	}

	private ItemStatusCode mapStatusCode(String statusCode) {
		return switch (statusCode) {
			case "Available",
           "Paged",
           "In transit",
           "Awaiting pickup",
           "Awaiting delivery" -> AVAILABLE;
			case "Checked out" -> CHECKED_OUT;
      case "Aged to lost",
           "Claimed returned",
           "Declared lost",
           "In process",
           "In process (non-requestable)",
           "Intellectual item",
           "Long missing",
           "Lost and paid",
           "Missing",
           "On order",
           "Restricted",
           "Unknown",
           "Withdrawn" -> UNAVAILABLE;
			default -> UNAVAILABLE;
		};
	}
}
