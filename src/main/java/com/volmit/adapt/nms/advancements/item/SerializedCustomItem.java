package com.volmit.adapt.nms.advancements.item;

import org.bukkit.Material;

import com.volmit.adapt.nms.advancements.NameKey;

public class SerializedCustomItem {
	
	private final String item;
	private final int customModelData;
	
	public SerializedCustomItem(String item, int customModelData) {
		this.item = item;
		this.customModelData = customModelData;
	}
	
	public String getItem() {
		return item;
	}
	
	public int getCustomModelData() {
		return customModelData;
	}
	
	public CustomItem deserialize(NameKey name) {
		Material type = Material.matchMaterial(getItem());
		return new CustomItem(name, type, customModelData);
	}
	
}