package com.volmit.adapt.nms.advancements.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Called when a Player closes their Advancement Screen
 * 
 * @author Axel
 *
 */
public class AdvancementScreenCloseEvent extends Event {
	
	public static final HandlerList handlers = new HandlerList();
	
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
	public static HandlerList getHandlerList() {
		return handlers;
	}
	
	private final Player player;
	
	/**
	 * Constructor for instantiating this Event
	 * 
	 * @param player The Player closing their Advancement Screen
	 */
	public AdvancementScreenCloseEvent(Player player) {
		super(true);
		this.player = player;
	}
	
	/**
	 * Gets the Player closing their Advancement Screen
	 * 
	 * @return The Player
	 */
	public Player getPlayer() {
		return player;
	}
	
	/**
	 * Gets an informative String for debugging purposes
	 * 
	 * @return Information about this event
	 */
	public String getInformationString() {
		return "tab_action=close;player=" + player.getName();
	}
	
}