package com.volmit.adapt.nms.advancements.advancement.serialized;

import com.volmit.adapt.nms.advancements.advancement.criteria.Criteria;

public class SerializedCriteria {
	
	private int requiredNumber;
	private final String[] actionNames;
	private final String[][] requirements;
	
	public SerializedCriteria(int requiredNumber) {
		this.requiredNumber = requiredNumber;
		this.actionNames = null;
		this.requirements = null;
	}
	
	public SerializedCriteria(String[] actionNames, String[][] requirements) {
		this.actionNames = actionNames;
		this.requirements = requirements;
	}
	
	public int getRequiredNumber() {
		return requiredNumber;
	}
	
	public String[] getActionNames() {
		return actionNames;
	}
	
	public String[][] getRequirements() {
		return requirements;
	}
	
	public Criteria deserialize() {
		if(actionNames != null && requirements != null) {
			return new Criteria(actionNames, requirements);
		} else {
			return new Criteria(requiredNumber);
		}
	}
	
}