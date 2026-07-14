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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.content.adaptation.crafting.CraftingBackpacks;
import art.arcane.adapt.content.adaptation.crafting.CraftingBulkArtisan;
import art.arcane.adapt.content.adaptation.crafting.CraftingCompactor;
import art.arcane.adapt.content.adaptation.crafting.CraftingDeconstruction;
import art.arcane.adapt.content.adaptation.crafting.CraftingLeather;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.content.adaptation.crafting.CraftingProvisioner;
import art.arcane.adapt.content.adaptation.crafting.CraftingReconstruction;
import art.arcane.adapt.content.adaptation.crafting.CraftingSignature;
import art.arcane.adapt.content.adaptation.crafting.CraftingSkulls;
import art.arcane.adapt.content.adaptation.crafting.CraftingStations;
import art.arcane.adapt.content.adaptation.crafting.CraftingThriftyHands;
import art.arcane.adapt.content.adaptation.crafting.CraftingTinkerer;
import art.arcane.adapt.content.adaptation.crafting.CraftingXP;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.reflect.registries.Particles;
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
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkillCrafting extends SimpleSkill<SkillCrafting.Config> {
  private final Cooldowns cooldowns = cooldowns();
  private final Map<String, Long> furnaceXpMarks = new ConcurrentHashMap<>();

  public SkillCrafting() {
    super("crafting", Localizer.dLocalize("skill.crafting.icon"));
    registerConfiguration(Config.class);
    setColor(C.YELLOW);
    setDescription(Localizer.dLocalize("skill.crafting.description"));
    setDisplayName(Localizer.dLocalize("skill.crafting.name"));
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
    registerAdaptation(new CraftingSignature());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CRAFTING_TABLE).key("challenge_craft_1k")
        .model(CustomModel.get(Material.CRAFTING_TABLE, "advancement", "crafting", "challenge_craft_1k"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
            .icon(Material.CRAFTING_TABLE)
            .key("challenge_craft_5k")
            .model(CustomModel.get(Material.CRAFTING_TABLE, "advancement", "crafting", "challenge_craft_5k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED).child(AdaptAdvancement.builder()
                .icon(Material.CRAFTING_TABLE)
                .key("challenge_craft_50k")
                .model(CustomModel.get(Material.CRAFTING_TABLE, "advancement", "crafting", "challenge_craft_50k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
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
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_craft_value_100k")
            .model(CustomModel.get(Material.DIAMOND, "advancement", "crafting", "challenge_craft_value_100k"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_craft_value_10k", "crafted.value", 10000, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_value_100k", "crafted.value", 100000, () -> getConfig().challengeCraft1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE).key("challenge_craft_tools_25")
        .model(CustomModel.get(Material.IRON_PICKAXE, "advancement", "crafting", "challenge_craft_tools_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_PICKAXE)
            .key("challenge_craft_tools_250")
            .model(CustomModel.get(Material.DIAMOND_PICKAXE, "advancement", "crafting", "challenge_craft_tools_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_craft_tools_25", "crafting.tools", 25, () -> getConfig().challengeCraft1kReward);
    registerMilestone("challenge_craft_tools_250", "crafting.tools", 250, () -> getConfig().challengeCraft1kReward * 2);

    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE).key("challenge_craft_armor_25")
        .model(CustomModel.get(Material.IRON_CHESTPLATE, "advancement", "crafting", "challenge_craft_armor_25"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_CHESTPLATE)
            .key("challenge_craft_armor_250")
            .model(CustomModel.get(Material.DIAMOND_CHESTPLATE, "advancement", "crafting", "challenge_craft_armor_250"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
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
    shouldReturnForPlayer(p, e, () -> {
      if (!isValidCraftEvent(e)) {
        return;
      }
      int recipeAmount = calculateRecipeAmount(e);
      if (recipeAmount > 0) {
        double v = recipeAmount * getValue(e.getRecipe().getResult()) * getConfig().craftingValueXPMultiplier;
        addStat(p, "crafted.items", recipeAmount);
        addStat(p, "crafted.value", v);
        Material resultType = e.getRecipe().getResult().getType();
        String typeName = resultType.name();
        if (typeName.contains("_PICKAXE") || typeName.contains("_AXE") || typeName.contains("_SHOVEL") || typeName.contains("_HOE") || typeName.contains("_SWORD")) {
          addStat(p, "crafting.tools", recipeAmount);
        }
        if (typeName.contains("_HELMET") || typeName.contains("_CHESTPLATE") || typeName.contains("_LEGGINGS") || typeName.contains("_BOOTS")) {
          addStat(p, "crafting.armor", recipeAmount);
        }
        xp(p, v + getConfig().baseCraftingXP);
        float chimePitch = (float) Math.min(1.8D, 0.9D + (Math.min(1.0D, v / 200.0D) * 0.9D));
        fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
            .particle(Particles.ENCHANTMENT_TABLE, 3, 0, 0.2D, 0, 0.3D, 0.3D)
            .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, chimePitch);
      }
    });
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(FurnaceSmeltEvent e) {
    if (shouldReturnForWorld(e.getBlock().getWorld(), this)) {
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

  private boolean isValidCraftEvent(CraftItemEvent e) {
    Player p = (Player) e.getWhoClicked();
    ItemStack result = e.getInventory().getResult();
    ItemStack cursor = e.getCursor();
    if (result == null || result.getAmount() <= 0 || (cursor != null && cursor.getAmount() >= cursor.getMaxStackSize())) {
      return false;
    }

    if (!cooldowns.isReady(p.getUniqueId(), getConfig().cooldownDelay)) {
      return false;
    }
    cooldowns.mark(p.getUniqueId());
    return true;
  }

  private int calculateRecipeAmount(CraftItemEvent e) {
    int recipeAmount = e.getInventory().getResult().getAmount();
    switch (e.getClick()) {
      case NUMBER_KEY -> {
        if (e.getWhoClicked().getInventory().getItem(e.getHotbarButton()) != null) {
          recipeAmount = 0;
        }
      }
      case DROP, CONTROL_DROP -> {
        ItemStack cursor = e.getCursor();
        if (!(cursor == null || cursor.getType().isAir())) {
          recipeAmount = 0;
        }
      }
      case SHIFT_RIGHT, SHIFT_LEFT -> {
        if (recipeAmount == 0) {
          break;
        }
        int maxCraftable = getMaxCraftAmount(e.getInventory());
        int capacity = fits(e.getRecipe().getResult(), e.getView().getBottomInventory());
        if (capacity < maxCraftable) {
          maxCraftable = ((capacity + recipeAmount - 1) / recipeAmount) * recipeAmount;
        }
        recipeAmount = maxCraftable;
      }
      default -> {
      }
    }
    return recipeAmount;
  }

  private int fits(ItemStack stack, Inventory inv) {
    ItemStack[] contents = inv.getContents();
    int maxStackSize = stack.getMaxStackSize();
    int result = 0;

    for (ItemStack is : contents) {
      if (is == null) {
        result += maxStackSize;
      } else if (is.isSimilar(stack)) {
        result += Math.max(maxStackSize - is.getAmount(), 0);
      }
    }

    return result;
  }

  private int getMaxCraftAmount(CraftingInventory inv) {
    if (inv.getResult() == null) {
      return 0;
    }

    int resultCount = inv.getResult().getAmount();
    int materialCount = Integer.MAX_VALUE;

    for (ItemStack is : inv.getMatrix()) {
      if (is != null && is.getAmount() < materialCount) {
        materialCount = is.getAmount();
      }
    }

    return resultCount * materialCount;
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
  protected static class Config {
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
