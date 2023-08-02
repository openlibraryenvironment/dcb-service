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

	private final ItemStatusMapper itemStatusMapper;

	ItemResultToItemMapper(ItemStatusMapper itemStatusMapper) {
		this.itemStatusMapper = itemStatusMapper;
	}

	Mono<org.olf.dcb.core.model.Item> mapResultToItem(SierraItem result, String hostLmsCode, String bibId) {
		// log.debug("mapResultToItem({}, {})", result, hostLmsCode);

			final var dueDate = result.getStatus().getDuedate();

			final var parsedDueDate = isNotEmpty(dueDate)
				? ZonedDateTime.parse(dueDate)
				: null;

			return itemStatusMapper.mapStatus(result.getStatus(), hostLmsCode)
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
					.build());
	}
}
