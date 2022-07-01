package com.volmit.adapt.util.advancements.save;

import com.volmit.adapt.util.advancements.NameKey;
import com.volmit.adapt.util.advancements.advancement.criteria.CriteriaType;

/**
 * Represents the Save Data for an Advancement saved by {@link CriteriaType} NUMBER
 * 
 * @author Axel
 *
 */
public class ProgressData {
	
	private final NameKey name;
	private final int progress;
	
	/**
	 * Constructor for creating ProgressData
	 * 
	 * @param name The Unique Name of the Advancement
	 * @param progress The Progress
	 */
	public ProgressData(NameKey name, int progress) {
		this.name = name;
		this.progress = progress;
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
	 * Gets the Progress
	 * 
	 * @return The Progress
	 */
	public int getProgress() {
		return progress;
	}
	
}