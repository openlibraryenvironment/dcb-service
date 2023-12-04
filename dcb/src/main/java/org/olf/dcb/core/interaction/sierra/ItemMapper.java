package org.olf.dcb.core.interaction.sierra;

import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.interaction.shared.NumericItemTypeMapper;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;

import jakarta.inject.Singleton;

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
}
