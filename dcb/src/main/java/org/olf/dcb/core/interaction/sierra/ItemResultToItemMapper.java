package org.olf.dcb.core.interaction.sierra;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.items.SierraItem;

import java.time.ZonedDateTime;

import static io.micronaut.core.util.StringUtils.isNotEmpty;

@Singleton
class ItemResultToItemMapper {
	private static final Logger log = LoggerFactory.getLogger(ItemResultToItemMapper.class);

	private final SierraItemStatusMapper sierraItemStatusMapper;
	private final SierraItemTypeMapper sierraItemTypeMapper;

	ItemResultToItemMapper(SierraItemStatusMapper sierraItemStatusMapper,
                               SierraItemTypeMapper sierraItemTypeMapper) {
		this.sierraItemStatusMapper = sierraItemStatusMapper;
		this.sierraItemTypeMapper = sierraItemTypeMapper;
	}

	Mono<org.olf.dcb.core.model.Item> mapResultToItem(SierraItem result, String hostLmsCode, String bibId) {
		// log.debug("mapResultToItem({}, {})", result, hostLmsCode);

			final var dueDate = result.getStatus().getDuedate();

			final var parsedDueDate = isNotEmpty(dueDate)
				? ZonedDateTime.parse(dueDate)
				: null;

			return sierraItemStatusMapper.mapStatus(result.getStatus(), hostLmsCode)
				.map(itemStatus -> org.olf.dcb.core.model.Item.builder()
					.id(result.getId())
					.status(itemStatus)
					.dueDate(parsedDueDate)
					.location(org.olf.dcb.core.model.Location.builder()
						.code(result.getLocation().getCode().trim())
						.name(result.getLocation().getName())
						.build())
					.barcode(result.getBarcode())
					.callNumber(result.getCallNumber())
					.hostLmsCode(hostLmsCode)
					.holdCount(result.getHoldCount())
					.bibId(bibId)
                                        .localItemType(result.getItemType())
                                        .deleted(result.getDeleted())
                                        .suppressed(result.getSuppressed())
					.build())
                                .flatMap(item -> enrichItemWithMappedItemType(item, hostLmsCode))
                                ;
	}

        Mono<org.olf.dcb.core.model.Item> enrichItemWithMappedItemType(org.olf.dcb.core.model.Item item, String hostSystem) {
                return sierraItemTypeMapper.getCanonicalItemType(hostSystem, item.getLocalItemType())
                        .defaultIfEmpty("UNKNOWN")
                        .map( mappedType -> item.setCanonicalItemType(mappedType) );
        }
}
