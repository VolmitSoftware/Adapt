package com.volmit.adapt.nms.advancements.item;

import org.bukkit.Material;

import com.volmit.adapt.nms.advancements.NameKey;

public class CustomItem {
	
	private final NameKey name;
	private final Material type;
	private final int customModelData;
	
	public CustomItem(NameKey name, Material type, int customModelData) {
		if(name == null) {
			throw new RuntimeException("Custom Item Name may not be null");
		}
		if(type == null) {
			throw new RuntimeException("Custom Item Type may not be null");
		}
		if(!type.isItem()) {
			throw new RuntimeException("Can't create Custom Item from non-item Type '" + type.name().toLowerCase() + "'");
		}
		this.name = name;
		this.type = type;
		this.customModelData = customModelData;
	}
	
	public NameKey getName() {
		return name;
	}
	
	public Material getType() {
		return type;
	}
	
	public int getCustomModelData() {
		return customModelData;
	}
	
	public SerializedCustomItem serialize() {
		return new SerializedCustomItem(type.name().toLowerCase(), customModelData);
	}
	
}