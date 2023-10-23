package com.volmit.adapt.nms.advancements.advancement;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.adapt.nms.advancements.CrazyAdvancementsAPI;
import com.volmit.adapt.nms.advancements.JSONMessage;
import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.AdvancementDisplay.AdvancementFrame;
import com.volmit.adapt.nms.advancements.advancement.criteria.Criteria;
import com.volmit.adapt.nms.advancements.advancement.progress.AdvancementProgress;
import com.volmit.adapt.nms.advancements.packet.ToastPacket;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * Represents a Toast Notification
 * 
 * @author Axel
 *
 */
public class ToastNotification {
	
	public static final NameKey NOTIFICATION_NAME = new NameKey(CrazyAdvancementsAPI.API_NAMESPACE, "notification");
	public static final Criteria NOTIFICATION_CRITERIA = new Criteria(1);
	public static final AdvancementProgress NOTIFICATION_PROGRESS = new AdvancementProgress(NOTIFICATION_CRITERIA.getRequirements());
	
	static {
		NOTIFICATION_PROGRESS.setCriteriaProgress(1);
	}
	
	private final ItemStack icon;
	private final JSONMessage message;
	private final AdvancementFrame frame;
	
	/**
	 * Constructor for creating Toast Notifications
	 * 
	 * @param icon The displayed Icon
	 * @param message The displayed Message
	 * @param frame Determines the displayed Title and Sound Effect (evaluated client-side and modifiable via resource packs)
	 */
	public ToastNotification(ItemStack icon, JSONMessage message, AdvancementFrame frame) {
		this.icon = icon;
		this.message = message;
		this.frame = frame;
	}
	
	/**
	 * Constructor for creating Toast Notifications
	 * 
	 * @param icon The displayed Icon
	 * @param message The displayed Message
	 * @param frame Determines the displayed Title and Sound Effect (evaluated client-side and modifiable via resource packs)
	 */
	public ToastNotification(ItemStack icon, String message, AdvancementFrame frame) {
		this.icon = icon;
		this.message = new JSONMessage(new TextComponent(message));
		this.frame = frame;
	}
	
	/**
	 * Constructor for creating Toast Notifications
	 * 
	 * @param icon The displayed Icon
	 * @param message The displayed Message
	 * @param frame Determines the displayed Title and Sound Effect (evaluated client-side and modifiable via resource packs)
	 */
	public ToastNotification(Material icon, JSONMessage message, AdvancementFrame frame) {
		this.icon = new ItemStack(icon);
		this.message = message;
		this.frame = frame;
	}
	
	/**
	 * Constructor for creating Toast Notifications
	 * 
	 * @param icon The displayed Icon
	 * @param message The displayed Message
	 * @param frame Determines the displayed Title and Sound Effect (evaluated client-side and modifiable via resource packs)
	 */
	public ToastNotification(Material icon, String message, AdvancementFrame frame) {
		this.icon = new ItemStack(icon);
		this.message = new JSONMessage(new TextComponent(message));
		this.frame = frame;
	}
	
	/**
	 * Gets the Icon
	 * 
	 * @return The Icon
	 */
	public ItemStack getIcon() {
		return icon;
	}
	
	/**
	 * Gets the TItle
	 * 
	 * @return The Title
	 */
	public JSONMessage getMessage() {
		return message;
	}
	
	/**
	 * Gets the Frame
	 * 
	 * @return The Frame
	 */
	public AdvancementFrame getFrame() {
		return frame;
	}
	
	/**
	 * Sends this Toast Notification to a Player
	 * 
	 * @param player The target Player
	 */
	public void send(Player player) {
		ToastPacket addPacket = new ToastPacket(player, true, this);
		ToastPacket removePacket = new ToastPacket(player, false, this);
		
		addPacket.send();
		removePacket.send();
	}
	
}