package com.volmit.adapt.nms.advancements.advancement.progress;

/**
 * Represents the Result to an Operation where Criteria is granted
 * 
 * @author Axel
 *
 */
public enum GrantCriteriaResult {
	
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
	
}