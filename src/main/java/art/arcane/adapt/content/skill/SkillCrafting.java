/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.xp.CraftingXpTransaction;
import art.arcane.adapt.content.adaptation.crafting.CraftingBackpacks;
import art.arcane.adapt.content.adaptation.crafting.CraftingBulkArtisan;
import art.arcane.adapt.content.adaptation.crafting.CraftingCompactor;
import art.arcane.adapt.content.adaptation.crafting.CraftingDeconstruction;
import art.arcane.adapt.content.adaptation.crafting.CraftingLeather;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.content.adaptation.crafting.CraftingProvisioner;
import art.arcane.adapt.content.adaptation.crafting.CraftingReconstruction;
import art.arcane.adapt.content.adaptation.crafting.CraftingSkulls;
import art.arcane.adapt.content.adaptation.crafting.CraftingStations;
import art.arcane.adapt.content.adaptation.crafting.CraftingThriftyHands;
import art.arcane.adapt.content.adaptation.crafting.CraftingTinkerer;
import art.arcane.adapt.content.adaptation.crafting.CraftingXP;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import io.papermc.paper.event.inventory.ItemCraftedEvent;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillCrafting extends SimpleSkill<SkillCrafting.Config> {
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, CraftingXpTransaction> pendingCrafts = playerState();
  private final Map<String, Long> furnaceXpMarks = new ConcurrentHashMap<>();

  public SkillCrafting() {
    super("crafting", SkillPresentation.of(SkillMessages.CRAFTING_NAME, SkillMessages.CRAFTING_ICON, SkillMessages.CRAFTING_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.YELLOW);
    setInterval(3789);
    setIcon(Material.CRAFTING_TABLE);
    registerAdaptation(new CraftingDeconstruction());
    registerAdaptation(new CraftingXP());
    registerAdaptation(new CraftingLeather());
    registerAdaptation(new CraftingSkulls());
    registerAdaptation(new CraftingBackpacks());
    registerAdaptation(new CraftingStations());
    registerAdaptation(new CraftingReconstruction());
    registerAdaptation(new CraftingBulkArtisan());
    registerAdaptation(new CraftingThriftyHands());
    registerAdaptation(new CraftingMasterwork());
    registerAdaptation(new CraftingCompactor());
    registerAdaptation(new CraftingTinkerer());
    registerAdaptation(new CraftingProvisioner());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CRAFTING_TABLE).key("challenge_craft_1k")
        .model(CustomModel.get(Material.CRAFTING_TABLE, "advancement", "crafting", "challenge_craft_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
            .icon(Material.CRAFTING_TABLE)
            .key("challenge_craft_5k")
            .model(CustomModel.get(Material.CRAFTING_TABLE, "advancement", "crafting", "challenge_craft_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA).child(AdaptAdvancement.builder()
                .icon(Material.CRAFTING_TABLE)
                .key("challenge_craft_50k")
                .model(CustomModel.get(Material.CRAFTING_TABLE, "advancement", "crafting", "challenge_craft_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.VANILLA)
                .build())
            .build())
        .build());
    registerMilestone("challenge_craft_1k", "crafted.items", 1000, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_5k", "crafted.items", 5000, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_50k", "crafted.items", 50000, () -> getConfig().challengeCraft1kReward);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLD_INGOT).key("challenge_craft_value_10k")
        .model(CustomModel.get(Material.GOLD_INGOT, "advancement", "crafting", "challenge_craft_value_10k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_craft_value_100k")
            .model(CustomModel.get(Material.DIAMOND, "advancement", "crafting", "challenge_craft_value_100k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_craft_value_10k", "crafted.value", 10000, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_value_100k", "crafted.value", 100000, () -> getConfig().challengeCraft1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE).key("challenge_craft_tools_25")
        .model(CustomModel.get(Material.IRON_PICKAXE, "advancement", "crafting", "challenge_craft_tools_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_PICKAXE)
            .key("challenge_craft_tools_250")
            .model(CustomModel.get(Material.DIAMOND_PICKAXE, "advancement", "crafting", "challenge_craft_tools_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_craft_tools_25", "crafting.tools", 25, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_tools_250", "crafting.tools", 250, () -> getConfig().challengeCraft1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE).key("challenge_craft_armor_25")
        .model(CustomModel.get(Material.IRON_CHESTPLATE, "advancement", "crafting", "challenge_craft_armor_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_CHESTPLATE)
            .key("challenge_craft_armor_250")
            .model(CustomModel.get(Material.DIAMOND_CHESTPLATE, "advancement", "crafting", "challenge_craft_armor_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_craft_armor_25", "crafting.armor", 25, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_armor_250", "crafting.armor", 250, () -> getConfig().challengeCraft1kReward * 2);

  }


  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p)) {
      return;
    }
    pendingCrafts.remove(p.getUniqueId());
    shouldReturnForPlayer(p, e, () -> {
      CraftingXpTransaction transaction = CraftingXpTransaction.open(e);
      if (transaction == null) {
        return;
      }
      pendingCrafts.put(p.getUniqueId(), transaction);
      J.runEntity(p, () -> completeCraft(p, transaction), 1);
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    CraftingXpTransaction transaction = pendingCrafts.get(e.getPlayer().getUniqueId());
    if (transaction != null) {
      transaction.accept(e.getCraftedItem());
    }
  }

  private void completeCraft(Player player, CraftingXpTransaction transaction) {
    pendingCrafts.remove(player.getUniqueId(), transaction);
    shouldReturnForPlayer(player, () -> rewardCraft(player, transaction));
  }

  private void rewardCraft(Player player, CraftingXpTransaction transaction) {
    int amount = transaction.committedItems();
    if (amount <= 0 || !cooldowns.isReady(player.getUniqueId(), getConfig().cooldownDelay)) {
      return;
    }
    cooldowns.mark(player.getUniqueId());
    double value = amount * getValue(transaction.material()) * getConfig().craftingValueXPMultiplier;
    addStat(player, "crafted.items", amount);
    addStat(player, "crafted.value", value);
    String type = transaction.material().name();
    if (type.endsWith("_PICKAXE") || type.endsWith("_AXE") || type.endsWith("_SHOVEL")
        || type.endsWith("_HOE") || type.endsWith("_SWORD")) {
      addStat(player, "crafting.tools", amount);
    }
    if (type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE")
        || type.endsWith("_LEGGINGS") || type.endsWith("_BOOTS")) {
      addStat(player, "crafting.armor", amount);
    }
    xp(player, value + getConfig().baseCraftingXP, transaction.rewardKey());
    float pitch = (float) Math.min(1.8D, 0.9D + Math.min(1.0D, value / 200.0D) * 0.9D);
    fx(player.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
        .particle(Particles.ENCHANTMENT_TABLE, 3, 0, 0.2D, 0, 0.3D, 0.3D)
        .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, pitch);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(FurnaceSmeltEvent e) {
    if (e.isCancelled() || shouldReturnForWorld(e.getBlock().getWorld(), this)) {
      return;
    }
    String furnaceKey = e.getBlock().getWorld().getUID() + ":" + e.getBlock().getX() + ":" + e.getBlock().getY() + ":" + e.getBlock().getZ();
    if (!markFurnaceXp(furnaceXpMarks, furnaceKey, System.currentTimeMillis(), getConfig().furnaceXpCooldown)) {
      return;
    }
    xp(e.getBlock().getLocation(), getConfig().furnaceBaseXP + (getValue(e.getResult()) * getConfig().furnaceValueXPMultiplier), getConfig().furnaceXPRadius, getConfig().furnaceXPDuration);
    Location furnace = e.getBlock().getLocation().add(0.5D, 0.9D, 0.5D);
    fx(furnace, FxPriority.AMBIENT)
        .particle(Particles.SMOKE, 1, 0, 0, 0, 0.04D, 0.02D)
        .particle(Particle.FLAME, 1, 0, 0, 0, 0.03D, 0.01D)
        .sound(Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.3F, 1.0F);
  }

  static boolean markFurnaceXp(Map<String, Long> marks, String key, long now, long cooldownMs) {
    if (cooldownMs <= 0) {
      return true;
    }
    if (marks.size() > 2048) {
      marks.values().removeIf((Long last) -> now - last >= cooldownMs);
    }
    Long previous = marks.putIfAbsent(key, now);
    if (previous == null) {
      return true;
    }
    if (now - previous < cooldownMs) {
      return false;
    }
    return marks.replace(key, previous, now);
  }

  @Override
  public void unregister() {
    furnaceXpMarks.clear();
    super.unregister();
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    String skillColor = "&e";
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Furnace Base XP for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double furnaceBaseXP = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Furnace Value XPMultiplier for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double furnaceValueXPMultiplier = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Furnace XPRadius for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int furnaceXPRadius = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Delay for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long cooldownDelay = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Furnace XPDuration for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long furnaceXPDuration = 10000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between spatial XP pulses per furnace.", impact = "Higher values reduce AFK furnace XP farming; lower values reward smelting more often.")
    long furnaceXpCooldown = 10000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crafting Value XPMultiplier for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double craftingValueXPMultiplier = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Crafting XP for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCraftingXP = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Challenge Craft1k Reward for the Crafting skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double challengeCraft1kReward = 1200;
  }
}
