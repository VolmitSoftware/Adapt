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

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Save File
 * 
 * @author Axel
 *
 */
public class SaveFile {
	
	private static final Gson gson = new Gson();
	
	private Map<String, ProgressData> progressData;
	private Map<String, CriteriaData> criteriaData;
	
	/**
	 * Constructor for creating a Save File
	 * 
	 * @param progressData A list of Advancement Progress that is saved by progress number
	 * @param criteriaData A list of Advancement Progress that is saved by criteria list
	 */
	public SaveFile(List<ProgressData> progressData, List<CriteriaData> criteriaData) {
		this.progressData = new HashMap<>();
		for(ProgressData progress : progressData) {
			this.progressData.put(progress.getName().toString(), progress);
		}
		
		this.criteriaData = new HashMap<>();
		for(CriteriaData criteria : criteriaData) {
			this.criteriaData.put(criteria.getName().toString(), criteria);
		}
	}
	
	/**
	 * Gets a list of Advancement Progress that is saved by progress number
	 * 
	 * @return The list containing {@link ProgressData}
	 */
	public Collection<ProgressData> getProgressData() {
		if(progressData == null) progressData = new HashMap<>();
		return progressData.values();
	}
	
	/**
	 * Gets a list of Advancement Progress that is saved by criteria list
	 * 
	 * @return The list containing {@link CriteriaData}
	 */
	public Collection<CriteriaData> getCriteriaData() {
		if(criteriaData == null) criteriaData = new HashMap<>();
		return criteriaData.values();
	}
	
	/**
	 * Merges another Save File onto this one<br>
	 * The Save File that is merged will take priority
	 * 
	 * @param saveFile The Save File that should be merged into this one
	 */
	public void merge(SaveFile saveFile) {
		//Merge Progress Data
		for(ProgressData progress : saveFile.getProgressData()) {
			this.progressData.put(progress.getName().toString(), progress);
		}
		
		//Merge Criteria Data
		for(CriteriaData criteria : saveFile.getCriteriaData()) {
			this.criteriaData.put(criteria.getName().toString(), criteria);
		}
	}
	
	/**
	 * Converts this Save File to JSON
	 * 
	 * @return the JSON String
	 */
	public String toJson() {
		return gson.toJson(this);
	}
	
	/**
	 * Creates a Save File from JSON Input
	 * 
	 * @param json The Input JSON
	 * @return The newly created Save File
	 */
	public static SaveFile fromJSON(String json) {
		return gson.fromJson(json, SaveFile.class);
	}
	
	/**
	 * Creates a Save File from JSON Input
	 * 
	 * @param json The Input JSON
	 * @return The newly created Save File
	 */
	public static SaveFile fromJSON(JsonElement json) {
		return gson.fromJson(json, SaveFile.class);
	}
	
}