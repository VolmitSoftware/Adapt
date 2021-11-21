package eu.endercentral.crazy_advancements.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import eu.endercentral.crazy_advancements.Advancement;
import eu.endercentral.crazy_advancements.manager.AdvancementManager;

public class CriteriaProgressChangeEvent extends Event {
	
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
	private final int progressBefore;
	private int progress;
	
	public CriteriaProgressChangeEvent(AdvancementManager manager, Advancement advancement, Player player, int progressBefore, int progress) {
		this.manager = manager;
		this.advancement = advancement;
		this.player = player;
		this.progressBefore = progressBefore;
		this.progress = progress;
	}
	
	/**
	 * 
	 * @return The Manager this event has been fired from
	 */
	public AdvancementManager getManager() {
		return manager;
	}
	
	/**
	 * 
	 * @return The Advancement that has been granted
	 */
	public Advancement getAdvancement() {
		return advancement;
	}
	
	/**
	 * 
	 * @return Reciever
	 */
	public Player getPlayer() {
		return player;
	}
	
	/**
	 * 
	 * @return The progress before it has been changed
	 */
	public int getProgressBefore() {
		return progressBefore;
	}
	
	/**
	 * 
	 * @return The new progress
	 */
	public int getProgress() {
		return progress;
	}
	
	/**
	 * Sets the progress
	 * 
	 * @param progress The new progress
	 */
	public void setProgress(int progress) {
		this.progress = progress;
	}
	
	
	
}