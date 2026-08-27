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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class CraftingCompactor extends SimpleAdaptation<CraftingCompactor.Config> {
  private static final int COMPACTOR_LEVELS = 1;
  private static final int FULL_STACK = 64;
  private static final CompactEntry[] ENTRIES = {
      new CompactEntry(Material.IRON_INGOT, Material.IRON_BLOCK, 9),
      new CompactEntry(Material.GOLD_INGOT, Material.GOLD_BLOCK, 9),
      new CompactEntry(Material.COAL, Material.COAL_BLOCK, 9),
      new CompactEntry(Material.GLOWSTONE_DUST, Material.GLOWSTONE, 4),
      new CompactEntry(Material.REDSTONE, Material.REDSTONE_BLOCK, 9),
      new CompactEntry(Material.COPPER_INGOT, Material.COPPER_BLOCK, 9),
      new CompactEntry(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK, 9),
      new CompactEntry(Material.RAW_IRON, Material.RAW_IRON_BLOCK, 9),
      new CompactEntry(Material.RAW_GOLD, Material.RAW_GOLD_BLOCK, 9),
      new CompactEntry(Material.RAW_COPPER, Material.RAW_COPPER_BLOCK, 9),
      new CompactEntry(Material.DIAMOND, Material.DIAMOND_BLOCK, 9),
      new CompactEntry(Material.EMERALD, Material.EMERALD_BLOCK, 9),
      new CompactEntry(Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, 9)
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
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.COMPACTOR_LORE1));
    statLore(v, materialsCovered(), 2);
    v.addLore(C.YELLOW + "* " + C.GRAY + AdaptLanguage.text(CraftingMessages.COMPACTOR_LORE3));
  }

  static int materialsCovered() {
    return ENTRIES.length;
  }

  static int unitsPerBlock(Material unit) {
    for (CompactEntry entry : ENTRIES) {
      if (entry.unit() == unit) {
        return entry.unitsPerBlock();
      }
    }
    return 0;
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.NORMAL)
  public void on(PlayerSwapHandItemsEvent e) {
    Player p = e.getPlayer();
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

  private void compact(Player p) {
    int totalBlocks = 0;
    for (CompactEntry entry : ENTRIES) {
      int available = availablePlain(p, entry.unit());
      if (available < FULL_STACK) {
        continue;
      }
      int blocks = blocksFor(available, entry.unitsPerBlock());
      if (blocks <= 0) {
        continue;
      }
      int consumed = unitsConsumed(blocks, entry.unitsPerBlock());
      if (!payItemCost(p, "materials", new ItemStack(entry.unit()), consumed,
          () -> removePlain(p, entry.unit(), consumed))) {
        continue;
      }

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

  static int blocksFor(int available, int unitsPerBlock) {
    return available <= 0 || unitsPerBlock <= 0 ? 0 : available / unitsPerBlock;
  }

  static int unitsConsumed(int blocks, int unitsPerBlock) {
    return blocks <= 0 || unitsPerBlock <= 0 ? 0 : blocks * unitsPerBlock;
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

  private boolean removePlain(Player player, Material material, int amount) {
    ItemStack unit = new ItemStack(material);
    if (amount <= 0 || availablePlain(player, material) < amount) {
      return false;
    }

    int remaining = amount;
    ItemStack[] storage = player.getInventory().getStorageContents();
    for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
      ItemStack item = storage[slot];
      if (item == null || !item.isSimilar(unit)) {
        continue;
      }

      int consumed = Math.min(remaining, item.getAmount());
      remaining -= consumed;
      if (consumed == item.getAmount()) {
        storage[slot] = null;
      } else {
        item.setAmount(item.getAmount() - consumed);
      }
    }
    player.getInventory().setStorageContents(storage);
    return remaining == 0;
  }

  private record CompactEntry(Material unit, Material block, int unitsPerBlock) {
  }

  @ConfigDescription("Sneak and swap hands while aiming at a Crafting Table to compact full stacks of ingots, gems, and raw ores into blocks.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.3;
      maxLevel = COMPACTOR_LEVELS;
      initialCost = 4;
    }
  }
}
