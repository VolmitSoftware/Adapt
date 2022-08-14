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

package com.volmit.adapt.util.advancements.save;

import java.util.List;

import com.volmit.adapt.util.advancements.NameKey;
import com.volmit.adapt.util.advancements.advancement.criteria.CriteriaType;

/**
 * Represents the Save Data for an Advancement saved by {@link CriteriaType} LIST
 * 
 * @author Axel
 *
 */
public class CriteriaData {
	
	private final NameKey name;
	private final List<String> criteria;
	
	/**
	 * Constructor for creating CriteriaData
	 * 
	 * @param name The Unique Name of the Advancement
	 * @param criteria The Criteria that has been awarded
	 */
	public CriteriaData(NameKey name, List<String> criteria) {
		this.name = name;
		this.criteria = criteria;
	}
	
	/**
	 * Gets the Unique Name of the Advancement
	 * 
	 * @return The Unique Name
	 */
	public NameKey getName() {
		return name;
	}
	
	/**
	 * Gets the Criteria that has been awarded
	 * 
	 * @return The Criteria
	 */
	public List<String> getCriteria() {
		return criteria;
	}
	
}