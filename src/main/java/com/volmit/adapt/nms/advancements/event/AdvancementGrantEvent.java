package com.volmit.adapt.nms.advancements.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.volmit.adapt.nms.advancements.advancement.Advancement;
import com.volmit.adapt.nms.advancements.advancement.AdvancementFlag;
import com.volmit.adapt.nms.advancements.manager.AdvancementManager;

public class AdvancementGrantEvent extends Event {

	public static final HandlerList handlers = new HandlerList();
	
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
	public static HandlerList getHandlerList() {
		return handlers;
	}
	
	
	private final AdvancementManager manager;
	private final Advancement advancement;
	private final Player player;
	private boolean showToast;
	private boolean displayMessage;
	
	public AdvancementGrantEvent(AdvancementManager manager, Advancement advancement, Player player) {
		this.manager = manager;
		this.advancement = advancement;
		this.player = player;
		this.showToast = advancement.hasFlag(AdvancementFlag.SHOW_TOAST);
		this.displayMessage = advancement.hasFlag(AdvancementFlag.DISPLAY_MESSAGE);
	}
	
	/**
	 * Gets the Manager
	 * 
	 * @return The Manager this event has been fired from
	 */
	public AdvancementManager getManager() {
		return manager;
	}
	
	/**
	 * Gets the Advancement
	 * 
	 * @return The Advancement that has been granted
	 */
	public Advancement getAdvancement() {
		return advancement;
	}
	
	/**
	 * Gets the Player
	 * 
	 * @return Reciever
	 */
	public Player getPlayer() {
		return player;
	}
	
	/**
	 * Gets whether a toast will be shown
	 * 
	 * @return true if a toast will be shown
	 */
	public boolean isShowToast() {
		return showToast;
	}
	
	/**
	 * Sets if a toast will be shown
	 * 
	 * @param showToast Whether toast should be shown
	 */
	public void setShowToast(boolean showToast) {
		this.showToast = showToast;
	}
	
	/**
	 * Gets whether a message will be displayed
	 * 
	 * @return true if a message will be displayed
	 */
	public boolean isDisplayMessage() {
		return displayMessage;
	}
	
	/**
	 * Sets if a message will be displayed
	 * 
	 * @param displayMessage Whethere message should be displayed
	 */
	public void setDisplayMessage(boolean displayMessage) {
		this.displayMessage = displayMessage;
	}
	
}