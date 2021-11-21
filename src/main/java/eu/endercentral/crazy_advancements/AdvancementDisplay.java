package eu.endercentral.crazy_advancements;

import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.google.gson.annotations.SerializedName;

import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.advancements.AdvancementFrameType;

public class AdvancementDisplay {
	
	@SerializedName("icon")
	private Material iconID;
	private transient ItemStack icon;
	private JSONMessage title, description;
	private AdvancementFrame frame;
	private boolean showToast;
	private boolean announceChat;
	private transient AdvancementVisibility vis;
	private String backgroundTexture;
	private float x = 0, y = 0, tabWidth = 0, tabHeight = 0;
	private transient Advancement positionOrigin;
	
	@SerializedName("visibility")
	public String visibilityIdentifier = "VANILLA";
	
	//Material Constructors
	
	/**
	 * 
	 * @param icon Icon {@link Material}
	 * @param title Title {@link JSONMessage}
	 * @param description Description {@link JSONMessage}
	 * @param frame {@link AdvancementFrame}
	 * @param showToast Should toast messages be shown
	 * @param announceChat Should advancements be announced in chat
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(Material icon, JSONMessage title, JSONMessage description, AdvancementFrame frame, boolean showToast, boolean announceChat, AdvancementVisibility visibility) {
		this.icon = new ItemStack(icon);
		this.iconID = icon;
		this.title = title;
		this.description = description;
		this.frame = frame;
		this.showToast = showToast;
		this.announceChat = announceChat;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link Material}
	 * @param title Title {@link String}
	 * @param description Description {@link String}
	 * @param frame {@link AdvancementFrame}
	 * @param showToast Should toast messages be shown
	 * @param announceChat Should advancements be announced in chat
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(Material icon, String title, String description, AdvancementFrame frame, boolean showToast, boolean announceChat, AdvancementVisibility visibility) {
		this.icon = new ItemStack(icon);
		this.iconID = icon;
		TextComponent titleComponent = new TextComponent(title);
		this.title = new JSONMessage(titleComponent);
		this.description = new JSONMessage(new TextComponent(description));
		this.frame = frame;
		this.showToast = showToast;
		this.announceChat = announceChat;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link Material}
	 * @param title Title {@link JSONMessage}
	 * @param description Description {@link JSONMessage}
	 * @param frame {@link AdvancementFrame}
	 * @param backgroundTexture Background texture path
	 * @param showToast Should toast messages be shown
	 * @param announceChat Should advancements be announced in chat
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(Material icon, JSONMessage title, JSONMessage description, AdvancementFrame frame, String backgroundTexture, boolean showToast, boolean announceChat, AdvancementVisibility visibility) {
		this.icon = new ItemStack(icon);
		this.iconID = icon;
		this.title = title;
		this.description = description;
		this.frame = frame;
		this.backgroundTexture = backgroundTexture;
		this.showToast = showToast;
		this.announceChat = announceChat;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link Material}
	 * @param title Title {@link String}
	 * @param description Description {@link String}
	 * @param frame {@link AdvancementFrame}
	 * @param backgroundTexture Background texture path
	 * @param showToast Should toast messages be shown
	 * @param announceChat Should advancements be announced in chat
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(Material icon, String title, String description, AdvancementFrame frame, String backgroundTexture, boolean showToast, boolean announceChat, AdvancementVisibility visibility) {
		this.icon = new ItemStack(icon);
		this.iconID = icon;
		TextComponent titleComponent = new TextComponent(title);
		this.title = new JSONMessage(titleComponent);
		this.description = new JSONMessage(new TextComponent(description));
		this.frame = frame;
		this.backgroundTexture = backgroundTexture;
		this.showToast = showToast;
		this.announceChat = announceChat;
		setVisibility(visibility);
	}
	
	//ItemStack constructors
	
	/**
	 * 
	 * @param icon Icon {@link ItemStack}
	 * @param title Title {@link JSONMessage}
	 * @param description Description {@link JSONMessage}
	 * @param frame {@link AdvancementFrame}
	 * @param showToast Should toast messages be shown
	 * @param announceChat Should advancements be announced in chat
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(ItemStack icon, JSONMessage title, JSONMessage description, AdvancementFrame frame, boolean showToast, boolean announceChat, AdvancementVisibility visibility) {
		this.icon = icon;
		this.iconID = icon.getType();
		this.title = title;
		this.description = description;
		this.frame = frame;
		this.showToast = showToast;
		this.announceChat = announceChat;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link ItemStack}
	 * @param title Title {@link String}
	 * @param description Description {@link String}
	 * @param frame {@link AdvancementFrame}
	 * @param showToast Should toast messages be shown
	 * @param announceChat Should advancements be announced in chat
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(ItemStack icon, String title, String description, AdvancementFrame frame, boolean showToast, boolean announceChat, AdvancementVisibility visibility) {
		this.icon = icon;
		this.iconID = icon.getType();
		TextComponent titleComponent = new TextComponent(title);
		this.title = new JSONMessage(titleComponent);
		this.description = new JSONMessage(new TextComponent(description));
		this.frame = frame;
		this.showToast = showToast;
		this.announceChat = announceChat;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link ItemStack}
	 * @param title Title {@link JSONMessage}
	 * @param description Description {@link JSONMessage}
	 * @param frame {@link AdvancementFrame}
	 * @param backgroundTexture Background texture path
	 * @param showToast Should toast messages be shown
	 * @param announceChat Should advancements be announced in chat
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(ItemStack icon, JSONMessage title, JSONMessage description, AdvancementFrame frame, String backgroundTexture, boolean showToast, boolean announceChat, AdvancementVisibility visibility) {
		this.icon = icon;
		this.iconID = icon.getType();
		this.title = title;
		this.description = description;
		this.frame = frame;
		this.backgroundTexture = backgroundTexture;
		this.showToast = showToast;
		this.announceChat = announceChat;
		setVisibility(visibility);
	}
	
	/**
	 * 
	 * @param icon Icon {@link ItemStack}
	 * @param title Title {@link String}
	 * @param description Description {@link String}
	 * @param frame {@link AdvancementFrame}
	 * @param backgroundTexture Background texture path
	 * @param showToast Should toast messages be shown
	 * @param announceChat Should advancements be announced in chat
	 * @param visibility When an advancement is visible
	 */
	public AdvancementDisplay(ItemStack icon, String title, String description, AdvancementFrame frame, String backgroundTexture, boolean showToast, boolean announceChat, AdvancementVisibility visibility) {
		this.icon = icon;
		this.iconID = icon.getType();
		TextComponent titleComponent = new TextComponent(title);
		this.title = new JSONMessage(titleComponent);
		this.description = new JSONMessage(new TextComponent(description));
		this.frame = frame;
		this.backgroundTexture = backgroundTexture;
		this.showToast = showToast;
		this.announceChat = announceChat;
		setVisibility(visibility);
	}
	
	public static enum AdvancementFrame {
		
		TASK(AdvancementFrameType.a),
		GOAL(AdvancementFrameType.b),
		CHALLENGE(AdvancementFrameType.c)
		;
		
		private AdvancementFrameType nms;
		
		private AdvancementFrame(AdvancementFrameType nms) {
			this.nms = nms;
		}
		
		public AdvancementFrameType getNMS() {
			return nms;
		}
		
	}
	
	/**
	 * 
	 * @return Icon {@link ItemStack}
	 */
	public ItemStack getIcon() {
		if(icon == null && iconID != null) icon = new ItemStack(iconID);
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
	 * @return true if toasts will be shown
	 */
	public boolean isToastShown() {
		return showToast;
	}
	
	/**
	 * 
	 * @return true if messages will be displayed in chat
	 */
	public boolean isAnnouncedToChat() {
		return announceChat && CrazyAdvancements.isAnnounceAdvancementMessages();
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
	
	public float getTabWidth() {
		return tabWidth;
	}
	
	public float getTabHeight() {
		return tabHeight;
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
		return visibility.isVisible(player, advancement) || advancement.isGranted(player) || (visibility.isAlwaysVisibleWhenAdvancementAfterIsVisible() && advancement.isAnythingGrantedAfter(player));
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
		this.iconID = icon;
	}
	
	/**
	 * Changes the Icon
	 * 
	 * @param icon New Icon to display
	 */
	public void setIcon(ItemStack icon) {
		this.icon = icon;
		this.iconID = icon.getType();
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
	 * @param title New title {@link JSONMessage}
	 */
	public void setDescription(JSONMessage description) {
		this.description = description;
	}
	
	/**
	 * Changes the Description
	 * 
	 * @param title New Title {@link String}
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
	 * Changes if toasts should be shown
	 * 
	 * @param showToast
	 */
	public void setShowToast(boolean showToast) {
		this.showToast = showToast;
	}
	
	/**
	 * Changes if chat messages should be displayed
	 * 
	 * @param announceChat
	 */
	public void setAnnounceChat(boolean announceChat) {
		this.announceChat = announceChat;
	}
	
	/**
	 * Changes the visibility
	 * 
	 * @param visibility New Visibility
	 */
	public void setVisibility(AdvancementVisibility visibility) {
		this.vis = visibility;
		this.visibilityIdentifier = getVisibility().getName();
	}
	
	/**
	 * Changes the relative coordinates
	 * 
	 * @param x relative x coordinate
	 * @param y relative y coordinate
	 */
	public void setCoordinates(float x, float y) {
		this.x = x;
		this.y = y;
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
	
	public void setTabHeight(float tabHeight) {
		this.tabHeight = tabHeight;
	}
	
	public void setTabWidth(float tabWidth) {
		this.tabWidth = tabWidth;
	}
	
	/**
	 * Changes the advancement that marks the origin of the coordinates
	 * 
	 * @param positionOrigin New position origin
	 */
	public void setPositionOrigin(Advancement positionOrigin) {
		this.positionOrigin = positionOrigin;
	}
	
	
	
	
}