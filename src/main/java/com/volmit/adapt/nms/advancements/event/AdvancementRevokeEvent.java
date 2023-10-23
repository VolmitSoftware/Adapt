package com.volmit.adapt.nms.advancements.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.volmit.adapt.nms.advancements.advancement.Advancement;
import com.volmit.adapt.nms.advancements.manager.AdvancementManager;

public class AdvancementRevokeEvent extends Event {

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
	
	public AdvancementRevokeEvent(AdvancementManager manager, Advancement advancement, Player player) {
		this.manager = manager;
		this.advancement = advancement;
		this.player = player;
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
	
}