package org.olf.dcb.core.interaction.sierra;

import static io.micronaut.core.util.StringUtils.isNotEmpty;

import java.time.Instant;

import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.interaction.shared.NumericItemTypeMapper;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import services.k_int.interaction.sierra.items.SierraItem;

@Singleton
public class ItemMapper {
	private final ItemStatusMapper itemStatusMapper;
	private final NumericItemTypeMapper itemTypeMapper;
	private final LocationToAgencyMappingService locationToAgencyMappingService;

	public ItemMapper(ItemStatusMapper itemStatusMapper, NumericItemTypeMapper itemTypeMapper,
		LocationToAgencyMappingService locationToAgencyMappingService) {

		this.itemStatusMapper = itemStatusMapper;
		this.itemTypeMapper = itemTypeMapper;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
	}

	@Nullable
	public Instant parsedDueDate(SierraItem result) {
		final var dueDate = result.getStatus().getDuedate();

		return isNotEmpty(dueDate)
			? Instant.parse(dueDate)
			: null;
	}
}
