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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class CraftingCompactor extends SimpleAdaptation<CraftingCompactor.Config> {
  private static final int COMPACTOR_LEVELS = 1;
  private static final int UNITS_PER_BLOCK = 9;
  private static final int FULL_STACK = 64;
  private static final CompactEntry[] ENTRIES = {
      new CompactEntry(Material.IRON_INGOT, Material.IRON_BLOCK),
      new CompactEntry(Material.GOLD_INGOT, Material.GOLD_BLOCK),
      new CompactEntry(Material.COAL, Material.COAL_BLOCK),
      new CompactEntry(Material.REDSTONE, Material.REDSTONE_BLOCK),
      new CompactEntry(Material.COPPER_INGOT, Material.COPPER_BLOCK),
      new CompactEntry(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK),
      new CompactEntry(Material.RAW_IRON, Material.RAW_IRON_BLOCK),
      new CompactEntry(Material.RAW_GOLD, Material.RAW_GOLD_BLOCK),
      new CompactEntry(Material.RAW_COPPER, Material.RAW_COPPER_BLOCK),
      new CompactEntry(Material.DIAMOND, Material.DIAMOND_BLOCK),
      new CompactEntry(Material.EMERALD, Material.EMERALD_BLOCK),
      new CompactEntry(Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK)
  };

  public CraftingCompactor() {
    super("crafting-compactor");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_BLOCK);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_BLOCK)
        .key("challenge_crafting_compactor_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_BLOCK)
            .key("challenge_crafting_compactor_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_crafting_compactor_1k", "crafting.compactor.blocks-compacted", 1000, 400);
    registerMilestone("challenge_crafting_compactor_10k", "crafting.compactor.blocks-compacted", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("crafting.compactor.lore1"));
    statLore(v, materialsCovered(), 2);
    v.addLore(C.YELLOW + "* " + C.GRAY + Localizer.dLocalize("crafting.compactor.lore3"));
  }

  static int materialsCovered() {
    return ENTRIES.length;
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void on(PlayerSwapHandItemsEvent e) {
    Player p = e.getPlayer();
    normalizeStoredLevel(p);
    int level = getActiveLevel(p);
    Block targetBlock = p.getTargetBlockExact(5, FluidCollisionMode.NEVER);
    Material targetType = targetBlock == null ? Material.AIR : targetBlock.getType();
    boolean defaultInventoryView = p.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING;
    if (!isActivation(p.isSneaking(), level, defaultInventoryView, targetType)) {
      return;
    }

    e.setCancelled(true);
    compact(p);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    Player player = e.getPlayer();
    withPlayerThread(player, () -> normalizeStoredLevel(player));
  }

  private void compact(Player p) {
    int totalBlocks = 0;
    for (CompactEntry entry : ENTRIES) {
      int available = availablePlain(p, entry.unit());
      if (available < FULL_STACK) {
        continue;
      }
      int blocks = available / UNITS_PER_BLOCK;
      if (blocks <= 0) {
        continue;
      }
      p.getInventory().removeItem(new ItemStack(entry.unit(), blocks * UNITS_PER_BLOCK));
      Map<Integer, ItemStack> leftovers = p.getInventory().addItem(new ItemStack(entry.block(), blocks));
      for (ItemStack leftover : leftovers.values()) {
        if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
          p.getWorld().dropItemNaturally(p.getLocation(), leftover);
        }
      }
      totalBlocks += blocks;
    }

    if (totalBlocks > 0) {
      addStat(p, "crafting.compactor.blocks-compacted", totalBlocks);
      fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
          .particle(Particles.CRIT_MAGIC, Math.min(12, 3 + totalBlocks), 0, 0.2D, 0, 0.25D, 0.15D)
          .sound(Sound.BLOCK_STONE_PLACE, 0.4F, 1.2F);
    }
  }

  static boolean isActivation(boolean sneaking, int activeLevel, boolean defaultInventoryView, Material targetType) {
    return sneaking
        && activeLevel > 0
        && defaultInventoryView
        && targetType == Material.CRAFTING_TABLE;
  }

  boolean normalizeStoredLevel(PlayerSkillLine line) {
    if (line == null || line.getAdaptationLevel(getName()) <= COMPACTOR_LEVELS) {
      return false;
    }
    line.setAdaptation(this, COMPACTOR_LEVELS);
    return true;
  }

  private int availablePlain(Player p, Material material) {
    ItemStack unit = new ItemStack(material);
    int total = 0;
    for (ItemStack slot : p.getInventory().getStorageContents()) {
      if (slot != null && slot.isSimilar(unit)) {
        total += slot.getAmount();
      }
    }
    return total;
  }

  private void normalizeStoredLevel(Player player) {
    AdaptPlayer adaptPlayer = getPlayer(player);
    PlayerSkillLine line = adaptPlayer.getData().getSkillLineNullable(getSkill().getName());
    normalizeStoredLevel(line);
  }

  private record CompactEntry(Material unit, Material block) {
  }

  @ConfigDescription("Sneak and swap hands while aiming at a Crafting Table to compact full stacks of ingots, gems, and raw ores into blocks.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.3;
      initialCost = 4;
      normalizeForPersistence();
    }

    void normalizeForPersistence() {
      maxLevel = COMPACTOR_LEVELS;
    }
  }
}
