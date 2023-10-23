package com.volmit.adapt.nms.advancements.advancement.serialized.message;

public class ClickEvent {
	
	private final String action;
	private final String value;
	
	public ClickEvent(String action, String value) {
		this.action = action;
		this.value = value;
	}
	
	public String getAction() {
		return action;
	}
	
	public String getValue() {
		return value;
	}
	
}