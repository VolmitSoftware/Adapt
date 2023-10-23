package com.volmit.adapt.nms.advancements.advancement.serialized.message;

public class Score {
	
	private final String name;
	private final String objective;
	
	public Score(String name, String objective) {
		this.name = name;
		this.objective = objective;
	}
	
	public String getName() {
		return name;
	}
	
	public String getObjective() {
		return objective;
	}
	
}