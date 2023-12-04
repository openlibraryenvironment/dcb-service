package org.olf.dcb.core.interaction.sierra;

import org.olf.dcb.core.interaction.shared.ItemStatusMapper;
import org.olf.dcb.core.interaction.shared.NumericItemTypeMapper;

import jakarta.inject.Singleton;

@Singleton
public class ItemMapper {
	private final ItemStatusMapper itemStatusMapper;
	private final NumericItemTypeMapper itemTypeMapper;

	public ItemMapper(ItemStatusMapper itemStatusMapper,
		NumericItemTypeMapper itemTypeMapper) {
		
		this.itemStatusMapper = itemStatusMapper;
		this.itemTypeMapper = itemTypeMapper;
	}
}
