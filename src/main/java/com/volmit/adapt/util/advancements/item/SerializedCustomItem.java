package com.volmit.adapt.util.advancements.item;

import com.volmit.adapt.util.advancements.NameKey;
import org.bukkit.Material;

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