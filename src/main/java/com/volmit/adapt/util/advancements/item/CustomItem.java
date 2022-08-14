/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.adapt.util.advancements.item;

import com.volmit.adapt.util.advancements.NameKey;
import org.bukkit.Material;

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