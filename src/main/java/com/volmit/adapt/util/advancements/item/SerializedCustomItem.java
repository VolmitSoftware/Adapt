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