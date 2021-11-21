package eu.endercentral.crazy_advancements.events.offline;

import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import eu.endercentral.crazy_advancements.Advancement;
import eu.endercentral.crazy_advancements.manager.AdvancementManager;

public class OfflineCriteriaProgressChangeEvent extends Event {
	
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
	private final UUID uuid;
	private final int progressBefore;
	private int progress;
	
	public OfflineCriteriaProgressChangeEvent(AdvancementManager manager, Advancement advancement, UUID uuid, int progressBefore, int progress) {
		this.manager = manager;
		this.advancement = advancement;
		this.uuid = uuid;;
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
	 * @return Reciever UUID
	 */
	public UUID getUUID() {
		return uuid;
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