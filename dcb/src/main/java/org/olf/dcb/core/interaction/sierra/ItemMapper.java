package org.olf.dcb.core.interaction.sierra;

import org.olf.dcb.core.interaction.shared.ItemStatusMapper;

import jakarta.inject.Singleton;

@Singleton
public class ItemMapper {
	private final ItemStatusMapper itemStatusMapper;

	public ItemMapper(ItemStatusMapper itemStatusMapper) {
		this.itemStatusMapper = itemStatusMapper;
	}
}
