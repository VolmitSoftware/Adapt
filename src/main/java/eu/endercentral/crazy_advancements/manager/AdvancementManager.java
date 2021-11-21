package eu.endercentral.crazy_advancements.manager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.volmit.adapt.Adapt;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_17_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.endercentral.crazy_advancements.Advancement;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import eu.endercentral.crazy_advancements.CrazyAdvancements;
import eu.endercentral.crazy_advancements.NameKey;
import eu.endercentral.crazy_advancements.SaveMethod;
import eu.endercentral.crazy_advancements.events.AdvancementGrantEvent;
import eu.endercentral.crazy_advancements.events.AdvancementRevokeEvent;
import eu.endercentral.crazy_advancements.events.CriteriaGrantEvent;
import eu.endercentral.crazy_advancements.events.CriteriaProgressChangeEvent;
import eu.endercentral.crazy_advancements.events.offline.OfflineAdvancementGrantEvent;
import eu.endercentral.crazy_advancements.events.offline.OfflineAdvancementRevokeEvent;
import eu.endercentral.crazy_advancements.events.offline.OfflineCriteriaGrantEvent;
import eu.endercentral.crazy_advancements.events.offline.OfflineCriteriaProgressChangeEvent;
import eu.endercentral.crazy_advancements.exception.UnloadProgressFailedException;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.minecraft.advancements.AdvancementDisplay;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionInstance;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.critereon.LootSerializationContext;
import net.minecraft.network.protocol.game.PacketPlayOutAdvancements;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.world.item.ItemStack;

public final class AdvancementManager {
	
	private static HashMap<String, AdvancementManager> accessible = new HashMap<>();
	
	private boolean hiddenBoolean = false;
	private String criterionPrefix = "criterion.";
	private String criterionNamespace = "minecraft";
	private String criterionKey = "impossible";
	
	private static HashMap<NameKey, Float> smallestY = new HashMap<>();
	private static HashMap<NameKey, Float> smallestX = new HashMap<>();
	
	/**
	 * Gets an accessible Advancement Manager by it's Name
	 * 
	 * @param name
	 * @return
	 */
	public static AdvancementManager getAccessibleManager(String name) {
		name = name.toLowerCase();
		return accessible.containsKey(name) ? accessible.get(name) : null;
	}
	
	public static Collection<AdvancementManager> getAccessibleManagers() {
		return accessible.values();
	}
	
	private static float getSmallestY(NameKey key) {
		return smallestY.containsKey(key) ? smallestY.get(key) : 0;
	}
	
	private static float getSmallestX(NameKey key) {
		return smallestX.containsKey(key) ? smallestX.get(key) : 0;
	}
	
	/**
	 * Sets the boolean that is passed via the advancement packet when an advancement is hidden<br>Default: false<br>When set to true, hidden advancements that have not been granted yet, will have a line drawn to them even though they aren't displayed yet, when they should be visible (according to their {@link AdvancementVisibility})<br>Can be used to create an empty advancement tab where there are no advancements visible and no lines visible, when the tab only has a hidden advancement as a root
	 * 
	 * @param hiddenBoolean The new hiddenBoolean
	 */
	public void setHiddenBoolean(boolean hiddenBoolean) {
		this.hiddenBoolean = hiddenBoolean;
	}
	
	/**
	 * Gets the boolean that is passed via the advancement packet when an advancement is hidden<brDefault: false
	 * 
	 * @return
	 */
	public boolean getHiddenBoolean() {
		return hiddenBoolean;
	}
	
	/**
	 * Set the prefix that is used for criteria<br>For legacy reasons, the default prefix is "criterion." as the advancement progress is stored by their criterion name, which consists of prefix + number<br>To reduce packet size and thus increase the max criteria number that can be used, the prefix can be set to an empty String<br>Only works for advancements that have not generated their criteria yet (use this method before adding advancements to it)
	 * 
	 * @param criterionPrefix The new prefix that is used for criteria
	 */
	public void setCriterionPrefix(String criterionPrefix) {
		this.criterionPrefix = criterionPrefix;
	}
	
	/**
	 * Get the prefix that is used for criteria
	 * 
	 * @return The prefix that is used for criteria
	 */
	public String getCriterionPrefix() {
		return criterionPrefix;
	}
	
	/**
	 * Set the namespace that is used for the Namespaced Key for criteria<br>Only works for advancements that have not generated their criteria yet (use this method before adding advancements to it)
	 * 
	 * @param criterionNamespace The new namespace that is used for the Namespaced Key for criteria
	 */
	public void setCriterionNamespace(String criterionNamespace) {
		this.criterionNamespace = criterionNamespace;
	}
	
	/**
	 * Get the namespace that is used for the Namespaced Key for criteria
	 * 
	 * @return The namespace that is used for the Namespaced Key for criteria
	 */
	public String getCriterionNamespace() {
		return criterionNamespace;
	}
	
	/**
	 * Sets the key that is used for the Namespaced Key for criteria<br>Only works for advancements that have not generated their criteria yet (use this method before adding advancements to it)
	 * 
	 * @param criterionKey The new key that is used for the Namespaced Key for criteria
	 */
	public void setCriterionKey(String criterionKey) {
		this.criterionKey = criterionKey;
	}
	
	/**
	 * Get the key that is used for the Namespaced Key for criteria
	 * 
	 * @return The key that is used for the Namespaced Key for criteria
	 */
	public String getCriterionKey() {
		return criterionKey;
	}
	
	private boolean announceAdvancementMessages = true;
	private ArrayList<Player> players;
	private ArrayList<Advancement> advancements = new ArrayList<>();
	
	/**
	 * 
	 * @param players All players that should be in the new manager from the start, can be changed at any time
	 */
	public AdvancementManager(Player... players) {
		this.players = new ArrayList<>();
		for(Player player : players) {
			this.addPlayer(player);
		}
	}
	
	/**
	 * Creates a new instance of an advancement manager
	 * 
	 * @param players All players that should be in the new manager from the start, can be changed at any time
	 * @return the generated advancement manager
	 * @deprecated Use the AdvancementManager constructor instead of this method
	 */
	@Deprecated(since = "1.13.10")
	public static AdvancementManager getNewAdvancementManager(Player... players) {
		AdvancementManager manager = new AdvancementManager(players);
		return manager;
	}
	
	/**
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
	 * Adds a player to the manager
	 * 
	 * @param player Player to add
	 */
	public void addPlayer(Player player) {
		Validate.notNull(player);
		addPlayer(player, null);
	}
	
	private void addPlayer(Player player, NameKey tab) {
		if(!players.contains(player)) {
			players.add(player);
		}
		
		Collection<net.minecraft.advancements.Advancement> advs = new ArrayList<>();
		Set<MinecraftKey> remove = new HashSet<>();
		Map<MinecraftKey, AdvancementProgress> prgs = new HashMap<>();
		
		for(Advancement advancement : advancements) {
			boolean isTab = tab != null && advancement.getTab().isSimilar(tab);
			if(isTab) {
				remove.add(advancement.getName().getMinecraftKey());
			}
			
			if(tab == null || isTab) {
				//Criteria
				checkAwarded(player, advancement);
				
				eu.endercentral.crazy_advancements.AdvancementDisplay display = advancement.getDisplay();
				
				boolean showToast = display.isToastShown() && getCriteriaProgress(player, advancement) < advancement.getSavedCriteria().size();
				
				ItemStack icon = CraftItemStack.asNMSCopy(display.getIcon());
				
				MinecraftKey backgroundTexture = null;
				boolean hasBackgroundTexture = display.getBackgroundTexture() != null;
				
				if(hasBackgroundTexture) {
					backgroundTexture = new MinecraftKey(display.getBackgroundTexture());
				}
				
				boolean hidden = !display.isVisible(player, advancement);
				advancement.saveHiddenStatus(player, hidden);
				
				if(!hidden || hiddenBoolean) {
					AdvancementDisplay advDisplay = new AdvancementDisplay(icon, display.getTitle().getBaseComponent(), display.getDescription().getBaseComponent(), backgroundTexture, display.getFrame().getNMS(), showToast, display.isAnnouncedToChat(), hidden ? hiddenBoolean : false);
					advDisplay.a(display.generateX() - getSmallestX(advancement.getTab()), display.generateY() - getSmallestY(advancement.getTab()));
					
					AdvancementRewards advRewards = new AdvancementRewards(0, new MinecraftKey[0], new MinecraftKey[0], null);
					
					Map<String, Criterion> advCriteria = new HashMap<>();
					String[][] advRequirements = new String[][] {};
					
					if(advancement.getSavedCriteria() == null) {
						for(int i = 0; i < advancement.getCriteria(); i++) {
							advCriteria.put(criterionPrefix + i, new Criterion(new CriterionInstance() {
								@Override
								public JsonObject a(LootSerializationContext arg0) {
									return null;
								}
								
								@Override
								public MinecraftKey a() {
									return new MinecraftKey(criterionNamespace, criterionKey);
								}
							}));
						}
						advancement.saveCriteria(advCriteria);
					} else {
						advCriteria = advancement.getSavedCriteria();
					}
					
					if(advancement.getSavedCriteriaRequirements() == null) {
						ArrayList<String[]> fixedRequirements = new ArrayList<>();
						for(String name : advCriteria.keySet()) {
							fixedRequirements.add(new String[] {name});
						}
						advRequirements = Arrays.stream(fixedRequirements.toArray()).toArray(String[][]::new);
						advancement.saveCriteriaRequirements(advRequirements);
					} else {
						advRequirements = advancement.getSavedCriteriaRequirements();
					}
					
					net.minecraft.advancements.Advancement adv = new net.minecraft.advancements.Advancement(advancement.getName().getMinecraftKey(), advancement.getParent() == null ? null : advancement.getParent().getSavedAdvancement(), advDisplay, advRewards, advCriteria, advRequirements);
					
					advs.add(adv);
					
					AdvancementProgress advPrg = advancement.getProgress(player);
					advPrg.a(advancement.getSavedCriteria(), advancement.getSavedCriteriaRequirements());
					
					for(String criterion : advancement.getAwardedCriteria().get(player.getUniqueId().toString())) {
						CriterionProgress critPrg = advPrg.getCriterionProgress(criterion);
						critPrg.b();
					}
					
					advancement.setProgress(player, advPrg);
					
					prgs.put(advancement.getName().getMinecraftKey(), advPrg);
				}
			}
			
		}
		
		//Packet
		PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(false, advs, remove, prgs);
		((CraftPlayer)player).getHandle().b.sendPacket(packet);
	}
	
	/**
	 * Removes a player from the manager
	 * 
	 * @param player Player to remove
	 */
	public void removePlayer(Player player) {
		players.remove(player);
		
		Collection<net.minecraft.advancements.Advancement> advs = new ArrayList<>();
		Set<MinecraftKey> remove = new HashSet<>();
		Map<MinecraftKey, AdvancementProgress> prgs = new HashMap<>();
		
		for(Advancement advancement : advancements) {
			remove.add(advancement.getName().getMinecraftKey());
		}
		
		//Packet
		PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(false, advs, remove, prgs);
		((CraftPlayer)player).getHandle().b.sendPacket(packet);
	}
	
	/**
	 * Adds advancements or updates one advancement
	 * 
	 * @param advancementsAdded An array of all advancements that should be added<br>If you want to update the display of an advancement, the array must have a length of 1
	 */
	public void addAdvancement(eu.endercentral.crazy_advancements.Advancement... advancementsAdded) {
		HashMap<Player, Collection<net.minecraft.advancements.Advancement>> advancementsList = new HashMap<>();
		Set<MinecraftKey> remove = new HashSet<>();
		HashMap<Player, Map<MinecraftKey, AdvancementProgress>> progressList = new HashMap<>();
		
		HashSet<NameKey> updatedTabs = new HashSet<>();
		
		for(Advancement adv : advancementsAdded) {
			float smallestY = getSmallestY(adv.getTab());
			float y = adv.getDisplay().generateY();
			if(y < smallestY) {
				smallestY = y;
				updatedTabs.add(adv.getTab());
				AdvancementManager.smallestY.put(adv.getTab(), smallestY);
			}
			
			float smallestX = getSmallestX(adv.getTab());
			float x = adv.getDisplay().generateY();
			if(x < smallestX) {
				smallestX = x;
				updatedTabs.add(adv.getTab());
				AdvancementManager.smallestX.put(adv.getTab(), smallestX);
			}
		}
		
		for(NameKey key : updatedTabs) {
			for(Player player : players) {
				update(player, key);
			}
		}
		
		for(Advancement advancement : advancementsAdded) {
			if(advancements.contains(advancement)) {
				remove.add(advancement.getName().getMinecraftKey());
			} else {
				advancements.add(advancement);
			}
			eu.endercentral.crazy_advancements.AdvancementDisplay display = advancement.getDisplay();
			
			AdvancementRewards advRewards = new AdvancementRewards(0, new MinecraftKey[0], new MinecraftKey[0], null);
			
			ItemStack icon = CraftItemStack.asNMSCopy(display.getIcon());
			
			MinecraftKey backgroundTexture = null;
			boolean hasBackgroundTexture = display.getBackgroundTexture() != null;
			
			if(hasBackgroundTexture) {
				backgroundTexture = new MinecraftKey(display.getBackgroundTexture());
			}
			
			Map<String, Criterion> advCriteria = new HashMap<>();
			String[][] advRequirements = new String[][] {};
			
			if(advancement.getSavedCriteria() == null) {
				for(int i = 0; i < advancement.getCriteria(); i++) {
					advCriteria.put(criterionPrefix + i, new Criterion(new CriterionInstance() {
						@Override
						public JsonObject a(LootSerializationContext arg0) {
							return null;
						}
						
						@Override
						public MinecraftKey a() {
							return new MinecraftKey(criterionNamespace, criterionKey);
						}
					}));
				}
				advancement.saveCriteria(advCriteria);
			} else {
				advCriteria = advancement.getSavedCriteria();
			}
			
			if(advancement.getSavedCriteriaRequirements() == null) {
				ArrayList<String[]> fixedRequirements = new ArrayList<>();
				for(String name : advCriteria.keySet()) {
					fixedRequirements.add(new String[] {name});
				}
				advRequirements = Arrays.stream(fixedRequirements.toArray()).toArray(String[][]::new);
				advancement.saveCriteriaRequirements(advRequirements);
			} else {
				advRequirements = advancement.getSavedCriteriaRequirements();
			}
			
			
			
			AdvancementDisplay saveDisplay = new AdvancementDisplay(icon, display.getTitle().getBaseComponent(), display.getDescription().getBaseComponent(), backgroundTexture, display.getFrame().getNMS(), display.isToastShown(), display.isAnnouncedToChat(), true);
			saveDisplay.a(display.generateX() - getSmallestY(advancement.getTab()), display.generateY() - getSmallestX(advancement.getTab()));
			
			net.minecraft.advancements.Advancement saveAdv = new net.minecraft.advancements.Advancement(advancement.getName().getMinecraftKey(), advancement.getParent() == null ? null : advancement.getParent().getSavedAdvancement(), saveDisplay, advRewards, advCriteria, advRequirements);
			
			advancement.saveAdvancement(saveAdv);
			
			for(Player player : getPlayers()) {
				Map<MinecraftKey, AdvancementProgress> prgs = progressList.containsKey(player) ? progressList.get(player) : new HashMap<>();
				checkAwarded(player, advancement);
				
				boolean showToast = display.isToastShown() && getCriteriaProgress(player, advancement) < advancement.getSavedCriteria().size();
				
				Collection<net.minecraft.advancements.Advancement> advs = advancementsList.containsKey(player) ? advancementsList.get(player) : new ArrayList<>();
				
				boolean hidden = !display.isVisible(player, advancement);
				advancement.saveHiddenStatus(player, hidden);
				
				if(!hidden || hiddenBoolean) {
					AdvancementDisplay advDisplay = new AdvancementDisplay(icon, display.getTitle().getBaseComponent(), display.getDescription().getBaseComponent(), backgroundTexture, display.getFrame().getNMS(), showToast, display.isAnnouncedToChat(), hidden ? hiddenBoolean : false);
					advDisplay.a(display.generateX() - getSmallestX(advancement.getTab()), display.generateY() - getSmallestY(advancement.getTab()));
					
					net.minecraft.advancements.Advancement adv = new net.minecraft.advancements.Advancement(advancement.getName().getMinecraftKey(), advancement.getParent() == null ? null : advancement.getParent().getSavedAdvancement(), advDisplay, advRewards, advCriteria, advRequirements);
					
					advs.add(adv);
					
					advancementsList.put(player, advs);
					
					AdvancementProgress advPrg = advancement.getProgress(player);
					advPrg.a(advCriteria, advRequirements);
					
					for(String criterion : advancement.getAwardedCriteria().get(player.getUniqueId().toString())) {
						CriterionProgress critPrg = advPrg.getCriterionProgress(criterion);
						critPrg.b();
					}
					
					advancement.setProgress(player, advPrg);
					
					prgs.put(advancement.getName().getMinecraftKey(), advPrg);
					
					progressList.put(player, prgs);
				}
			}
		}

		for(Player player : getPlayers()) {
			//Packet
			PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(false, advancementsList.get(player), remove, progressList.get(player));
			((CraftPlayer)player).getHandle().b.sendPacket(packet);
		}
	}
	
	/**
	 * Removes an advancement from the manager
	 * 
	 * @param advancementsRemoved An array of advancements that should be removed
	 */
	public void removeAdvancement(Advancement... advancementsRemoved) {
		Collection<net.minecraft.advancements.Advancement> advs = new ArrayList<>();
		Set<MinecraftKey> remove = new HashSet<>();
		Map<MinecraftKey, AdvancementProgress> prgs = new HashMap<>();
		
		for(Advancement advancement : advancementsRemoved) {
			if(advancements.contains(advancement)) {
				advancements.remove(advancement);
				
				remove.add(advancement.getName().getMinecraftKey());
			}
		}
		
		for(Player player : getPlayers()) {
			//Packet
			PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(false, advs, remove, prgs);
			((CraftPlayer)player).getHandle().b.sendPacket(packet);
		}
	}
	
	/**
	 * Updates/Refreshes the player
	 * 
	 * @param player Player to update
	 */
	public void update(Player player) {
		if(players.contains(player)) {
			NameKey rootAdvancement = CrazyAdvancements.getActiveTab(player);
			CrazyAdvancements.clearActiveTab(player);
			addPlayer(player);
			Bukkit.getScheduler().runTaskLater(CrazyAdvancements.getInstance(), new Runnable() {
				
				@Override
				public void run() {
					CrazyAdvancements.setActiveTab(player, rootAdvancement);
				}
			}, 5);
		}
	}
	
	/**
	 * Updates/Refreshes the player
	 * 
	 * @param player Player to update
	 * @param tab Tab to update
	 */
	public void update(Player player, NameKey tab) {
		if(players.contains(player)) {
			NameKey rootAdvancement = CrazyAdvancements.getActiveTab(player);
			CrazyAdvancements.clearActiveTab(player);
			addPlayer(player, tab);
			Bukkit.getScheduler().runTaskLater(CrazyAdvancements.getInstance(), new Runnable() {
				
				@Override
				public void run() {
					CrazyAdvancements.setActiveTab(player, rootAdvancement);
				}
			}, 5);
		}
	}
	
	/**
	 * Updates advancement progress for a player
	 * 
	 * @param player Player to update
	 * @param advancementsUpdated An array of advancement to update progress
	 */
	public void updateProgress(Player player, Advancement... advancementsUpdated) {
		updateProgress(player, false, true, advancementsUpdated);
	}
	
	private void updateProgress(Player player, boolean alreadyGranted, boolean fireEvent, Advancement... advancementsUpdated) {
		if(players.contains(player)) {
			Collection<net.minecraft.advancements.Advancement> advs = new ArrayList<>();
			Set<MinecraftKey> remove = new HashSet<>();
			Map<MinecraftKey, AdvancementProgress> prgs = new HashMap<>();
			
			for(Advancement advancement : advancementsUpdated) {
				if(advancements.contains(advancement)) {
					checkAwarded(player, advancement);
					
					AdvancementProgress advPrg = advancement.getProgress(player);
					boolean hidden = advancement.getHiddenStatus(player);
					
					
					advPrg.a(advancement.getSavedCriteria(), advancement.getSavedCriteriaRequirements());
					
					HashSet<String> awarded = advancement.getAwardedCriteria(player.getUniqueId());
					
					for(String criterion : advancement.getSavedCriteria().keySet()) {
						if(awarded.contains(criterion)) {
							CriterionProgress critPrg = advPrg.getCriterionProgress(criterion);
							critPrg.b();
						} else {
							CriterionProgress critPrg = advPrg.getCriterionProgress(criterion);
							critPrg.c();
						}
					}
					
					advancement.setProgress(player, advPrg);
					prgs.put(advancement.getName().getMinecraftKey(), advPrg);
					
					if(hidden && advPrg.isDone()) {
						eu.endercentral.crazy_advancements.AdvancementDisplay display = advancement.getDisplay();
						
						AdvancementRewards advRewards = new AdvancementRewards(0, new MinecraftKey[0], new MinecraftKey[0], null);
						
						ItemStack icon = CraftItemStack.asNMSCopy(display.getIcon());
						
						MinecraftKey backgroundTexture = null;
						boolean hasBackgroundTexture = display.getBackgroundTexture() != null;
						
						if(hasBackgroundTexture) {
							backgroundTexture = new MinecraftKey(display.getBackgroundTexture());
						}
						
						Map<String, Criterion> advCriteria = new HashMap<>();
						String[][] advRequirements = new String[][] {};
						
						if(advancement.getSavedCriteria() == null) {
							for(int i = 0; i < advancement.getCriteria(); i++) {
								advCriteria.put(criterionPrefix + i, new Criterion(new CriterionInstance() {
									@Override
									public JsonObject a(LootSerializationContext arg0) {
										return null;
									}
									
									@Override
									public MinecraftKey a() {
										return new MinecraftKey(criterionNamespace, criterionKey);
									}
								}));
							}
							advancement.saveCriteria(advCriteria);
						} else {
							advCriteria = advancement.getSavedCriteria();
						}
						
						if(advancement.getSavedCriteriaRequirements() == null) {
							ArrayList<String[]> fixedRequirements = new ArrayList<>();
							for(String name : advCriteria.keySet()) {
								fixedRequirements.add(new String[] {name});
							}
							advRequirements = Arrays.stream(fixedRequirements.toArray()).toArray(String[][]::new);
							advancement.saveCriteriaRequirements(advRequirements);
						} else {
							advRequirements = advancement.getSavedCriteriaRequirements();
						}
						
						AdvancementDisplay advDisplay = new AdvancementDisplay(icon, display.getTitle().getBaseComponent(), display.getDescription().getBaseComponent(), backgroundTexture, display.getFrame().getNMS(), display.isToastShown(), display.isAnnouncedToChat(), hidden ? hiddenBoolean : false);
						advDisplay.a(display.generateX() - getSmallestX(advancement.getTab()), display.generateY() - getSmallestY(advancement.getTab()));
						
						net.minecraft.advancements.Advancement adv = new net.minecraft.advancements.Advancement(advancement.getName().getMinecraftKey(), advancement.getParent() == null ? null : advancement.getParent().getSavedAdvancement(), advDisplay, advRewards, advCriteria, advRequirements);
						
						advs.add(adv);
					}
					
					
					if(!alreadyGranted) {
						if(advPrg.isDone()) {
							grantAdvancement(player, advancement, true, false, fireEvent);
						}
					}
				}
			}
			
			PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(false, advs, remove, prgs);
			((CraftPlayer)player).getHandle().b.sendPacket(packet);
		}
	}
	
	/**
	 * Updates all possibly affected visibilities for all parents and childs
	 * 
	 * @param player Player to update
	 * @param from Advancement to check from
	 */
	public void updateAllPossiblyAffectedVisibilities(Player player, Advancement from) {
		List<Advancement> updated = from.getRow();
		for(Advancement adv : updated) {
			updateVisibility(player, adv);
		}
	}
	
	/**
	 * Updates the visibility
	 * 
	 * @param player Player to update
	 * @param advancement Advancement to update
	 */
	public void updateVisibility(Player player, Advancement advancement) {
		if(players.contains(player)) {
			Collection<net.minecraft.advancements.Advancement> advs = new ArrayList<>();
			Set<MinecraftKey> remove = new HashSet<>();
			Map<MinecraftKey, AdvancementProgress> prgs = new HashMap<>();
			
			if(advancements.contains(advancement)) {
				checkAwarded(player, advancement);
				
				eu.endercentral.crazy_advancements.AdvancementDisplay display = advancement.getDisplay();
				boolean hidden = !display.isVisible(player, advancement);
				
				if(hidden == advancement.getHiddenStatus(player)) {
					return;
				}
				
				advancement.saveHiddenStatus(player, hidden);
				
				if(!hidden || hiddenBoolean) {
					remove.add(advancement.getName().getMinecraftKey());
					
					AdvancementRewards advRewards = new AdvancementRewards(0, new MinecraftKey[0], new MinecraftKey[0], null);
					
					ItemStack icon = CraftItemStack.asNMSCopy(display.getIcon());
					
					MinecraftKey backgroundTexture = null;
					boolean hasBackgroundTexture = display.getBackgroundTexture() != null;
					
					if(hasBackgroundTexture) {
						backgroundTexture = new MinecraftKey(display.getBackgroundTexture());
					}
					
					Map<String, Criterion> advCriteria = new HashMap<>();
					String[][] advRequirements = new String[][] {};
					
					if(advancement.getSavedCriteria() == null) {
						for(int i = 0; i < advancement.getCriteria(); i++) {
							advCriteria.put(criterionPrefix + i, new Criterion(new CriterionInstance() {
								@Override
								public JsonObject a(LootSerializationContext arg0) {
									return null;
								}
								
								@Override
								public MinecraftKey a() {
									return new MinecraftKey(criterionNamespace, criterionKey);
								}
							}));
						}
						advancement.saveCriteria(advCriteria);
					} else {
						advCriteria = advancement.getSavedCriteria();
					}
					
					if(advancement.getSavedCriteriaRequirements() == null) {
						ArrayList<String[]> fixedRequirements = new ArrayList<>();
						for(String name : advCriteria.keySet()) {
							fixedRequirements.add(new String[] {name});
						}
						advRequirements = Arrays.stream(fixedRequirements.toArray()).toArray(String[][]::new);
						advancement.saveCriteriaRequirements(advRequirements);
					} else {
						advRequirements = advancement.getSavedCriteriaRequirements();
					}
					
					
					
					
					
					boolean showToast = display.isToastShown();
					
					AdvancementDisplay advDisplay = new AdvancementDisplay(icon, display.getTitle().getBaseComponent(), display.getDescription().getBaseComponent(), backgroundTexture, display.getFrame().getNMS(), showToast, display.isAnnouncedToChat(), hidden ? hiddenBoolean : false);
					advDisplay.a(display.generateX() - getSmallestX(advancement.getTab()), display.generateY() - getSmallestY(advancement.getTab()));
					
					net.minecraft.advancements.Advancement adv = new net.minecraft.advancements.Advancement(advancement.getName().getMinecraftKey(), advancement.getParent() == null ? null : advancement.getParent().getSavedAdvancement(), advDisplay, advRewards, advCriteria, advRequirements);
					
					advs.add(adv);
					
					AdvancementProgress advPrg = advancement.getProgress(player);
					advPrg.a(advCriteria, advRequirements);
					
					for(String criterion : advancement.getAwardedCriteria().get(player.getUniqueId().toString())) {
						CriterionProgress critPrg = advPrg.getCriterionProgress(criterion);
						critPrg.b();
					}
					
					advancement.setProgress(player, advPrg);
					
					prgs.put(advancement.getName().getMinecraftKey(), advPrg);
				}
			}
			
			//Packet
			PacketPlayOutAdvancements packet = new PacketPlayOutAdvancements(false, advs, remove, prgs);
			((CraftPlayer)player).getHandle().b.sendPacket(packet);
		}
	}
	
	/**
	 * 
	 * @return A list of all advancements in the manager
	 */
	public ArrayList<Advancement> getAdvancements() {
		return (ArrayList<Advancement>) advancements.clone();
	}
	
	/**
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
	 * Displays a message to all players in the manager<br>
	 * Note that this doesn't grant the advancement
	 * 
	 * @param player Player which has received an advancement
	 * @param advancement Advancement Player has received
	 */
	public void displayMessage(Player player, Advancement advancement) {
		BaseComponent message = advancement.getMessage(player);
		
		for(Player managerPlayer : getPlayers()) {
			managerPlayer.spigot().sendMessage(ChatMessageType.CHAT, message);
		}
	}
	
	
	/**
	 * 
	 * @return true if advancement messages will be shown by default in this manager<br>false if advancement messages will never be shown in this manager
	 */
	public boolean isAnnounceAdvancementMessages() {
		return announceAdvancementMessages;
	}
	
	/**
	 * Changes if advancement messages will be shown by default in this manager
	 * 
	 * @param announceAdvancementMessages
	 */
	public void setAnnounceAdvancementMessages(boolean announceAdvancementMessages) {
		this.announceAdvancementMessages = announceAdvancementMessages;
	}
	
	/**
	 * Makes the AdvancementManager accessible
	 * 
	 * @param name Unique Name, case insensitive
	 */
	public void makeAccessible(String name) {
		name = name.toLowerCase();
		if(name.equals("file")) {
			throw new RuntimeException("There is already an AdvancementManager with Name '" + name + "'!");
		}
		if(accessible.containsKey(name)) {
			throw new RuntimeException("There is already an AdvancementManager with Name '" + name + "'!");
		} else if(accessible.containsValue(this)) {
			throw new RuntimeException("AdvancementManager is already accessible with a different Name!");
		}
		accessible.put(name, this);
	}
	
	/**
	 * Resets Accessibility-Status and Name
	 * 
	 */
	public void resetAccessible() {
		Iterator<String> it = accessible.keySet().iterator();
		while(it.hasNext()) {
			String name = it.next();
			if(accessible.get(name).equals(this)) {
				it.remove();
				break;
			}
		}
	}
	
	/**
	 * Returns the Unique Name if AdvancementManager is accessible
	 * 
	 * @return Name or null if not accessible
	 */
	public String getName() {
		for(String name : accessible.keySet()) {
			if(accessible.get(name).equals(this)) return name;
		}
		return null;
	}
	
	
	
	
	
	
	
	
	
	
	
	protected void checkAwarded(Player player, Advancement advancement) {
		Map<String, Criterion> advCriteria = new HashMap<>();
		String[][] advRequirements = new String[][] {};
		
		if(advancement.getSavedCriteria() == null) {
			for(int i = 0; i < advancement.getCriteria(); i++) {
				advCriteria.put(criterionPrefix + i, new Criterion(new CriterionInstance() {
					@Override
					public JsonObject a(LootSerializationContext arg0) {
						return null;
					}
					
					@Override
					public MinecraftKey a() {
						return new MinecraftKey(criterionNamespace, criterionKey);
					}
				}));
			}
			advancement.saveCriteria(advCriteria);
		} else {
			advCriteria = advancement.getSavedCriteria();
		}
		
		if(advancement.getSavedCriteriaRequirements() == null) {
			ArrayList<String[]> fixedRequirements = new ArrayList<>();
			for(String name : advCriteria.keySet()) {
				fixedRequirements.add(new String[] {name});
			}
			advRequirements = Arrays.stream(fixedRequirements.toArray()).toArray(String[][]::new);
			advancement.saveCriteriaRequirements(advRequirements);
		} else {
			advRequirements = advancement.getSavedCriteriaRequirements();
		}
		
		Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
		if(!awardedCriteria.containsKey(player.getUniqueId().toString())) {
    		awardedCriteria.put(player.getUniqueId().toString(), new HashSet<>());
    	}
	}
	
	protected void checkAwarded(UUID uuid, Advancement advancement) {
		Map<String, Criterion> advCriteria = new HashMap<>();
		String[][] advRequirements = new String[][] {};
		
		if(advancement.getSavedCriteria() == null) {
			for(int i = 0; i < advancement.getCriteria(); i++) {
				advCriteria.put(criterionPrefix + i, new Criterion(new CriterionInstance() {
					@Override
					public JsonObject a(LootSerializationContext arg0) {
						return null;
					}
					
					@Override
					public MinecraftKey a() {
						return new MinecraftKey(criterionNamespace, criterionKey);
					}
				}));
			}
			advancement.saveCriteria(advCriteria);
		} else {
			advCriteria = advancement.getSavedCriteria();
		}
		
		if(advancement.getSavedCriteriaRequirements() == null) {
			ArrayList<String[]> fixedRequirements = new ArrayList<>();
			for(String name : advCriteria.keySet()) {
				fixedRequirements.add(new String[] {name});
			}
			advRequirements = Arrays.stream(fixedRequirements.toArray()).toArray(String[][]::new);
			advancement.saveCriteriaRequirements(advRequirements);
		} else {
			advRequirements = advancement.getSavedCriteriaRequirements();
		}
		
		Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
		if(!awardedCriteria.containsKey(uuid.toString())) {
    		awardedCriteria.put(uuid.toString(), new HashSet<>());
    	}
	}
	
	private boolean isOnline(UUID uuid) {
		Player player = Bukkit.getPlayer(uuid);
		return player != null && player.isOnline();
	}
	
	/**
	 * Grants an advancement
	 * 
	 * @param player Reciever
	 * @param advancement Advancement to grant
	 */
	public void grantAdvancement(Player player, Advancement advancement) {
		grantAdvancement(player, advancement, false, true, true);
	}
	
	private void grantAdvancement(Player player, Advancement advancement, boolean alreadyGranted, boolean updateProgress, boolean fireEvent) {
		checkAwarded(player, advancement);
		Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
		
		HashSet<String> awarded = advancement.getAwardedCriteria(player.getUniqueId());
		for(String criterion : advancement.getSavedCriteria().keySet()) {
			awarded.add(criterion);
		}
		awardedCriteria.put(player.getUniqueId().toString(), awarded);
		advancement.setAwardedCriteria(awardedCriteria);
		
		if(fireEvent) {
			boolean announceChat = advancement.getDisplay().isAnnouncedToChat() && CrazyAdvancements.getInitiatedPlayers().contains(player) && CrazyAdvancements.isAnnounceAdvancementMessages() && isAnnounceAdvancementMessages();
			
			AdvancementGrantEvent event = new AdvancementGrantEvent(this, advancement, player, announceChat);
			Bukkit.getPluginManager().callEvent(event);
			if(advancement.getReward() != null) advancement.getReward().onGrant(player);
			if(event.isDisplayMessage()) {
				displayMessage(player, advancement);
			}
		}
		
		if(updateProgress) {
			updateProgress(player, alreadyGranted, false, advancement);
			updateAllPossiblyAffectedVisibilities(player, advancement);
		}
	}
	
	/**
	 * Grants an advancement, also works with offline players
	 * 
	 * @param uuid Receiver UUID
	 * @param advancement Advancement to grant
	 */
	public void grantAdvancement(UUID uuid, Advancement advancement) {
		if(isOnline(uuid)) {
			grantAdvancement(Bukkit.getPlayer(uuid), advancement);
		} else {
			checkAwarded(uuid, advancement);
			
			Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
			
			HashSet<String> awarded = advancement.getAwardedCriteria(uuid);
			for(String criterion : advancement.getSavedCriteria().keySet()) {
				awarded.add(criterion);
			}
			awardedCriteria.put(uuid.toString(), awarded);
			advancement.setAwardedCriteria(awardedCriteria);
			
			OfflineAdvancementGrantEvent event = new OfflineAdvancementGrantEvent(this, advancement, uuid);
			Bukkit.getPluginManager().callEvent(event);
		}
	}
	
	/**
	 * Revokes an advancement
	 * 
	 * @param player Receiver
	 * @param advancement Advancement to revoke
	 */
	public void revokeAdvancement(Player player, Advancement advancement) {
		checkAwarded(player, advancement);
		
		advancement.setAwardedCriteria(new HashMap<String, HashSet<String>>());
		
		updateProgress(player, advancement);
		updateAllPossiblyAffectedVisibilities(player, advancement);
		
		AdvancementRevokeEvent event = new AdvancementRevokeEvent(this, advancement, player);
		Bukkit.getPluginManager().callEvent(event);
	}
	
	/**
	 * Revokes an advancement, also works with offline players
	 * 
	 * @param uuid Receiver UUID
	 * @param advancement Advancement to revoke
	 */
	public void revokeAdvancement(UUID uuid, Advancement advancement) {
		if(isOnline(uuid)) {
			revokeAdvancement(Bukkit.getPlayer(uuid), advancement);
		} else {
			checkAwarded(uuid, advancement);
			
			advancement.setAwardedCriteria(new HashMap<String, HashSet<String>>());
			
			OfflineAdvancementRevokeEvent event = new OfflineAdvancementRevokeEvent(this, advancement, uuid);
			Bukkit.getPluginManager().callEvent(event);
		}
	}
	
	/**
	 * Grants criteria for an advancement
	 * 
	 * @param player Receiver
	 * @param advancement
	 * @param criteria Array of criteria to grant
	 */
	public void grantCriteria(Player player, Advancement advancement, String... criteria) {
		checkAwarded(player, advancement);
		
		Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
		
		HashSet<String> awarded = advancement.getAwardedCriteria(player.getUniqueId());
		for(String criterion : criteria) {
			awarded.add(criterion);
		}
		awardedCriteria.put(player.getUniqueId().toString(), awarded);
		advancement.setAwardedCriteria(awardedCriteria);
		
		updateProgress(player, false, true, advancement);
		updateAllPossiblyAffectedVisibilities(player, advancement);
		
		CriteriaGrantEvent event = new CriteriaGrantEvent(this, advancement, criteria, player);
		Bukkit.getPluginManager().callEvent(event);
	}
	
	/**
	 * Grans criteria for an advancement, also works with offline players
	 * 
	 * @param uuid
	 * @param advancement
	 * @param criteria
	 */
	public void grantCriteria(UUID uuid, Advancement advancement, String... criteria) {
		if(isOnline(uuid)) {
			grantCriteria(Bukkit.getPlayer(uuid), advancement, criteria);
		} else {
			checkAwarded(uuid, advancement);
			
			Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
			
			HashSet<String> awarded = advancement.getAwardedCriteria(uuid);
			for(String criterion : criteria) {
				awarded.add(criterion);
			}
			awardedCriteria.put(uuid.toString(), awarded);
			advancement.setAwardedCriteria(awardedCriteria);
			
			OfflineCriteriaGrantEvent event = new OfflineCriteriaGrantEvent(this, advancement, criteria, uuid);
			Bukkit.getPluginManager().callEvent(event);
		}
	}
	
	/**
	 * Revokes criteria for an advancement
	 * 
	 * @param player Receiver
	 * @param advancement
	 * @param criteria Array of criteria to revoke
	 */
	public void revokeCriteria(Player player, Advancement advancement, String... criteria) {
		checkAwarded(player, advancement);
		Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
		
		AdvancementProgress advPrg = advancement.getProgress(player);
		if(advPrg.isDone()) {
			AdvancementRevokeEvent event = new AdvancementRevokeEvent(this, advancement, player);
			Bukkit.getPluginManager().callEvent(event);
		}
		
		HashSet<String> awarded = advancement.getAwardedCriteria(player.getUniqueId());
		for(String criterion : criteria) {
			if(awarded.contains(criterion)) awarded.remove(criterion);
		}
		awardedCriteria.put(player.getUniqueId().toString(), awarded);
		advancement.setAwardedCriteria(awardedCriteria);
		
		updateProgress(player, advancement);
		updateAllPossiblyAffectedVisibilities(player, advancement);
		
		CriteriaGrantEvent event = new CriteriaGrantEvent(this, advancement, criteria, player);
		Bukkit.getPluginManager().callEvent(event);
	}
	
	/**
	 * Revokes criteria for an advancement, also works with offline players
	 * 
	 * @param uuid Receiver UUID
	 * @param advancement
	 * @param criteria Array of criteria to revoke
	 */
	public void revokeCriteria(UUID uuid, Advancement advancement, String... criteria) {
		if(isOnline(uuid)) {
			revokeCriteria(Bukkit.getPlayer(uuid), advancement, criteria);
		} else {
			checkAwarded(uuid, advancement);
			Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
			
			AdvancementProgress advPrg = advancement.getProgress(uuid);
			if(advPrg.isDone()) {
				OfflineAdvancementRevokeEvent event = new OfflineAdvancementRevokeEvent(this, advancement, uuid);
				Bukkit.getPluginManager().callEvent(event);
			}
			
			HashSet<String> awarded = advancement.getAwardedCriteria(uuid);
			for(String criterion : criteria) {
				if(awarded.contains(criterion)) awarded.remove(criterion);
			}
			awardedCriteria.put(uuid.toString(), awarded);
			advancement.setAwardedCriteria(awardedCriteria);
			
			OfflineCriteriaGrantEvent event = new OfflineCriteriaGrantEvent(this, advancement, criteria, uuid);
			Bukkit.getPluginManager().callEvent(event);
		}
	}
	
	/**
	 * Sets the criteria progress for an advancement<br>
	 * Might not work as expected when using features for experts<br>
	 * Is the only method triggering CriteriaProgressChangeEvent
	 * 
	 * @param player Receiver
	 * @param advancement
	 * @param progress
	 */
	public void setCriteriaProgress(Player player, Advancement advancement, int progress) {
		checkAwarded(player, advancement);
		Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
		
		HashSet<String> awarded = advancement.getAwardedCriteria(player.getUniqueId());
		
		CriteriaProgressChangeEvent event = new CriteriaProgressChangeEvent(this, advancement, player, awarded.size(), progress);
		Bukkit.getPluginManager().callEvent(event);
		progress = event.getProgress();
		
		int difference = Math.abs(awarded.size() - progress);
		
		if(awarded.size() > progress) {
			//Count down
			int i = 0;
			for(String criterion : advancement.getSavedCriteria().keySet()) {
				if(i >= difference) break;
				if(awarded.contains(criterion)) {
					awarded.remove(criterion);
					i++;
				}
			}
		} else if(awarded.size() < progress) {
			//Count up
			int i = 0;
			for(String criterion : advancement.getSavedCriteria().keySet()) {
				if(i >= difference) break;
				if(!awarded.contains(criterion)) {
					awarded.add(criterion);
					i++;
				}
			}
		}
		
		awardedCriteria.put(player.getUniqueId().toString(), awarded);
		advancement.setAwardedCriteria(awardedCriteria);
		
		updateProgress(player, false, true, advancement);
		updateAllPossiblyAffectedVisibilities(player, advancement);
	}
	
	/**
	 * Sets the criteria progress for an advancement, also works for offline players<br>
	 * Might not work as expected when using features for experts<br>
	 * Is the only method triggering CriteriaProgressChangeEvent
	 * 
	 * @param uuid Receiver UUID
	 * @param advancement
	 * @param progress
	 */
	public void setCriteriaProgress(UUID uuid, Advancement advancement, int progress) {
		if(isOnline(uuid)) {
			setCriteriaProgress(Bukkit.getPlayer(uuid), advancement, progress);
		} else {
			checkAwarded(uuid, advancement);
			Map<String, HashSet<String>> awardedCriteria = advancement.getAwardedCriteria();
			
			HashSet<String> awarded = advancement.getAwardedCriteria(uuid);
			
			OfflineCriteriaProgressChangeEvent event = new OfflineCriteriaProgressChangeEvent(this, advancement, uuid, awarded.size(), progress);
			Bukkit.getPluginManager().callEvent(event);
			progress = event.getProgress();
			
			int difference = Math.abs(awarded.size() - progress);
			
			if(awarded.size() > progress) {
				//Count down
				int i = 0;
				for(String criterion : advancement.getSavedCriteria().keySet()) {
					if(i >= difference) break;
					if(awarded.contains(criterion)) {
						awarded.remove(criterion);
						i++;
					}
				}
			} else if(awarded.size() < progress) {
				//Count up
				int i = 0;
				for(String criterion : advancement.getSavedCriteria().keySet()) {
					if(i >= difference) break;
					if(!awarded.contains(criterion)) {
						awarded.add(criterion);
						i++;
					}
				}
			}
			
			awardedCriteria.put(uuid.toString(), awarded);
			advancement.setAwardedCriteria(awardedCriteria);
		}
	}
	
	/**
	 * 
	 * @param player
	 * @param advancement
	 * @return The criteria progress
	 */
	public int getCriteriaProgress(Player player, Advancement advancement) {
		checkAwarded(player, advancement);
		return advancement.getAwardedCriteria(player.getUniqueId()).size();
	}
	
	/**
	 * 
	 * @param uuid
	 * @param advancement
	 * @return The criteria progress
	 */
	public int getCriteriaProgress(UUID uuid, Advancement advancement) {
		checkAwarded(uuid, advancement);
		return advancement.getAwardedCriteria(uuid).size();
	}
	
	private String getSavePath(Player player, String namespace) {
		return getSaveDirectory(namespace) + (CrazyAdvancements.isUseUUID() ? player.getUniqueId() : player.getName()) + ".json";
	}
	
	private String getSaveDirectory(String namespace) {
		return CrazyAdvancements.getInstance().getDataFolder().getAbsolutePath() + File.separator + "saved_data" + File.separator + namespace + File.separator;
	}
	
	private File getSaveFile(Player player, String namespace) {
		File file = new File(getSaveDirectory(namespace));
		file.mkdirs();
		return new File(getSavePath(player, namespace));
	}
	
	
	private String getSavePath(UUID uuid, String namespace) {
		return getSaveDirectory(namespace) + uuid + ".json";
	}
	
	private File getSaveFile(UUID uuid, String namespace) {
		File file = new File(getSaveDirectory(namespace));
		file.mkdirs();
		return new File(getSavePath(uuid, namespace));
	}
	
	//Online Save/Load
	
	/**
	 * 
	 * @param player Player to check
	 * @return A JSON String representation of the progress for a player
	 */
	public String getProgressJSON(Player player) {
		HashMap<String, List<String>> prg = new HashMap<>();
		
		for(Advancement advancement : getAdvancements()) {
			String nameKey = advancement.getName().toString();
			SaveMethod saveMethod = advancement.getSaveMethod();
			
			if(saveMethod == SaveMethod.NUMBER) {
				int criteriaProgress = getCriteriaProgress(player, advancement);
				ArrayList<String> progress = new ArrayList<>();
				progress.add("NUM");//Indicator for Number Save Method
				progress.add("" + criteriaProgress);
				prg.put(nameKey, progress);
			} else {
				ArrayList<String> progress = new ArrayList<>(advancement.getAwardedCriteria(player.getUniqueId()));
				prg.put(nameKey, progress);
			}
		}
		
		check();
		String json = gson.toJson(prg);
		
		return json;
	}
	
	/**
	 * 
	 * @param player Player to check
	 * @param namespace Namespace to check
	 * @return A JSON String representation of the progress for a player in a specified namespace
	 */
	public String getProgressJSON(Player player, String namespace) {
		HashMap<String, List<String>> prg = new HashMap<>();
		
		for(Advancement advancement : getAdvancements()) {
			String anotherNamespace = advancement.getName().getNamespace();
			
			if(namespace.equalsIgnoreCase(anotherNamespace)) {
				String nameKey = advancement.getName().toString();
				SaveMethod saveMethod = advancement.getSaveMethod();
				
				if(saveMethod == SaveMethod.NUMBER) {
					int criteriaProgress = getCriteriaProgress(player, advancement);
					ArrayList<String> progress = new ArrayList<>();
					progress.add("NUM");//Indicator for Number Save Method
					progress.add("" + criteriaProgress);
					prg.put(nameKey, progress);
				} else {
					ArrayList<String> progress = new ArrayList<>(advancement.getAwardedCriteria(player.getUniqueId()));
					prg.put(nameKey, progress);
				}
			}
		}
		
		check();
		String json = gson.toJson(prg);
		
		return json;
	}
	
	/**
	 * Saves the progress
	 * 
	 * @param player Player to check
	 * @param namespace Namespace to check
	 */
	public void saveProgress(Player player, String namespace) {
		File saveFile = getSaveFile(player, namespace);
		
		String json = getProgressJSON(player, namespace);
		
		try {
			if(!saveFile.exists()) {
				saveFile.createNewFile();
			}
			FileWriter w = new FileWriter(saveFile);
			w.write(json);
			w.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//Load Progress
	
	/**
	 * Loads the progress
	 * 
	 * @param player Player to check
	 * @param namespace Namespace to check
	 */
	public void loadProgress(Player player, String namespace) {
		File saveFile = getSaveFile(player, namespace);
		
		if(saveFile.exists() && saveFile.isFile()) {
			HashMap<String, List<String>> prg = getProgress(player, namespace);
			
			for(Advancement advancement : advancements) {
				if(advancement.getName().getNamespace().equalsIgnoreCase(namespace)) {
					checkAwarded(player, advancement);
					
					String nameKey = advancement.getName().toString();
					
					if(prg.containsKey(nameKey)) {
						List<String> loaded = prg.get(nameKey);
						SaveMethod saveMethod = advancement.getSaveMethod();
						
						if(saveMethod == SaveMethod.NUMBER) {
							if(loaded.size() == 2) {
								if(loaded.get(0).equals("NUM")) {
									try {
										int progress = Integer.parseInt(loaded.get(1));
										setCriteriaProgress(player, advancement, progress);
									} catch (NumberFormatException e) {
										//Use Default Load Method
										saveMethod = SaveMethod.DEFAULT;
									}
								} else {
									//Use Default Load Method
									saveMethod = SaveMethod.DEFAULT;
								}
							} else {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						}
						
						if(saveMethod == SaveMethod.DEFAULT) {
							grantCriteria(player, advancement, loaded.toArray(new String[loaded.size()]));
						}
					}
				}
			}
		}
	}
	
	/**
	 * Loads the progress
	 * 
	 * @param player Player to check
	 * @param advancementsLoaded Array of advancements to check, all advancements which arent in the same namespace as the first one will be ignored
	 */
	public void loadProgress(Player player, Advancement... advancementsLoaded) {
		if(advancementsLoaded.length == 0) return;
		List<Advancement> advancements = Arrays.asList(advancementsLoaded);
		
		String namespace = advancements.get(0).getName().getNamespace();
		
		File saveFile = getSaveFile(player, namespace);
		
		if(saveFile.exists() && saveFile.isFile()) {
			HashMap<String, List<String>> prg = getProgress(player, namespace);
			
			for(Advancement advancement : advancements) {
				if(advancement.getName().getNamespace().equalsIgnoreCase(namespace)) {
					checkAwarded(player, advancement);
					
					String nameKey = advancement.getName().toString();
					
					if(prg.containsKey(nameKey)) {
						List<String> loaded = prg.get(nameKey);
						SaveMethod saveMethod = advancement.getSaveMethod();
						
						if(saveMethod == SaveMethod.NUMBER) {
							if(loaded.size() == 2) {
								if(loaded.get(0).equals("NUM")) {
									try {
										int progress = Integer.parseInt(loaded.get(1));
										setCriteriaProgress(player, advancement, progress);
									} catch (NumberFormatException e) {
										//Use Default Load Method
										saveMethod = SaveMethod.DEFAULT;
									}
								} else {
									//Use Default Load Method
									saveMethod = SaveMethod.DEFAULT;
								}
							} else {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						}
						
						if(saveMethod == SaveMethod.DEFAULT) {
							grantCriteria(player, advancement, loaded.toArray(new String[loaded.size()]));
						}
					}
				}
			}
		}
	}
	
	/**
	 * Loads the progress with a custom JSON String
	 * 
	 * @param player Player to check
	 * @param json JSON String to load from
	 * @param advancementsLoaded Array of advancements to check
	 */
	public void loadCustomProgress(Player player, String json, Advancement... advancementsLoaded) {
		if(advancementsLoaded.length == 0) return;
		List<Advancement> advancements = Arrays.asList(advancementsLoaded);
				
		HashMap<String, List<String>> prg = getCustomProgress(json);
		
		for(Advancement advancement : advancements) {
			checkAwarded(player, advancement);
			
			String nameKey = advancement.getName().toString();
			
			if(prg.containsKey(nameKey)) {
				List<String> loaded = prg.get(nameKey);
				SaveMethod saveMethod = advancement.getSaveMethod();
				
				if(saveMethod == SaveMethod.NUMBER) {
					if(loaded.size() == 2) {
						if(loaded.get(0).equals("NUM")) {
							try {
								int progress = Integer.parseInt(loaded.get(1));
								setCriteriaProgress(player, advancement, progress);
							} catch (NumberFormatException e) {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						} else {
							//Use Default Load Method
							saveMethod = SaveMethod.DEFAULT;
						}
					} else {
						//Use Default Load Method
						saveMethod = SaveMethod.DEFAULT;
					}
				}
				
				if(saveMethod == SaveMethod.DEFAULT) {
					grantCriteria(player, advancement, loaded.toArray(new String[loaded.size()]));
				}
			}
		}
	}
	
	/**
	 * Loads the progress with a custom JSON String
	 * 
	 * @param player Player to check
	 * @param json JSON String to load from
	 */
	public void loadCustomProgress(Player player, String json) {
		HashMap<String, List<String>> prg = getCustomProgress(json);
		
		for(Advancement advancement : advancements) {
			checkAwarded(player, advancement);
			
			String nameKey = advancement.getName().toString();
			
			if(prg.containsKey(nameKey)) {
				List<String> loaded = prg.get(nameKey);
				SaveMethod saveMethod = advancement.getSaveMethod();
				
				if(saveMethod == SaveMethod.NUMBER) {
					if(loaded.size() == 2) {
						if(loaded.get(0).equals("NUM")) {
							try {
								int progress = Integer.parseInt(loaded.get(1));
								setCriteriaProgress(player, advancement, progress);
							} catch (NumberFormatException e) {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						} else {
							//Use Default Load Method
							saveMethod = SaveMethod.DEFAULT;
						}
					} else {
						//Use Default Load Method
						saveMethod = SaveMethod.DEFAULT;
					}
				}
				
				if(saveMethod == SaveMethod.DEFAULT) {
					grantCriteria(player, advancement, loaded.toArray(new String[loaded.size()]));
				}
			}
		}
	}
	
	/**
	 * Loads the progress with a custom JSON String
	 * 
	 * @param player Player to check
	 * @param json JSON String to load from
	 * @param namespace Namespace to check
	 */
	public void loadCustomProgress(Player player, String json, String namespace) {
		HashMap<String, List<String>> prg = getCustomProgress(json);
		
		for(Advancement advancement : advancements) {
			if(advancement.getName().getNamespace().equalsIgnoreCase(namespace)) {
				checkAwarded(player, advancement);
				
				String nameKey = advancement.getName().toString();
				
				if(prg.containsKey(nameKey)) {
					List<String> loaded = prg.get(nameKey);
					SaveMethod saveMethod = advancement.getSaveMethod();
					
					if(saveMethod == SaveMethod.NUMBER) {
						if(loaded.size() == 2) {
							if(loaded.get(0).equals("NUM")) {
								try {
									int progress = Integer.parseInt(loaded.get(1));
									setCriteriaProgress(player, advancement, progress);
								} catch (NumberFormatException e) {
									//Use Default Load Method
									saveMethod = SaveMethod.DEFAULT;
								}
							} else {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						} else {
							//Use Default Load Method
							saveMethod = SaveMethod.DEFAULT;
						}
					}
					
					if(saveMethod == SaveMethod.DEFAULT) {
						grantCriteria(player, advancement, loaded.toArray(new String[loaded.size()]));
					}
				}
			}
		}
	}
	
	private HashMap<String, List<String>> getProgress(Player player, String namespace) {
		File saveFile = getSaveFile(player, namespace);
		
		try {
			FileReader os = new FileReader(saveFile);
			
			JsonParser parser = new JsonParser();
			JsonElement element = parser.parse(os);
			os.close();
			
			check();
			
			HashMap<String, List<String>> progressList = gson.fromJson(element, progressListType);
			
			return progressList;
		} catch (Exception ex) {
			ex.printStackTrace();
			return new HashMap<>();
		}
	}
	
	private HashMap<String, List<String>> getCustomProgress(String json) {
		check();
		HashMap<String, List<String>> progressList = gson.fromJson(json, progressListType);
		
		return progressList;
	}
	
	//Offline Save/Load
	
	/**
	 * 
	 * @param uuid Player UUID to check
	 * @return A JSON String representation of the progress for a player
	 */
	public String getProgressJSON(UUID uuid) {
		HashMap<String, List<String>> prg = new HashMap<>();
		
		for(Advancement advancement : getAdvancements()) {
			String nameKey = advancement.getName().toString();
			SaveMethod saveMethod = advancement.getSaveMethod();
			
			if(saveMethod == SaveMethod.NUMBER) {
				int criteriaProgress = getCriteriaProgress(uuid, advancement);
				ArrayList<String> progress = new ArrayList<>();
				progress.add("NUM");//Indicator for Number Save Method
				progress.add("" + criteriaProgress);
				prg.put(nameKey, progress);
			} else {
				ArrayList<String> progress = new ArrayList<>(advancement.getAwardedCriteria(uuid));
				prg.put(nameKey, progress);
			}
		}
		
		check();
		String json = gson.toJson(prg);
		
		return json;
	}
	
	/**
	 * 
	 * @param uuid Player UUID to check
	 * @param namespace Namespace to check
	 * @return A JSON String representation of the progress for a player in a specified namespace
	 */
	public String getProgressJSON(UUID uuid, String namespace) {
		HashMap<String, List<String>> prg = new HashMap<>();
		
		for(Advancement advancement : getAdvancements()) {
			String anotherNamespace = advancement.getName().getNamespace();
			
			if(namespace.equalsIgnoreCase(anotherNamespace)) {
				String nameKey = advancement.getName().toString();
				SaveMethod saveMethod = advancement.getSaveMethod();
				
				if(saveMethod == SaveMethod.NUMBER) {
					int criteriaProgress = getCriteriaProgress(uuid, advancement);
					ArrayList<String> progress = new ArrayList<>();
					progress.add("NUM");//Indicator for Number Save Method
					progress.add("" + criteriaProgress);
					prg.put(nameKey, progress);
				} else {
					ArrayList<String> progress = new ArrayList<>(advancement.getAwardedCriteria(uuid));
					prg.put(nameKey, progress);
				}
			}
		}
		
		check();
		String json = gson.toJson(prg);
		
		return json;
	}
	
	/**
	 * Saves the progress
	 * 
	 * @param uuid Player UUID to check
	 * @param namespace Namespace to check
	 */
	public void saveProgress(UUID uuid, String namespace) {
		File saveFile = getSaveFile(uuid, namespace);
		
		String json = getProgressJSON(uuid, namespace);
		
		try {
			if(!saveFile.exists()) {
				saveFile.createNewFile();
			}
			FileWriter w = new FileWriter(saveFile);
			w.write(json);
			w.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//Load Progress
	
	/**
	 * Loads the progress<br>
	 * <b>Recommended to only load progress for online players!</b>
	 * 
	 * @param uuid Player UUID to check
	 * @param namespace Namespace to check
	 */
	@Deprecated
	public void loadProgress(UUID uuid, String namespace) {
		File saveFile = getSaveFile(uuid, namespace);
		
		if(saveFile.exists() && saveFile.isFile()) {
			HashMap<String, List<String>> prg = getProgress(uuid, namespace);
			
			for(Advancement advancement : advancements) {
				if(advancement.getName().getNamespace().equalsIgnoreCase(namespace)) {
					checkAwarded(uuid, advancement);
					
					String nameKey = advancement.getName().toString();
					
					if(prg.containsKey(nameKey)) {
						List<String> loaded = prg.get(nameKey);
						SaveMethod saveMethod = advancement.getSaveMethod();
						
						if(saveMethod == SaveMethod.NUMBER) {
							if(loaded.size() == 2) {
								if(loaded.get(0).equals("NUM")) {
									try {
										int progress = Integer.parseInt(loaded.get(1));
										setCriteriaProgress(uuid, advancement, progress);
									} catch (NumberFormatException e) {
										//Use Default Load Method
										saveMethod = SaveMethod.DEFAULT;
									}
								} else {
									//Use Default Load Method
									saveMethod = SaveMethod.DEFAULT;
								}
							} else {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						}
						
						if(saveMethod == SaveMethod.DEFAULT) {
							grantCriteria(uuid, advancement, loaded.toArray(new String[loaded.size()]));
						}
					}
				}
			}
		}
	}
	
	/**
	 * Loads the progress<br>
	 * <b>Recommended to only load progress for online players!</b>
	 * 
	 * @param uuid Player UUID to check
	 * @param advancementsLoaded Array of advancements to check, all advancements which arent in the same namespace as the first one will be ignored
	 */
	@Deprecated
	public void loadProgress(UUID uuid, Advancement... advancementsLoaded) {
		if(advancementsLoaded.length == 0) return;
		List<Advancement> advancements = Arrays.asList(advancementsLoaded);
		
		String namespace = advancements.get(0).getName().getNamespace();
		
		File saveFile = getSaveFile(uuid, namespace);
		
		if(saveFile.exists() && saveFile.isFile()) {
			HashMap<String, List<String>> prg = getProgress(uuid, namespace);
			
			for(Advancement advancement : advancements) {
				if(advancement.getName().getNamespace().equalsIgnoreCase(namespace)) {
					checkAwarded(uuid, advancement);
					
					String nameKey = advancement.getName().toString();
					
					if(prg.containsKey(nameKey)) {
						List<String> loaded = prg.get(nameKey);
						SaveMethod saveMethod = advancement.getSaveMethod();
						
						if(saveMethod == SaveMethod.NUMBER) {
							if(loaded.size() == 2) {
								if(loaded.get(0).equals("NUM")) {
									try {
										int progress = Integer.parseInt(loaded.get(1));
										setCriteriaProgress(uuid, advancement, progress);
									} catch (NumberFormatException e) {
										//Use Default Load Method
										saveMethod = SaveMethod.DEFAULT;
									}
								} else {
									//Use Default Load Method
									saveMethod = SaveMethod.DEFAULT;
								}
							} else {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						}
						
						if(saveMethod == SaveMethod.DEFAULT) {
							grantCriteria(uuid, advancement, loaded.toArray(new String[loaded.size()]));
						}
					}
				}
			}
		}
	}
	
	/**
	 * Loads the progress with a custom JSON String<br>
	 * <b>Recommended to only load progress for online players!</b>
	 * 
	 * @param uuid Player UUID to check
	 * @param json JSON String to load from
	 * @param advancementsLoaded Array of advancements to check
	 */
	@Deprecated
	public void loadCustomProgress(UUID uuid, String json, Advancement... advancementsLoaded) {
		if(advancementsLoaded.length == 0) return;
		List<Advancement> advancements = Arrays.asList(advancementsLoaded);
				
		HashMap<String, List<String>> prg = getCustomProgress(json);
		
		for(Advancement advancement : advancements) {
			checkAwarded(uuid, advancement);
			
			String nameKey = advancement.getName().toString();
			
			if(prg.containsKey(nameKey)) {
				List<String> loaded = prg.get(nameKey);
				SaveMethod saveMethod = advancement.getSaveMethod();
				
				if(saveMethod == SaveMethod.NUMBER) {
					if(loaded.size() == 2) {
						if(loaded.get(0).equals("NUM")) {
							try {
								int progress = Integer.parseInt(loaded.get(1));
								setCriteriaProgress(uuid, advancement, progress);
							} catch (NumberFormatException e) {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						} else {
							//Use Default Load Method
							saveMethod = SaveMethod.DEFAULT;
						}
					} else {
						//Use Default Load Method
						saveMethod = SaveMethod.DEFAULT;
					}
				}
				
				if(saveMethod == SaveMethod.DEFAULT) {
					grantCriteria(uuid, advancement, loaded.toArray(new String[loaded.size()]));
				}
			}
		}
	}
	
	/**
	 * Loads the progress with a custom JSON String<br>
	 * <b>Recommended to only load progress for online players!</b>
	 * 
	 * @param uuid Player UUID to check
	 * @param json JSON String to load from
	 */
	@Deprecated
	public void loadCustomProgress(UUID uuid, String json) {
		HashMap<String, List<String>> prg = getCustomProgress(json);
		
		for(Advancement advancement : advancements) {
			checkAwarded(uuid, advancement);
			
			String nameKey = advancement.getName().toString();
			
			if(prg.containsKey(nameKey)) {
				List<String> loaded = prg.get(nameKey);
				SaveMethod saveMethod = advancement.getSaveMethod();
				
				if(saveMethod == SaveMethod.NUMBER) {
					if(loaded.size() == 2) {
						if(loaded.get(0).equals("NUM")) {
							try {
								int progress = Integer.parseInt(loaded.get(1));
								setCriteriaProgress(uuid, advancement, progress);
							} catch (NumberFormatException e) {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						} else {
							//Use Default Load Method
							saveMethod = SaveMethod.DEFAULT;
						}
					} else {
						//Use Default Load Method
						saveMethod = SaveMethod.DEFAULT;
					}
				}
				
				if(saveMethod == SaveMethod.DEFAULT) {
					grantCriteria(uuid, advancement, loaded.toArray(new String[loaded.size()]));
				}
			}
		}
	}
	
	/**
	 * Loads the progress with a custom JSON String<br>
	 * <b>Recommended to only load progress for online players!</b>
	 * 
	 * @param uuid Player UUID to check
	 * @param json JSON String to load from
	 * @param namespace Namespace to check
	 */
	@Deprecated
	public void loadCustomProgress(UUID uuid, String json, String namespace) {
		HashMap<String, List<String>> prg = getCustomProgress(json);
		
		for(Advancement advancement : advancements) {
			if(advancement.getName().getNamespace().equalsIgnoreCase(namespace)) {
				checkAwarded(uuid, advancement);
				
				String nameKey = advancement.getName().toString();
				
				if(prg.containsKey(nameKey)) {
					List<String> loaded = prg.get(nameKey);
					SaveMethod saveMethod = advancement.getSaveMethod();
					
					if(saveMethod == SaveMethod.NUMBER) {
						if(loaded.size() == 2) {
							if(loaded.get(0).equals("NUM")) {
								try {
									int progress = Integer.parseInt(loaded.get(1));
									setCriteriaProgress(uuid, advancement, progress);
								} catch (NumberFormatException e) {
									//Use Default Load Method
									saveMethod = SaveMethod.DEFAULT;
								}
							} else {
								//Use Default Load Method
								saveMethod = SaveMethod.DEFAULT;
							}
						} else {
							//Use Default Load Method
							saveMethod = SaveMethod.DEFAULT;
						}
					}
					
					if(saveMethod == SaveMethod.DEFAULT) {
						grantCriteria(uuid, advancement, loaded.toArray(new String[loaded.size()]));
					}
				}
			}
		}
	}
	
	//Unload Progress
	
	/**
	 * Unloads the progress for all advancements in the manager<br>
	 * <b>Does not work for Online Players!</b>
	 * 
	 * @param uuid Affected Player UUID
	 */
	public void unloadProgress(UUID uuid) {
		if(isOnline(uuid)) {
			throw new UnloadProgressFailedException(uuid);
		} else {
			for(Advancement advancement : getAdvancements()) {
				advancement.unsetProgress(uuid);
				advancement.unsetAwardedCriteria(uuid);
			}
		}
	}
	
	/**
	 * Unloads the progress for all advancements in the manager with a specified namespace<br>
	 * <b>Does not work for Online Players!</b>
	 * 
	 * @param uuid Affected Player UUID
	 * @param namespace Specific Namespace
	 */
	public void unloadProgress(UUID uuid, String namespace) {
		if(isOnline(uuid)) {
			throw new UnloadProgressFailedException(uuid);
		} else {
			for(Advancement advancement : getAdvancements(namespace)) {
				advancement.unsetProgress(uuid);
				advancement.unsetAwardedCriteria(uuid);
			}
		}
	}
	
	/**
	 * Unloads the progress for the given advancements<br>
	 * <b>Does not work for Online Players!</b>
	 * 
	 * @param uuid Affected Player UUID
	 * @param advancements Specific Advancements
	 */
	public void unloadProgress(UUID uuid, Advancement... advancements) {
		if(isOnline(uuid)) {
			throw new UnloadProgressFailedException(uuid);
		} else {
			for(Advancement advancement : advancements) {
				advancement.unsetProgress(uuid);
				advancement.unsetAwardedCriteria(uuid);
			}
		}
	}
	
	private HashMap<String, List<String>> getProgress(UUID uuid, String namespace) {
		File saveFile = getSaveFile(uuid, namespace);
		
		try {
			FileReader os = new FileReader(saveFile);
			
			JsonParser parser = new JsonParser();
			JsonElement element = parser.parse(os);
			os.close();
			
			check();
			
			HashMap<String, List<String>> progressList = gson.fromJson(element, progressListType);
			
			return progressList;
		} catch (Exception ex) {
			ex.printStackTrace();
			return new HashMap<>();
		}
	}
	
	
	
	
	private static Gson gson;
	private static Type progressListType;
	
	private static void check() {
		if(gson == null) {
			gson = new Gson();
		}
		if(progressListType == null) {
			progressListType = new TypeToken<HashMap<String, List<String>>>() {private static final long serialVersionUID = 5832697137241815078L;}.getType();
		}
	}
	
}