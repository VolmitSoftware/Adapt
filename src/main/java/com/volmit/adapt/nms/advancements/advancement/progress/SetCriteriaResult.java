package com.volmit.adapt.nms.advancements.advancement.progress;

import com.volmit.adapt.nms.advancements.advancement.criteria.CriteriaType;

/**
 * Represents the Result to an Operation where Criteria is set
 * 
 * @author Axel
 *
 */
public enum SetCriteriaResult {
	
	/**
	 * Operations with this Result did not lead to any changes
	 */
	UNCHANGED,
	
	/**
	 * Operations with this Result did lead to changes, but did not lead to the Advancement being completed
	 */
	CHANGED,
	
	/**
	 * Operations with this Result did lead to the Advancement being completed
	 */
	COMPLETED,
	
	/**
	 * Operations with this Result could not be processed because the {@link CriteriaType} did not match
	 */
	INVALID,
	
}