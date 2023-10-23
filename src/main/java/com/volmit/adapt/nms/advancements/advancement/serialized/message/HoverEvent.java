package com.volmit.adapt.nms.advancements.advancement.serialized.message;

public class HoverEvent {
	
	private final String action;
	private final String contents;
	
	public HoverEvent(String action, String contents) {
		this.action = action;
		this.contents = contents;
	}
	
	public String getAction() {
		return action;
	}
	
	public String getContents() {
		return contents;
	}
	
	
}