package org.olf.reshare.dcb.core.interaction.sierra;

import static io.micronaut.core.util.StringUtils.isNotEmpty;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import services.k_int.interaction.sierra.items.Result;

class ItemResultToItemMapper {
	private static final Logger log = LoggerFactory.getLogger(ItemResultToItemMapper.class);

	private final ItemStatusMapper itemStatusMapper = new ItemStatusMapper();

	org.olf.reshare.dcb.core.model.Item mapResultToItem(Result result, String hostLmsCode) {
		log.debug("mapResultToItem({}, {})", result, hostLmsCode);

		final var dueDate = result.getStatus().getDuedate();

		final var parsedDueDate = isNotEmpty(dueDate)
			? ZonedDateTime.parse(dueDate)
			:null;

		return org.olf.reshare.dcb.core.model.Item.builder()
			.id(result.getId())
			.status(itemStatusMapper.mapStatus(result.getStatus()))
			.dueDate(parsedDueDate)
			.location(org.olf.reshare.dcb.core.model.Location.builder()
				.code(result.getLocation().getCode())
				.name(result.getLocation().getName())
				.build())
			.barcode(result.getBarcode())
			.callNumber(result.getCallNumber())
			.hostLmsCode(hostLmsCode)
			.holdCount(result.getHoldCount())
			.build();
	}
}
