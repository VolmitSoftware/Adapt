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

package com.volmit.adapt.util.advancements.advancement.serialized;

import java.util.List;

import com.volmit.adapt.util.advancements.CrazyAdvancementsAPI;
import com.volmit.adapt.util.advancements.NameKey;
import com.volmit.adapt.util.advancements.advancement.AdvancementFunctionReward;

public class SerializedAdvancement {
	
	private final transient NameKey name;
	private final SerializedAdvancementDisplay display;
	private final SerializedCriteria criteria;
	private final AdvancementFunctionReward reward;
	private final NameKey parent;
	private final List<String> flags;
	
	public SerializedAdvancement(NameKey name, SerializedAdvancementDisplay display, SerializedCriteria criteria, AdvancementFunctionReward reward, NameKey parent, List<String> flags) {
		this.name = name;
		this.display = display;
		this.criteria = criteria;
		this.reward = reward;
		this.parent = parent;
		this.flags = flags;
	}
	
	public NameKey getName() {
		return name;
	}
	
	public SerializedAdvancementDisplay getDisplay() {
		return display;
	}
	
	public SerializedCriteria getCriteria() {
		return criteria;
	}
	
	public AdvancementFunctionReward getReward() {
		return reward;
	}
	
	public NameKey getParent() {
		return parent;
	}
	
	public List<String> getFlags() {
		return flags;
	}
	
	public String toJson() {
		return CrazyAdvancementsAPI.getGson().toJson(this);
	}
	
}