package com.volmit.adapt.nms.advancements.save;

import java.util.List;

import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.criteria.CriteriaType;

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