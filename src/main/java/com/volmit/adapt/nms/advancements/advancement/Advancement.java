package com.volmit.adapt.nms.advancements.advancement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.AdvancementDisplay.AdvancementFrame;
import com.volmit.adapt.nms.advancements.advancement.criteria.Criteria;
import com.volmit.adapt.nms.advancements.advancement.progress.AdvancementProgress;
import com.volmit.adapt.nms.advancements.manager.AdvancementManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * Represents an Advancement
 * 
 * @author Axel
 * 
 */
public class Advancement {
	
	private final NameKey name;
	private final AdvancementDisplay display;
	private Criteria criteria = new Criteria(1);
	private AdvancementReward reward;
	
	private final Advancement parent;
	private HashSet<Advancement> children = new HashSet<>();
	private final boolean childrenTracked;
	
	private final List<AdvancementFlag> flags;
	private HashMap<String, Boolean> savedVisibilityStatus;
	
	private HashMap<String, AdvancementProgress> progressMap = new HashMap<>();
	
	/**
	 * Constructor for Advancements with a parent
	 * 
	 * @param parent Parent advancement
	 * @param name Unique Identifier
	 * @param display The Display of the Advancement
	 * @param flags The flags which apply to this Advancement
	 */
	public Advancement(@Nullable Advancement parent, NameKey name, AdvancementDisplay display, AdvancementFlag... flags) {
		this.parent = parent;
		this.childrenTracked = true;
		if(this.parent != null) this.parent.addChild(this);
		this.name = name;
		this.display = display;
		this.flags = Arrays.asList(flags);
	}
	
	/**
	 * Constructor for Advancements with a parent and the option to disable children tracking
	 * 
	 * @param parent Parent advancement
	 * @param name Unique Identifier
	 * @param display The Display of the Advancement
	 * @param childrenTracking Whether children will be tracked. If false, direct children will not be cached resulting in methods like getRow() not containing
	 * advancements after this one. Advancements before this one will also be missing these advancements for the respective methods. Also certain behavior like
	 * AdvancementVisibility might not work as intended
	 * @param flags The flags which apply to this Advancement
	 */
	public Advancement(@Nullable Advancement parent, NameKey name, AdvancementDisplay display, boolean childrenTracking, AdvancementFlag... flags) {
		this.parent = null;
		this.childrenTracked = childrenTracking;
		if(this.parent != null) this.parent.addChild(this);
		this.name = name;
		this.display = display;
		this.flags = Arrays.asList(flags);
	}
	
	/**
	 * Constructor for Root Advancements
	 * 
	 * @param name Unique Identifier
	 * @param display The Display of the Advancement
	 * @param flags The flags which apply to this Advancement
	 */
	public Advancement(NameKey name, AdvancementDisplay display, AdvancementFlag... flags) {
		this.parent = null;
		this.childrenTracked = true;
		if(this.parent != null) this.parent.addChild(this);
		this.name = name;
		this.display = display;
		this.flags = Arrays.asList(flags);
	}
	
	/**
	 * Constructor for Root Advancements with the option to disable children tracking
	 * 
	 * @param name Unique Identifier
	 * @param display The Display of the Advancement
	 * @param childrenTracking Whether children will be tracked. If false, direct children will not be cached resulting in methods like getRow() not containing
	 * advancements after this one. Advancements before this one will also be missing these advancements for the respective methods. Also certain behavior like
	 * AdvancementVisibility might not work as intended
	 * @param flags The flags which apply to this Advancement
	 */
	public Advancement(NameKey name, AdvancementDisplay display, boolean childrenTracking, AdvancementFlag... flags) {
		this.parent = null;
		this.childrenTracked = childrenTracking;
		if(this.parent != null) this.parent.addChild(this);
		this.name = name;
		this.display = display;
		this.flags = Arrays.asList(flags);
	}
	
	/**
	 * Get the Unique Identifier of this Advancement
	 * 
	 * @return The Unique Identifier for this Advancement
	 */
	public NameKey getName() {
		return name;
	}
	
	/**
	 * Checks whether this Advancement has a specific Name
	 * 
	 * @param key Key to check
	 * @return true if {@link Advancement} name and key share the same namespace and name
	 */
	public boolean hasName(NameKey key) {
		return key.getNamespace().equalsIgnoreCase(name.getNamespace()) && key.getKey().equalsIgnoreCase(name.getKey());
	}
	
	/**
	 * Get the Display of this Advancement
	 * 
	 * @return the Display of this Advancement
	 */
	public AdvancementDisplay getDisplay() {
		return display;
	}
	
	/**
	 * Sets the required Criteria that needs to be met in order to complete this Advancement
	 * 
	 * @param criteria The required Criteria
	 */
	public void setCriteria(Criteria criteria) {
		this.criteria = criteria;
		this.progressMap.clear();
	}
	
	/**
	 * Gets the required Criteria that needs to be met in order to complete this Advancement
	 * 
	 * @return The required Criteria
	 */
	public Criteria getCriteria() {
		return criteria;
	}
	
	/**
	 * Sets the Reward of this Advancement
	 * 
	 * @param reward The Reward that should be given to Players upon completing this Advancement
	 */
	public void setReward(AdvancementReward reward) {
		this.reward = reward;
	}
	
	/**
	 * Gets the Reward of this Advancement
	 * 
	 * @return The Reward that gets awarded to Players upon completing this Advancement
	 */
	public AdvancementReward getReward() {
		return reward;
	}
	
	/**
	 * Gets the Parent of this Advancement
	 * 
	 * @return The Parent of this Advancement
	 */
	public Advancement getParent() {
		return parent;
	}
	
	/**
	 * Gets whether this Advancement is a Root Advancement
	 * 
	 * @return Whether this Advancement is a Root Advancement
	 */
	public boolean isRoot() {
		return getParent() == null;
	}
	
	/**
	 * Registers a Child
	 * 
	 * @param adv The Child
	 */
	private void addChild(Advancement adv) {
		if(adv.getParent() == this && childrenTracked) children.add(adv);
	}
	
	/**
	 * Gets all direct Children
	 * 
	 * @return All direct Children
	 */
	public HashSet<Advancement> getChildren() {
		return new HashSet<>(children);
	}
	
	/**
	 * Gets the Root Advancement
	 * 
	 * @return The Root Advancement
	 */
	public Advancement getRootAdvancement() {
		if(parent == null) {
			return this;
		} else {
			return parent.getRootAdvancement();
		}
	}
	
	/**
	 * Gets the Tab
	 * 
	 * @return NameKey of the Tabs Root Advancement
	 */
	public NameKey getTab() {
		return getRootAdvancement().getName();
	}
	
	/**
	 * Gets all parents and children
	 * 
	 * @return All parents and children
	 */
	public List<Advancement> getRow() {
		List<Advancement> row = new ArrayList<>();
		row.add(this);
		if(getParent() != null) {
			for(Advancement untilRow : getParent().getRowUntil()) {
				if(!row.contains(untilRow)) row.add(untilRow);
			}
			Collections.reverse(row);
		}
		for(Advancement child : getChildren()) {
			for(Advancement afterRow : child.getRowAfter()) {
				if(!row.contains(afterRow)) row.add(afterRow);
			}
		}
		return row;
	}
	
	/**
	 * Gets all parents
	 * 
	 * @return All parents
	 */
	public List<Advancement> getRowUntil() {
		List<Advancement> row = new ArrayList<>();
		row.add(this);
		if(getParent() != null) {
			for(Advancement untilRow : getParent().getRowUntil()) {
				if(!row.contains(untilRow)) row.add(untilRow);
			}
		}
		return row;
	}
	
	/**
	 * Gets all children
	 * 
	 * @return All children
	 */
	public List<Advancement> getRowAfter() {
		List<Advancement> row = new ArrayList<>();
		row.add(this);
		for(Advancement child : getChildren()) {
			for(Advancement afterRow : child.getRowAfter()) {
				if(!row.contains(afterRow)) row.add(afterRow);
			}
		}
		return row;
	}
	
	/**
	 * Checks whether any parents have been granted
	 * 
	 * @param player Player to check
	 * @return true if any parent is granted
	 */
	public boolean isAnythingGrantedUntil(Player player) {
		for(Advancement until : getRowUntil()) {
			if(until.isGranted(player)) return true;
		}
		return false;
	}
	
	/**
	 * Checks whether any children have been granted
	 * 
	 * @param player Player to check
	 * @return true if any child is granted
	 */
	public boolean isAnythingGrantedAfter(Player player) {
		for(Advancement after : getRowAfter()) {
			if(after.isGranted(player)) return true;
		}
		return false;
	}
	
	/**
	 * Gets a player's progress
	 * 
	 * @param player The player to check
	 * @return The progress
	 */
	public AdvancementProgress getProgress(Player player) {
		return getProgress(player.getUniqueId());
	}
	
	/**
	 * Gets a Player's progress
	 * 
	 * @param uuid The uuid of the player to check
	 * @return The Player's progress
	 */
	public AdvancementProgress getProgress(UUID uuid) {
		if(!progressMap.containsKey(uuid.toString())) {
			progressMap.put(uuid.toString(), new AdvancementProgress(getCriteria().getRequirements()));
		}
		return progressMap.get(uuid.toString());
	}
	
	/**
	 * Unloads the progress<br>
	 * Will not update the player, use {@link AdvancementManager#unloadProgress(Player player, Advancement... advancements)} for online Players
	 * 
	 * @param player Player to unload progress
	 */
	public void unloadProgress(Player player) {
		unloadProgress(player.getUniqueId());
	}
	
	/**
	 * Unloads the progress<br>
	 * Will not update the player, use {@link AdvancementManager#unloadProgress(UUID uuid, Advancement... advancements)} for online Players
	 * 
	 * @param uuid UUID of Player to unload progress
	 */
	public void unloadProgress(UUID uuid) {
		progressMap.remove(uuid.toString());
	}
	
	/**
	 * Checks whether this Adavncement is granted to a certain player
	 * 
	 * @param player Player to check
	 * @return true if advancement is granted
	 */
	public boolean isGranted(Player player) {
		return getProgress(player).isDone();
	}
	
	/**
	 * Checks whether this Adavncement is granted to a certain player
	 * 
	 * @param uuid The uuid of the Player to check
	 * @return true if advancement is granted
	 */
	public boolean isGranted(UUID uuid) {
		return getProgress(uuid).isDone();
	}
	
	/**
	 * Gets a list of the applied Flags
	 * 
	 * @return The list containing the flags
	 */
	public List<AdvancementFlag> getFlags() {
		return new ArrayList<>(flags);
	}
	
	/**
	 * Checks whether this advancement has a certain flag
	 * 
	 * @param flag The flag to check for
	 * @return Whether this advancement has the specified flag
	 */
	public boolean hasFlag(AdvancementFlag flag) {
		return flags.contains(flag);
	}
	
	/**
	 * Saves the current Visibility Status for a Player, later the visibility is checked against this value to decide whether it changed
	 * 
	 * @param player The Player to save the Visibility Status for
	 * @param visible Whether the Visibility Status is true or false
	 */
	public void saveVisibilityStatus(Player player, boolean visible) {
		if(savedVisibilityStatus == null) savedVisibilityStatus = new HashMap<>();
		savedVisibilityStatus.put(player.getUniqueId().toString(), visible);
	}
	
	/**
	 * Gets the last saved Visibility Status
	 * 
	 * @param player The Player to check
	 * @return The Visibility Status
	 */
	public boolean getVisibilityStatus(Player player) {
		if(savedVisibilityStatus == null) savedVisibilityStatus = new HashMap<>();
		if(!savedVisibilityStatus.containsKey(player.getUniqueId().toString())) savedVisibilityStatus.put(player.getUniqueId().toString(), getDisplay().isVisible(player, this));
		return savedVisibilityStatus.get(player.getUniqueId().toString());
	}
	
	/**
	 * Unloads the Visibility Status for a Player<br>
	 * Should only be run after somebody disconnects to free up RAM
	 * 
	 * @param player The Player to Unload Visibility
	 */
	public void unloadVisibilityStatus(Player player) {
		if(savedVisibilityStatus == null) return;
		savedVisibilityStatus.remove(player.getUniqueId().toString());
	}
	
	/**
	 * Unloads the Visibility Status for a Player<br>
	 * Should only be run after somebody disconnects to free up RAM
	 * 
	 * @param uuid The UUID of Player to Unload Visibility
	 */
	public void unloadVisibilityStatus(UUID uuid) {
		savedVisibilityStatus.remove(uuid.toString());
	}
	
	/**
	 * Gets a Toast Notification for this Advancement
	 * 
	 * @return The Toast Notification
	 */
	public ToastNotification getToastNotification() {
		ToastNotification notification = new ToastNotification(getDisplay().getIcon(), getDisplay().getTitle(), getDisplay().getFrame());
		return notification;
	}
	
	/**
	 * Sends a Toast regardless if the Player has it in one of their Advancement Managers or not
	 * 
	 * @param player Player who should see the Toast Message
	 */
	public void displayToast(Player player) {
		ToastNotification notification = getToastNotification();
		notification.send(player);
	}
	
	/**
	 * Gets an Advancement Message
	 * 
	 * @param player Player who has recieved the advancement
	 * @return The Advancement Message as a Base Component
	 */
	public BaseComponent getMessage(Player player) {
		String translation = "chat.type.advancement." + display.getFrame().name().toLowerCase();
		boolean challenge = getDisplay().getFrame() == AdvancementFrame.CHALLENGE;
		
		TranslatableComponent message = new TranslatableComponent();
		message.setTranslate(translation);
		
		TextComponent playerNameText = new TextComponent();
		BaseComponent[] playerNameComponents = TextComponent.fromLegacyText(player.getDisplayName());
		playerNameText.setExtra(Arrays.asList(playerNameComponents));
		
		TextComponent title = new TextComponent("[");
		title.addExtra(display.getTitle().getJson());
		title.addExtra("]");
		title.setColor(challenge ? ChatColor.DARK_PURPLE : ChatColor.GREEN);
		
		TextComponent titleTextComponent = new TextComponent(display.getTitle().getJson());
		titleTextComponent.setColor(title.getColor());
		
		Text titleText = new Text(new BaseComponent[] {titleTextComponent});
		Text descriptionText = new Text(new BaseComponent[] {display.getDescription().getJson()});
		title.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, titleText, new Text("\n"), descriptionText));
		
		message.setWith(Arrays.asList(playerNameText, title));
		
		return message;
	}
	
	/**
	 * Displays an Advancement Message to every Player saying Player has completed said advancement<br>
	 * Note that this doesn't grant the advancement
	 * 
	 * 
	 * @param player Player who has recieved the advancement
	 */
	public void displayMessageToEverybody(Player player) {
		BaseComponent message = getMessage(player);
		
		for(Player online : Bukkit.getOnlinePlayers()) {
			online.spigot().sendMessage(ChatMessageType.CHAT, message);
		}
	}
	
	@Override
	public boolean equals(Object obj) {
		return getName().equals(((Advancement) obj).getName());
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}