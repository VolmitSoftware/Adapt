package com.volmit.adapt.nms.advancements.advancement;

import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.volmit.adapt.nms.advancements.JSONMessage;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.advancements.AdvancementFrameType;

/**
 * Represents the Display Information of an Advancement
 * 
 * @author Axel
 *
 */
public class AdvancementDisplay {
	
	private ItemStack icon;
	private JSONMessage title, description;
	private AdvancementFrame frame;
	private transient AdvancementVisibility vis;
	private String backgroundTexture;
	private float x = 0, y = 0;
	private Advancement positionOrigin;
	
	//Material Constructors
	
	/**
	 * 
	 * @param icon Icon {@link Material}
	 * @param title Title {@link JSONMessage}
	 * @param description Description {@link JSONMessage}
	 * @param frame {@link AdvancementFrame}
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(Material icon, JSONMessage title, JSONMessage description, AdvancementFrame frame, AdvancementVisibility visibility) {
		this.icon = new ItemStack(icon);
		this.title = title;
		this.description = description;
		this.frame = frame;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link Material}
	 * @param title Title {@link String}
	 * @param description Description {@link String}
	 * @param frame {@link AdvancementFrame}
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(Material icon, String title, String description, AdvancementFrame frame, AdvancementVisibility visibility) {
		this.icon = new ItemStack(icon);
		TextComponent titleComponent = new TextComponent(title);
		this.title = new JSONMessage(titleComponent);
		this.description = new JSONMessage(new TextComponent(description));
		this.frame = frame;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link Material}
	 * @param title Title {@link JSONMessage}
	 * @param description Description {@link JSONMessage}
	 * @param frame {@link AdvancementFrame}
	 * @param backgroundTexture Background texture path
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(Material icon, JSONMessage title, JSONMessage description, AdvancementFrame frame, String backgroundTexture, AdvancementVisibility visibility) {
		this.icon = new ItemStack(icon);
		this.title = title;
		this.description = description;
		this.frame = frame;
		this.backgroundTexture = backgroundTexture;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link Material}
	 * @param title Title {@link String}
	 * @param description Description {@link String}
	 * @param frame {@link AdvancementFrame}
	 * @param backgroundTexture Background texture path
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(Material icon, String title, String description, AdvancementFrame frame, String backgroundTexture, AdvancementVisibility visibility) {
		this.icon = new ItemStack(icon);
		TextComponent titleComponent = new TextComponent(title);
		this.title = new JSONMessage(titleComponent);
		this.description = new JSONMessage(new TextComponent(description));
		this.frame = frame;
		this.backgroundTexture = backgroundTexture;
		setVisibility(visibility);
	}
	
	//ItemStack constructors
	
	/**
	 * 
	 * @param icon Icon {@link ItemStack}
	 * @param title Title {@link JSONMessage}
	 * @param description Description {@link JSONMessage}
	 * @param frame {@link AdvancementFrame}
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(ItemStack icon, JSONMessage title, JSONMessage description, AdvancementFrame frame, AdvancementVisibility visibility) {
		this.icon = icon;
		this.title = title;
		this.description = description;
		this.frame = frame;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link ItemStack}
	 * @param title Title {@link String}
	 * @param description Description {@link String}
	 * @param frame {@link AdvancementFrame}
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(ItemStack icon, String title, String description, AdvancementFrame frame, AdvancementVisibility visibility) {
		this.icon = icon;
		TextComponent titleComponent = new TextComponent(title);
		this.title = new JSONMessage(titleComponent);
		this.description = new JSONMessage(new TextComponent(description));
		this.frame = frame;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link ItemStack}
	 * @param title Title {@link JSONMessage}
	 * @param description Description {@link JSONMessage}
	 * @param frame {@link AdvancementFrame}
	 * @param backgroundTexture Background texture path
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(ItemStack icon, JSONMessage title, JSONMessage description, AdvancementFrame frame, String backgroundTexture, AdvancementVisibility visibility) {
		this.icon = icon;
		this.title = title;
		this.description = description;
		this.frame = frame;
		this.backgroundTexture = backgroundTexture;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link ItemStack}
	 * @param title Title {@link String}
	 * @param description Description {@link String}
	 * @param frame {@link AdvancementFrame}
	 * @param backgroundTexture Background texture path
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(ItemStack icon, String title, String description, AdvancementFrame frame, String backgroundTexture, AdvancementVisibility visibility) {
		this.icon = icon;
		TextComponent titleComponent = new TextComponent(title);
		this.title = new JSONMessage(titleComponent);
		this.description = new JSONMessage(new TextComponent(description));
		this.frame = frame;
		this.backgroundTexture = backgroundTexture;
		setVisibility(visibility);
	}
	
	/**
	 * Represents the Frame of an Advancement
	 * 
	 * @author Axel
	 *
	 */
	public static enum AdvancementFrame {
		
		/**
		 * A Task has the default Frame and defaults to a green Color in Completion Messages
		 */
		TASK(AdvancementFrameType.a),
		/**
		 * A Goal has a rounded off Frame and defaults to a green Color in Completion Messages
		 */
		GOAL(AdvancementFrameType.c),
		/**
		 * A Challenge has a differently shaped Frame and defaults to a purple Color in Completion Messages and it's Toast plays a Sound when displayed
		 */
		CHALLENGE(AdvancementFrameType.b)
		;
		
		private AdvancementFrameType nms;
		
		private AdvancementFrame(AdvancementFrameType nms) {
			this.nms = nms;
		}
		
		/**
		 * Get the NMS Representation of this AdvancementFrame
		 * 
		 * @return THE NMS Representation
		 */
		public AdvancementFrameType getNMS() {
			return nms;
		}
		
		/**
		 * Parses the AdvancementFrame by it's name
		 * 
		 * @param name The case-insensitive name
		 * @return The AdvancementFrame
		 */
		public static AdvancementFrame parse(String name) {
			for(AdvancementFrame frame : values()) {
				if(frame.name().equalsIgnoreCase(name)) {
					return frame;
				}
			}
			return TASK;
		}
		
		/**
		 * Parses the AdvancementFrame by it's name
		 * 
		 * @param name The case-insensitive name
		 * @return The AdvancementFrame or null if no matching Frame is found
		 */
		public static AdvancementFrame parseStrict(String name) {
			for(AdvancementFrame frame : values()) {
				if(frame.name().equalsIgnoreCase(name)) {
					return frame;
				}
			}
			return null;
		}
		
	}
	
	/**
	 * 
	 * @return Icon {@link ItemStack}
	 */
	public ItemStack getIcon() {
		return icon;
	}
	
	/**
	 * 
	 * @return Title {@link JSONMessage}
	 */
	public JSONMessage getTitle() {
		return title;
	}
	
	/**
	 * 
	 * @return Description {@link JSONMessage}
	 */
	public JSONMessage getDescription() {
		return description;
	}
	
	/**
	 * 
	 * @return {@link AdvancementFrame}
	 */
	public AdvancementFrame getFrame() {
		return frame;
	}
	
	/**
	 * 
	 * @return Background texture path
	 */
	@Nullable
	public String getBackgroundTexture() {
		return backgroundTexture;
	}
	
	/**
	 * Sets the background texture
	 * 
	 * @param backgroundTexture Background Texture path
	 */
	public void setBackgroundTexture(@Nullable String backgroundTexture) {
		this.backgroundTexture = backgroundTexture;
	}
	
	/**
	 * Gets the relative X coordinate
	 * 
	 * @return relative X coordinate
	 */
	public float getX() {
		return x;
	}
	
	/**
	 * Gets the relative y coordinate
	 * 
	 * @return relative y coordinate
	 */
	public float getY() {
		return y;
	}
	
	/**
	 * Gets the absolute x coordinate
	 * 
	 * @return absolute x coordinate
	 */
	public float generateX() {
		if(getPositionOrigin() == null) {
			return x;
		} else {
			return getPositionOrigin().getDisplay().generateX() + x;
		}
	}
	
	/**
	 * Gets the absolute y coordinate
	 * 
	 * @return absolute y coordinate
	 */
	public float generateY() {
		if(getPositionOrigin() == null) {
			return y;
		} else {
			return getPositionOrigin().getDisplay().generateY() + y;
		}
	}
	
	/**
	 * Gets the {@link AdvancementVisibility}
	 * 
	 * @return when an advancement is visible
	 */
	public AdvancementVisibility getVisibility() {
		return vis != null ? vis : AdvancementVisibility.VANILLA;
	}
	
	/**
	 * 
	 * @param player Player to check
	 * @param advancement Advancement to check (because {@link AdvancementDisplay} is not bound to one advancement)
	 * @return true if it should be currently visible
	 */
	public boolean isVisible(Player player, Advancement advancement) {
		AdvancementVisibility visibility = getVisibility();
		Advancement parent = advancement.getParent();
		boolean parentVisible = parent == null ? true : parent.getDisplay().isVisible(player, parent);
		return parentVisible && visibility.isVisible(player, advancement) || advancement.isGranted(player) || (visibility.isAlwaysVisibleWhenAnyChildIsGranted() && advancement.isAnythingGrantedAfter(player));
	}
	
	/**
	 * 
	 * @return the advancement that marks the origin of the coordinates
	 */
	public Advancement getPositionOrigin() {
		return positionOrigin;
	}
	
	
	
	
	
	/**
	 * Changes the Icon
	 * 
	 * @param icon New Icon Material to display
	 */
	public void setIcon(Material icon) {
		this.icon = new ItemStack(icon);
	}
	
	/**
	 * Changes the Icon
	 * 
	 * @param icon New Icon to display
	 */
	public void setIcon(ItemStack icon) {
		this.icon = icon;
	}
	
	/**
	 * Changes the Title
	 * 
	 * @param title New title {@link JSONMessage}
	 */
	public void setTitle(JSONMessage title) {
		this.title = title;
	}
	
	/**
	 * Changes the Title
	 * 
	 * @param title New Title {@link String}
	 */
	public void setTitle(String title) {
		TextComponent titleComponent = new TextComponent(title);
		this.title = new JSONMessage(titleComponent);
	}
	
	/**
	 * Changes the Description
	 * 
	 * @param description New description {@link JSONMessage}
	 */
	public void setDescription(JSONMessage description) {
		this.description = description;
	}
	
	/**
	 * Changes the Description
	 * 
	 * @param description New Description {@link String}
	 */
	public void setDescription(String description) {
		this.description = new JSONMessage(new TextComponent(description));
	}
	
	/**
	 * Changes the Frame
	 * 
	 * @param frame New Frame
	 */
	public void setFrame(AdvancementFrame frame) {
		this.frame = frame;
	}
	
	/**
	 * Changes the visibility
	 * 
	 * @param visibility New Visibility
	 */
	public void setVisibility(AdvancementVisibility visibility) {
		this.vis = visibility;
	}
	
	/**
	 * Changes the relative coordinates
	 * 
	 * @param x relative x coordinate
	 * @param y relative y coordinate
	 */
	public void setCoordinates(float x, float y) {
		setX(x);
		setY(y);
	}
	
	/**
	 * Changes the relative x coordinate
	 * 
	 * @param x relative x coordinate
	 */
	public void setX(float x) {
		this.x = x;
	}
	
	/**
	 * Changes the relative y coordinate
	 * 
	 * @param y relative y coordinate
	 */
	public void setY(float y) {
		this.y = y;
	}
	
	/**
	 * Changes the advancement that marks the origin of the coordinates
	 * 
	 * @param positionOrigin New position origin
	 */
	public void setPositionOrigin(@Nullable Advancement positionOrigin) {
		this.positionOrigin = positionOrigin;
	}
	
	
	
	
}