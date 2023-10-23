package com.volmit.adapt.nms.advancements.advancement;

/**
 * Alters the behavior of Advancements
 * 
 * @author Axel
 *
 */
public enum AdvancementFlag {
	
	/**
	 * Advancements with this Flag will display a Toast upon Completion
	 */
	SHOW_TOAST,
	/**
	 * Advancements with this Flag will broadcast a Message in Chat upon Completion
	 */
	DISPLAY_MESSAGE,
	/**
	 * Advancements with this Flag will be sent with the hidden boolean set to true allowing the creation of empty Advancement Tabs or to draw lines
	 */
	SEND_WITH_HIDDEN_BOOLEAN,
	
	;
	
	/**
	 * Shorthand for combining Toast Notifications and Chat Messages
	 */
	public static final AdvancementFlag[] TOAST_AND_MESSAGE = new AdvancementFlag[] {SHOW_TOAST, DISPLAY_MESSAGE};
	
}