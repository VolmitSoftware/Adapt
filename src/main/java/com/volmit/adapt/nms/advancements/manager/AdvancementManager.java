package com.volmit.adapt.nms.advancements.manager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import com.volmit.adapt.nms.advancements.CrazyAdvancementsAPI;
import com.volmit.adapt.nms.advancements.NameKey;
import com.volmit.adapt.nms.advancements.advancement.Advancement;
import com.volmit.adapt.nms.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.nms.advancements.advancement.AdvancementReward;
import com.volmit.adapt.nms.advancements.advancement.criteria.CriteriaType;
import com.volmit.adapt.nms.advancements.advancement.progress.AdvancementProgress;
import com.volmit.adapt.nms.advancements.advancement.progress.GenericResult;
import com.volmit.adapt.nms.advancements.advancement.progress.GrantCriteriaResult;
import com.volmit.adapt.nms.advancements.advancement.progress.SetCriteriaResult;
import com.volmit.adapt.nms.advancements.event.AdvancementGrantEvent;
import com.volmit.adapt.nms.advancements.event.AdvancementRevokeEvent;
import com.volmit.adapt.nms.advancements.packet.AdvancementsPacket;
import com.volmit.adapt.nms.advancements.packet.PacketConverter;
import com.volmit.adapt.nms.advancements.save.CriteriaData;
import com.volmit.adapt.nms.advancements.save.ProgressData;
import com.volmit.adapt.nms.advancements.save.SaveFile;

/**
 * Represents a Manager that manages Players and Advancements
 * 
 * @author Axel
 *
 */
public final class AdvancementManager {
	
	private static HashMap<String, AdvancementManager> accessibleManagers = new HashMap<>();
	
	/**
	 * Gets an accessible Advancement Manager by it's Name
	 * 
	 * @param name The Name of the Manager
	 * @return the Manager or null if no matching Manager is found
	 */
	public static AdvancementManager getAccessibleManager(NameKey name) {
		return accessibleManagers.containsKey(name.toString()) ? accessibleManagers.get(name.toString()) : null;
	}
	
	/**
	 * Gets a list of all accessible Advancement Managers
	 * 
	 * @return A list of all accessible Advancement Managers
	 */
	public static Collection<AdvancementManager> getAccessibleManagers() {
		return accessibleManagers.values();
	}
	
	private final NameKey name;
	private ArrayList<Player> players;
	private ArrayList<Advancement> advancements = new ArrayList<>();
	
	/**
	 * Constructor for creating Advancement Managers
	 * 
	 * @param name The Name of the Manager
	 * @param players All players that should be in the new manager from the start, can be changed at any time
	 */
	public AdvancementManager(NameKey name, Player... players) {
		this.name = name;
		this.players = new ArrayList<>();
		for(Player player : players) {
			this.addPlayer(player);
		}
	}
	
	/**
	 * Returns the Name of this Manager
	 * 
	 * @return The Name
	 */
	public NameKey getName() {
		return name;
	}
	
	/**
	 * Adds a player to the manager
	 * 
	 * @param player Player to add
	 */
	public void addPlayer(Player player) {
		if(player == null) {
			throw new RuntimeException("Player may not be null");
		}
		if(!players.contains(player)) {
			players.add(player);
		}
		
		List<Advancement> advancements = new ArrayList<>();
		
		for(Advancement advancement : getAdvancements()) {
			AdvancementDisplay display = advancement.getDisplay();
			
			boolean visible = display.isVisible(player, advancement);
			advancement.saveVisibilityStatus(player, visible);
			
			if(visible) {
				advancements.add(advancement);
			}
		}
		
		AdvancementsPacket packet = new AdvancementsPacket(player, false, advancements, null);
		packet.send();
	}
	
	/**
	 * Removes a player from the manager
	 * 
	 * @param player Player to remove
	 */
	public void removePlayer(Player player) {
		players.remove(player);
		
		List<NameKey> removedAdvancements = new ArrayList<>();
		
		for(Advancement advancement : getAdvancements()) {
			removedAdvancements.add(advancement.getName());
		}
		
		AdvancementsPacket packet = new AdvancementsPacket(player, false, null, removedAdvancements);
		packet.send();
	}
	
	/**
	 * Get a list of players in the Manager
	 * 
	 * @return All players that have been added to the manager
	 */
	public ArrayList<Player> getPlayers() {
		Iterator<Player> it = players.iterator();
		while(it.hasNext()) {
			Player p = it.next();
			if(p == null || !p.isOnline()) {
				it.remove();
			}
		}
		return players;
	}
	
	/**
	 * Adds advancements to the Manager<br>Duplicates will be discarded
	 * 
	 * @param addedAdvancements An array of all advancements that should be added
	 */
	public void addAdvancement(Advancement... addedAdvancements) {
		List<Advancement> nonDuplicates = new ArrayList<>();
		for(Advancement advancement : addedAdvancements) {
			if(!advancements.contains(advancement)) {
				advancements.add(advancement);
				nonDuplicates.add(advancement);
			}
		}
		
		HashSet<NameKey> updatedTabs = new HashSet<>();
		
		for(Advancement adv : nonDuplicates) {
			float smallestX = PacketConverter.getSmallestX(adv.getTab());
			float x = adv.getDisplay().generateX();
			if(x < smallestX) {
				smallestX = x;
				updatedTabs.add(adv.getTab());
				PacketConverter.setSmallestX(adv.getTab(), smallestX);
			}
			
			float smallestY = PacketConverter.getSmallestY(adv.getTab());
			float y = adv.getDisplay().generateY();
			if(y < smallestY) {
				smallestY = y;
				updatedTabs.add(adv.getTab());
				PacketConverter.setSmallestY(adv.getTab(), smallestY);
			}
		}
		
		for(NameKey tab : updatedTabs) {
			for(Player player : getPlayers()) {
				updateTab(player, tab);
			}
		}
		
		for(Player player : getPlayers()) {
			List<Advancement> advancements = new ArrayList<>();
			
			for(Advancement advancement : nonDuplicates) {
				AdvancementDisplay display = advancement.getDisplay();
				
				boolean visible = display.isVisible(player, advancement);
				advancement.saveVisibilityStatus(player, visible);
				
				if(visible) {
					advancements.add(advancement);
				}
			}
			
			AdvancementsPacket packet = new AdvancementsPacket(player, false, advancements, null);
			packet.send();
		}
	}
	
	/**
	 * Updates advancements in this manager
	 * 
	 * @param updatedAdvancements The advancements that should be updated
	 */
	public void updateAdvancement(Advancement... updatedAdvancements) {
		Set<NameKey> updatedTabs = new HashSet<>();
		List<Advancement> remainingAdvancements = new ArrayList<>();
		List<NameKey> remainingNames = new ArrayList<>();
		
		for(Advancement advancement : updatedAdvancements) {
			boolean updated = false;
			
			float smallestX = PacketConverter.getSmallestX(advancement.getTab());
			float x = advancement.getDisplay().generateX();
			if(x < smallestX) {
				updated = true;
				smallestX = x;
				updatedTabs.add(advancement.getTab());
				PacketConverter.setSmallestX(advancement.getTab(), smallestX);
			}
			
			float smallestY = PacketConverter.getSmallestY(advancement.getTab());
			float y = advancement.getDisplay().generateY();
			if(y < smallestY) {
				updated = true;
				smallestY = y;
				updatedTabs.add(advancement.getTab());
				PacketConverter.setSmallestY(advancement.getTab(), smallestY);
			}
			
			if(!updated) {
				remainingAdvancements.add(advancement);
			}
		}
		
		for(NameKey tab : updatedTabs) {
			for(Player player : getPlayers()) {
				updateTab(player, tab);
			}
		}
		
		//Update Remaining Advancements (that do not need their whole tab updated)
		if(remainingAdvancements.size() > 0) {
			for(Player player : getPlayers()) {
				NameKey activeTab = CrazyAdvancementsAPI.getActiveTab(player);
				CrazyAdvancementsAPI.clearActiveTab(player);
				
				AdvancementsPacket packet = new AdvancementsPacket(player, false, remainingAdvancements, remainingNames);
				packet.send();
				
				CrazyAdvancementsAPI.setActiveTab(player, activeTab);
			}
		}
	}
	
	/**
	 * Removes an advancement from the Manager
	 * 
	 * @param removedAdvancements An array of advancements that should be removed
	 */
	public void removeAdvancement(Advancement... removedAdvancements) {
		List<NameKey> nonDuplicates = new ArrayList<>();
		for(Advancement advancement : removedAdvancements) {
			if(advancements.contains(advancement)) {
				advancements.remove(advancement);
				nonDuplicates.add(advancement.getName());
			}
		}
		
		for(Player player : getPlayers()) {
			AdvancementsPacket packet = new AdvancementsPacket(player, false, null, nonDuplicates);
			packet.send();
		}
	}
	
	/**
	 * Gets a list of Advancements in the Manager
	 * 
	 * @return The list of Advancements
	 */
	public ArrayList<Advancement> getAdvancements() {
		return new ArrayList<>(advancements);
	}
	
	/**
	 * Gets a list of Advancements with a certain namespace
	 * 
	 * @param namespace Namespace to check
	 * @return A list of all advancements in the manager with a specified namespace
	 */
	public ArrayList<Advancement> getAdvancements(String namespace) {
		ArrayList<Advancement> advs = getAdvancements();
		Iterator<Advancement> it = advs.iterator();
		while(it.hasNext()) {
			Advancement adv = it.next();
			if(!adv.getName().getNamespace().equalsIgnoreCase(namespace)) {
				it.remove();
			}
		}
		return advs;
	}
	
	/**
	 * Gets an Advancement with a specified Name
	 * 
	 * @param name Name to check
	 * @return An advancement matching the given name or null if it doesn't exist in the AdvancementManager
	 */
	public Advancement getAdvancement(NameKey name) {
		for(Advancement advancement : advancements) {
			if(advancement.hasName(name)) {
				return advancement;
			}
		}
		return null;
	}
	
	/**
	 * Makes the AdvancementManager accessible to commands and other plugins using it's Name<br>
	 * There can only be one Manager per Name
	 * 
	 */
	public void makeAccessible() {
		if(accessibleManagers.containsKey(name.toString())) {
			throw new RuntimeException("There is already an AdvancementManager with Name '" + name + "'!");
		} else if(accessibleManagers.containsValue(this)) {
			throw new RuntimeException("AdvancementManager is already accessible!");
		}
		accessibleManagers.put(name.toString(), this);
	}
	
	/**
	 * Resets Accessibility-Status
	 * 
	 */
	public void resetAccessible() {
		Iterator<String> it = accessibleManagers.keySet().iterator();
		while(it.hasNext()) {
			String name = it.next();
			if(accessibleManagers.get(name).equals(this)) {
				it.remove();
				break;
			}
		}
	}
	
	/**
	 * Updates all Advancements in a Tab<br>
	 * If you have Advancements in different tabs
	 * 
	 * @param player The target Player
	 * @param tab The tab to update
	 */
	public void updateTab(Player player, NameKey tab) {
		List<Advancement> advancements = new ArrayList<>();
		List<NameKey> names = new ArrayList<>();
		for(Advancement advancement : getAdvancements()) {
			if(advancement.getTab().isSimilar(tab) && advancement.getDisplay().isVisible(player, advancement)) {
				advancements.add(advancement);
				names.add(advancement.getName());
			}
		}
		NameKey activeTab = CrazyAdvancementsAPI.getActiveTab(player);
		CrazyAdvancementsAPI.clearActiveTab(player);
		
		AdvancementsPacket packet = new AdvancementsPacket(player, false, advancements, names);
		packet.send();
		
		CrazyAdvancementsAPI.setActiveTab(player, activeTab);
	}
	
	/**
	 * Updates Advancement Progress for a Player
	 * 
	 * @param player The target Player
	 * @param advancements An array of Advancements that need their progress updated
	 */
	public void updateProgress(Player player, Advancement... advancements) {
		List<Advancement> advancementsList = new ArrayList<>();
		for(Advancement advancement : advancements) {
			boolean visible = advancement.getDisplay().isVisible(player, advancement);
			advancement.saveVisibilityStatus(player, visible);
			
			if(visible) {
				advancementsList.add(advancement);//Only send advancements that are visible
			}
		}
		AdvancementsPacket packet = new AdvancementsPacket(player, false, advancementsList, null);
		packet.send();
	}
	
	/**
	 * Updates Visibility for Advancements in this Manager
	 * 
	 * @param player The target Player
	 */
	public void updateVisibility(Player player) {
		for(Advancement advancement : getAdvancements()) {
			boolean visibleBefore = advancement.getVisibilityStatus(player);
			boolean visible = advancement.getDisplay().isVisible(player, advancement);
			
			if(visibleBefore != visible) {
				advancement.saveVisibilityStatus(player, visible);
				
				if(visible) {
					AdvancementsPacket packet = new AdvancementsPacket(player, false, Arrays.asList(advancement), null);
					packet.send();
				} else {
					AdvancementsPacket packet = new AdvancementsPacket(player, false, null, Arrays.asList(advancement.getName()));
					packet.send();
				}
			}
		}
	}
	
	/**
	 * Grants an advancement
	 * 
	 * @param player Reciever
	 * @param advancement Advancement to grant
	 * @return The Result of this operation
	 */
	public GenericResult grantAdvancement(Player player, Advancement advancement) {
		AdvancementProgress progress = advancement.getProgress(player);
		GenericResult result = progress.grant();
		
		if(result == GenericResult.CHANGED) {
			AdvancementGrantEvent event = new AdvancementGrantEvent(this, advancement, player);
			Bukkit.getPluginManager().callEvent(event);
			
			if(event.isShowToast()) {
				advancement.displayToast(player);
			}
			if(event.isDisplayMessage()) {
				advancement.displayMessageToEverybody(player);
			}
			AdvancementReward reward = advancement.getReward();
			if(reward != null) {
				reward.onGrant(player);
			}
			updateVisibility(player);
			updateProgress(player, advancement);
		}
		return result;
	}
	
	/**
	 * Grants an advancement, also works with offline players
	 * 
	 * @param uuid Receiver UUID
	 * @param advancement Advancement to grant
	 * @return The Result of this operation
	 */
	public GenericResult grantAdvancement(UUID uuid, Advancement advancement) {
		if(isOnline(uuid)) {
			return grantAdvancement(Bukkit.getPlayer(uuid), advancement);
		} else {
			AdvancementProgress progress = advancement.getProgress(uuid);
			GenericResult result = progress.grant();
			return result;
		}
	}
	
	/**
	 * Revokes an advancement
	 * 
	 * @param player Receiver
	 * @param advancement Advancement to revoke
	 * @return The Result of this operation
	 */
	public GenericResult revokeAdvancement(Player player, Advancement advancement) {
		AdvancementProgress progress = advancement.getProgress(player);
		GenericResult result = progress.revoke();
		
		if(result == GenericResult.CHANGED) {
			AdvancementRevokeEvent event = new AdvancementRevokeEvent(this, advancement, player);
			Bukkit.getPluginManager().callEvent(event);
			
			updateVisibility(player);
			updateProgress(player, advancement);
		}
		return result;
	}
	
	/**
	 * Revokes an advancement, also works with offline players
	 * 
	 * @param uuid Receiver UUID
	 * @param advancement Advancement to revoke
	 * @return The Result of this operation
	 */
	public GenericResult revokeAdvancement(UUID uuid, Advancement advancement) {
		if(isOnline(uuid)) {
			return revokeAdvancement(Bukkit.getPlayer(uuid), advancement);
		} else {
			AdvancementProgress progress = advancement.getProgress(uuid);
			GenericResult result = progress.revoke();
			return result;
		}
	}
	
	/**
	 * Grants criteria for an advancement
	 * 
	 * @param player Receiver
	 * @param advancement The Advancement
	 * @param criteria Array of criteria to grant
	 * @return The Result of this operation
	 */
	public GrantCriteriaResult grantCriteria(Player player, Advancement advancement, String... criteria) {
		AdvancementProgress progress = advancement.getProgress(player);
		GrantCriteriaResult result = progress.grantCriteria(criteria);
		
		switch(result) {
		case COMPLETED:
			AdvancementGrantEvent event = new AdvancementGrantEvent(this, advancement, player);
			Bukkit.getPluginManager().callEvent(event);
			
			if(event.isShowToast()) {
				advancement.displayToast(player);
			}
			if(event.isDisplayMessage()) {
				advancement.displayMessageToEverybody(player);
			}
			AdvancementReward reward = advancement.getReward();
			if(reward != null) {
				reward.onGrant(player);
			}
			updateVisibility(player);
		case CHANGED:
			updateProgress(player, advancement);
			break;
		default:
			//Do nothing
		}
		return result;
	}

	/**
	 * Grans criteria for an advancement, also works with offline players
	 * 
	 * @param uuid Receiver
	 * @param advancement The Advancement
	 * @param criteria Array of criteria to grant
	 * @return The Result of this operation
	 */
	public GrantCriteriaResult grantCriteria(UUID uuid, Advancement advancement, String... criteria) {
		if(isOnline(uuid)) {
			return grantCriteria(Bukkit.getPlayer(uuid), advancement, criteria);
		} else {
			AdvancementProgress progress = advancement.getProgress(uuid);
			GrantCriteriaResult result = progress.grantCriteria(criteria);
			return result;
		}
	}
	
	/**
	 * Revokes criteria for an advancement
	 * 
	 * @param player Receiver
	 * @param advancement The Advancement
	 * @param criteria Array of criteria to revoke
	 * @return The Result of this operation
	 */
	public GenericResult revokeCriteria(Player player, Advancement advancement, String... criteria) {
		AdvancementProgress progress = advancement.getProgress(player);
		GenericResult result = progress.revokeCriteria(criteria);
		
		if(result == GenericResult.CHANGED) {
			AdvancementRevokeEvent event = new AdvancementRevokeEvent(this, advancement, player);
			Bukkit.getPluginManager().callEvent(event);
			
			updateVisibility(player);
			updateProgress(player, advancement);
		}
		
		return result;
	}
	
	/**
	 * Revokes criteria for an advancement, also works with offline players
	 * 
	 * @param uuid Receiver
	 * @param advancement The Advancement
	 * @param criteria Array of criteria to revoke
	 * @return The Result of this operation
	 */
	public GenericResult revokeCriteria(UUID uuid, Advancement advancement, String... criteria) {
		if(isOnline(uuid)) {
			return revokeCriteria(Bukkit.getPlayer(uuid), advancement, criteria);
		} else {
			AdvancementProgress progress = advancement.getProgress(uuid);
			GenericResult result = progress.revokeCriteria(criteria);
			return result;
		}
	}
	
	/**
	 * Sets the criteria progress for an advancement<br>Only works for Advancements with {@link CriteriaType} Number and will return {@link SetCriteriaResult} INVALID if it doesn't match
	 * 
	 * @param player Receiver
	 * @param advancement The Advancement
	 * @param criteriaProgress Amount of progress
	 * @return The Result of this operation
	 */
	public SetCriteriaResult setCriteriaProgress(Player player, Advancement advancement, int criteriaProgress) {
		if(advancement.getCriteria().getType() == CriteriaType.NUMBER) {
			AdvancementProgress progress = advancement.getProgress(player);
			boolean doneBefore = progress.isDone();
			SetCriteriaResult result = progress.setCriteriaProgress(criteriaProgress);
			
			switch(result) {
			case COMPLETED:
				AdvancementGrantEvent event = new AdvancementGrantEvent(this, advancement, player);
				Bukkit.getPluginManager().callEvent(event);
				
				if(event.isShowToast()) {
					advancement.displayToast(player);
				}
				if(event.isDisplayMessage()) {
					advancement.displayMessageToEverybody(player);
				}
				AdvancementReward reward = advancement.getReward();
				if(reward != null) {
					reward.onGrant(player);
				}
				updateVisibility(player);
			case CHANGED:
				if(doneBefore) {
					AdvancementRevokeEvent revokeEvent = new AdvancementRevokeEvent(this, advancement, player);
					Bukkit.getPluginManager().callEvent(revokeEvent);
				}
				updateProgress(player, advancement);
				break;
			default:
				//Do nothing
			}
			
			return result;
		}
		return SetCriteriaResult.INVALID;
	}
	
	/**
	 * Sets the criteria progress for an advancement, also works with offline players<br>Only works for Advancements with {@link CriteriaType} NUMBER and will return {@link SetCriteriaResult} INVALID if it doesn't match
	 * 
	 * @param uuid Receiver
	 * @param advancement The Advancement
	 * @param criteriaProgress Array of criteria to revoke
	 * @return The Result of this operation
	 */
	public SetCriteriaResult setCriteriaProgress(UUID uuid, Advancement advancement, int criteriaProgress) {
		if(isOnline(uuid)) {
			return setCriteriaProgress(Bukkit.getPlayer(uuid), advancement, criteriaProgress);
		} else {
			if(advancement.getCriteria().getType() == CriteriaType.NUMBER) {
				AdvancementProgress progress = advancement.getProgress(uuid);
				SetCriteriaResult result = progress.setCriteriaProgress(criteriaProgress);
				
				return result;
			}
			return SetCriteriaResult.INVALID;
		}
	}
	
	/**
	 * Gets the Criteria Progress
	 * 
	 * @param player The target Player
	 * @param advancement The Advancement
	 * @return The criteria progress
	 */
	public int getCriteriaProgress(Player player, Advancement advancement) {
		return advancement.getProgress(player).getCriteriaProgress();
	}
	
	/**
	 * Gets the Criteria Progress
	 * 
	 * @param uuid The target Player
	 * @param advancement The Advancement
	 * @return The criteria progress
	 */
	public int getCriteriaProgress(UUID uuid, Advancement advancement) {
		return advancement.getProgress(uuid).getCriteriaProgress();
	}
	
	private String getSavePath(UUID uuid) {
		return CrazyAdvancementsAPI.getInstance().getDataFolder().getAbsolutePath() + File.separator + "saved_data" + File.separator + name.getNamespace() + File.separator + name.getKey() + File.separator + uuid + ".json";
	}
	
	private File getSaveFile(UUID uuid) {
		File file = new File(getSavePath(uuid));
		file.getParentFile().mkdirs();
		return file;
	}
	
	/**
	 * Gets the Progress as JSON
	 * 
	 * @param player Player to check
	 * @param advancements A list of advancements that will have their progress saved- Leave empty if all Advancements should be saved
	 * @return A JSON String representation of the progress for a player
	 */
	public SaveFile createNewSave(Player player, Advancement... advancements) {
		return createNewSave(player.getUniqueId(), advancements);
	}
	
	/**
	 * Gets the Progress as JSON
	 * 
	 * @param uuid UUID of Player to check
	 * @param advancements A list of advancements that will have their progress saved- Leave empty if all Advancements should be saved
	 * @return A JSON String representation of the progress for a player
	 */
	public SaveFile createNewSave(UUID uuid, Advancement... advancements) {
		List<Advancement> advancementsList = advancements.length == 0 ? getAdvancements() : Arrays.asList(advancements);
		
		List<ProgressData> progressData = new ArrayList<>();
		List<CriteriaData> criteriaData = new ArrayList<>();
		
		for(Advancement advancement : advancementsList) {
			switch(advancement.getCriteria().getType()) {
			case NUMBER:
				progressData.add(new ProgressData(advancement.getName(), getCriteriaProgress(uuid, advancement)));
				break;
			case LIST:
				criteriaData.add(new CriteriaData(advancement.getName(), Lists.newArrayList(advancement.getProgress(uuid).getAwardedCriteria())));
				break;
			}
		}
		SaveFile saveFile = new SaveFile(progressData, criteriaData);
		
		return saveFile;
	}
	
	/**
	 * Saves the progress in this Advancement Managers file
	 * 
	 * @param player Player to save
	 * @param advancements A list of advancements that will have their progress saved- Leave empty if all Advancements should be saved
	 */
	public void saveProgress(Player player, Advancement... advancements) {
		saveProgress(player.getUniqueId(), advancements);
	}
	
	/**
	 * Saves the progress in this Advancement Managers file
	 * 
	 * @param uuid UUID of Player to save
	 * @param advancements A list of advancements that will have their progress saved- Leave empty if all Advancements should be saved
	 */
	public void saveProgress(UUID uuid, Advancement... advancements) {
		File file = getSaveFile(uuid);
		
		SaveFile saveFile = generateSaveFile(file);
		SaveFile newSaveFile = createNewSave(uuid, advancements);
		
		saveFile.merge(newSaveFile);//Merge new Save Data onto existing Data so nothing gets lost
		
		try {
			if(!file.exists()) {
				file.createNewFile();
			} else if(!file.isFile()) {
				if(file.listFiles().length > 0) {
					throw new RuntimeException("Could not create Save File: A Non-Empty Folder with the File Name already exists: " + file.getName());
				} else {
					file.delete();
					file.createNewFile();
				}
			}
			FileWriter w = new FileWriter(file);
			w.write(saveFile.toJson());
			w.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Loads the progress for Advancements in this Manager
	 * 
	 * @param player Player to load
	 * @param advancements A list of advancements that will have their progress loaded - Leave empty if all Advancements should be loaded
	 */
	public void loadProgress(Player player, Advancement... advancements) {
		loadProgress(player.getUniqueId(), advancements);
	}
	
	/**
	 * Loads the progress for Advancements in this Manager
	 * 
	 * @param uuid UUID of Player to load
	 * @param advancements A list of advancements that will have their progress loaded - Leave empty if all Advancements should be loaded
	 */
	public void loadProgress(UUID uuid, Advancement... advancements) {
		File file = getSaveFile(uuid);
		
		SaveFile saveFile = generateSaveFile(file);
		loadProgress(uuid, saveFile, advancements);
	}
	
	/**
	 * Loads the progress for Advancements in this Manager
	 * 
	 * @param player Player to load
	 * @param saveFile The Save File to be loaded from
	 * @param advancements A list of advancements that will have their progress loaded - Leave empty if all Advancements should be loaded
	 */
	public void loadProgress(Player player, SaveFile saveFile, Advancement... advancements) {
		loadProgress(player.getUniqueId(), saveFile, advancements);
	}
	
	/**
	 * Loads the progress for Advancements in this Manager
	 * 
	 * @param uuid UUID of Player to load
	 * @param saveFile The Save File to be loaded from
	 * @param advancements A list of advancements that will have their progress loaded - Leave empty if all Advancements should be loaded
	 */
	public void loadProgress(UUID uuid, SaveFile saveFile, Advancement... advancements) {
		List<Advancement> advancementsList = advancements.length == 0 ? getAdvancements() : Arrays.asList(advancements);
		
		for(ProgressData progressData : saveFile.getProgressData()) {
			NameKey name = progressData.getName();
			int progress = progressData.getProgress();
			
			for(Advancement advancement: advancementsList) {
				if(advancement.getCriteria().getType() == CriteriaType.NUMBER && advancement.hasName(name)) {
					advancement.getProgress(uuid).setCriteriaProgress(progress);
					break;
				}
			}
		}
		
		for(CriteriaData progressData : saveFile.getCriteriaData()) {
			NameKey name = progressData.getName();
			Iterable<String> criteria = progressData.getCriteria();
			
			for(Advancement advancement: advancementsList) {
				if(advancement.getCriteria().getType() == CriteriaType.LIST && advancement.hasName(name)) {
					advancement.getProgress(uuid).grantCriteria(Iterables.toArray(criteria, String.class));
					break;
				}
			}
		}
	}
	
	/**
	 * Unloads progress for Advancements in this Manager
	 * 
	 * @param player Player to unload
	 * @param advancements A list of advancements that will have their progress unloaded - Leave empty if all Advancements should be unloaded
	 */
	public void unloadProgress(Player player, Advancement... advancements) {
		List<Advancement> advancementsList = advancements.length == 0 ? getAdvancements() : Arrays.asList(advancements);
		
		for(Advancement advancement : advancementsList) {
			advancement.getProgress(player).revoke();//Reset Progress
			updateProgress(player, advancements);//Send Resetted Progress
			advancement.unloadProgress(player);//Remove Progress Object from Advancement for garbage collection
		}
	}
	
	/**
	 * Unloads progress for Advancements in this Manager
	 * 
	 * @param uuid UUID of Player to unload
	 * @param advancements A list of advancements that will have their progress unloaded - Leave empty if all Advancements should be unloaded
	 */
	public void unloadProgress(UUID uuid, Advancement... advancements) {
		List<Advancement> advancementsList = advancements.length == 0 ? getAdvancements() : Arrays.asList(advancements);
		
		for(Advancement advancement : advancementsList) {
			advancement.unloadProgress(uuid);//Remove Progress Object from Advancement for garbage collection
		}
	}
	
	/**
	 * Unloads the Visibility Status for Advancements in this Manager
	 * 
	 * @param player Player to unload
	 * @param advancements A list of advancements that will have their Visibility Status unloaded - Leave empty if all Advancements should be unloaded
	 */
	public void unloadVisibilityStatus(Player player, Advancement... advancements) {
		List<Advancement> advancementsList = advancements.length == 0 ? getAdvancements() : Arrays.asList(advancements);
		
		for(Advancement advancement : advancementsList) {
			advancement.unloadVisibilityStatus(player);
		}
	}
	
	/**
	 * Unloads the Visibility Status for Advancements in this Manager
	 * 
	 * @param uuid UUID of Player to unload
	 * @param advancements A list of advancements that will have their Visibility Status unloaded - Leave empty if all Advancements should be unloaded
	 */
	public void unloadVisibilityStatus(UUID uuid, Advancement... advancements) {
		List<Advancement> advancementsList = advancements.length == 0 ? getAdvancements() : Arrays.asList(advancements);
		
		for(Advancement advancement : advancementsList) {
			advancement.unloadVisibilityStatus(uuid);
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	//Internal Utility methods
	
	private static boolean isOnline(UUID uuid) {
		Player player = Bukkit.getPlayer(uuid);
		return player != null && player.isOnline();
	}
	
	private static SaveFile generateSaveFile(File file) {
		if(file.exists() && file.isFile()) {
			FileReader os = null;
			try {
				os = new FileReader(file);
				
				JsonElement element = JsonParser.parseReader(os);
				os.close();
				
				SaveFile saveFile = SaveFile.fromJSON(element);
				return saveFile;
			} catch (Exception ex) {
				if(os != null) {
					try {
						os.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
				CrazyAdvancementsAPI.getInstance().getLogger().severe("Unable to read Save File!");
				ex.printStackTrace();
			}
		}
		//Return Empty Save if Save File doesn't exist
		return new SaveFile(new ArrayList<ProgressData>(), new ArrayList<CriteriaData>());
	}
	
}