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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;

public class CraftingCompactor extends SimpleAdaptation<CraftingCompactor.Config> {
  public static final PlayerPreference<CraftingPreferences.Category> MATERIALS = CraftingPreferences.categories("materials", CraftingMessages.PREFERENCE_CRAFTINGCOMPACTOR_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Scale> BATCH = CommonPreferences.scale("batch-limit", CraftingMessages.PREFERENCE_CRAFTINGCOMPACTOR_BATCH);
  public static final PlayerPreference<CraftingPreferences.Reserve> RESERVE = CraftingPreferences.reserve("loose-reserve", CraftingMessages.PREFERENCE_CRAFTINGCOMPACTOR_RESERVE);

  private static final int COMPACTOR_LEVELS = 1;
  private static final int FULL_STACK = 64;
  private static final Id AIR = Id.minecraft("air");
  private static final Id CRAFTING_TABLE = Id.minecraft("crafting_table");
  private static final CompactEntry[] ENTRIES = {
      new CompactEntry(Id.minecraft("iron_ingot"), Id.minecraft("iron_block"), 9),
      new CompactEntry(Id.minecraft("gold_ingot"), Id.minecraft("gold_block"), 9),
      new CompactEntry(Id.minecraft("coal"), Id.minecraft("coal_block"), 9),
      new CompactEntry(Id.minecraft("glowstone_dust"), Id.minecraft("glowstone"), 4),
      new CompactEntry(Id.minecraft("redstone"), Id.minecraft("redstone_block"), 9),
      new CompactEntry(Id.minecraft("copper_ingot"), Id.minecraft("copper_block"), 9),
      new CompactEntry(Id.minecraft("lapis_lazuli"), Id.minecraft("lapis_block"), 9),
      new CompactEntry(Id.minecraft("raw_iron"), Id.minecraft("raw_iron_block"), 9),
      new CompactEntry(Id.minecraft("raw_gold"), Id.minecraft("raw_gold_block"), 9),
      new CompactEntry(Id.minecraft("raw_copper"), Id.minecraft("raw_copper_block"), 9),
      new CompactEntry(Id.minecraft("diamond"), Id.minecraft("diamond_block"), 9),
      new CompactEntry(Id.minecraft("emerald"), Id.minecraft("emerald_block"), 9),
      new CompactEntry(Id.minecraft("netherite_ingot"), Id.minecraft("netherite_block"), 9)
  };

  public CraftingCompactor() {
    super("crafting-compactor");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_block"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_block"))
        .key("challenge_crafting_compactor_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_block"))
            .key("challenge_crafting_compactor_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_crafting_compactor_1k", "crafting.compactor.blocks-compacted", 1000, 400);
    registerMilestone("challenge_crafting_compactor_10k", "crafting.compactor.blocks-compacted", 10000, 1500);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS, BATCH, RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.COMPACTOR_LORE1));
    statLore(v, materialsCovered(), 2);
    v.addLore(C.YELLOW + "* " + C.GRAY + AdaptLanguage.text(CraftingMessages.COMPACTOR_LORE3));
  }

  static int materialsCovered() {
    return ENTRIES.length;
  }

  static int unitsPerBlock(Id unit) {
    for (CompactEntry entry : ENTRIES) {
      if (entry.unit().equals(unit)) {
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
  @Handle(priority = EventPriority.NORMAL)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    BlockRef targetBlock = p.targetBlock(5);
    Id targetType = targetBlock == null ? AIR : targetBlock.type();
    boolean defaultInventoryView = !p.hasOpenContainer();
    if (!isActivation(p.isSneaking(), level, defaultInventoryView, targetType)) {
      return;
    }

    e.setCancelled(true);
    compact(p);
  }

  private void compact(PlayerRef p) {
    int totalBlocks = 0;
    for (CompactEntry entry : ENTRIES) {
      if (!preference(p, MATERIALS).allows(entry.unit())) {
        continue;
      }
      int available = Math.max(0, availablePlain(p, entry.unit()) - preference(p, RESERVE).amount());
      if (available < FULL_STACK) {
        continue;
      }
      int blocks = (int) Math.floor(blocksFor(available, entry.unitsPerBlock()) * preference(p, BATCH).multiplier());
      if (blocks <= 0) {
        continue;
      }
      int consumed = unitsConsumed(blocks, entry.unitsPerBlock());
      if (!payItemCost(p, "materials", items().create(entry.unit(), 1), consumed,
          () -> removePlain(p, entry.unit(), consumed))) {
        continue;
      }

      List<ItemRef> leftovers = p.inventory().addItem(items().create(entry.block(), blocks));
      for (ItemRef leftover : leftovers) {
        if (leftover != null && !leftover.isEmpty() && !leftover.type().isAir() && leftover.amount() > 0) {
          p.world().dropItemNaturally(p.location().position(), leftover);
        }
      }
      totalBlocks += blocks;
    }

    if (totalBlocks > 0) {
      addStat(p, "crafting.compactor.blocks-compacted", totalBlocks);
      fx(p.location().add(0, 1, 0), FxPriority.AMBIENT)
          .particle(Particles.CRIT_MAGIC, Math.min(12, 3 + totalBlocks), 0, 0.2D, 0, 0.25D, 0.15D)
          .sound(Sounds.BLOCK_STONE_PLACE, 0.4F, 1.2F);
    }
  }

  static boolean isActivation(boolean sneaking, int activeLevel, boolean defaultInventoryView, Id targetType) {
    return sneaking
        && activeLevel > 0
        && defaultInventoryView
        && CRAFTING_TABLE.equals(targetType);
  }

  static int blocksFor(int available, int unitsPerBlock) {
    return available <= 0 || unitsPerBlock <= 0 ? 0 : available / unitsPerBlock;
  }

  static int unitsConsumed(int blocks, int unitsPerBlock) {
    return blocks <= 0 || unitsPerBlock <= 0 ? 0 : blocks * unitsPerBlock;
  }

  private int availablePlain(PlayerRef p, Id material) {
    ItemRef unit = items().create(material, 1);
    int total = 0;
    for (ItemRef slot : CraftingBulkArtisan.storageContents(p.inventory())) {
      if (slot != null && slot.isSimilar(unit)) {
        total += slot.amount();
      }
    }
    return total;
  }

  private boolean removePlain(PlayerRef player, Id material, int amount) {
    ItemRef unit = items().create(material, 1);
    if (amount <= 0 || availablePlain(player, material) < amount) {
      return false;
    }

    int remaining = amount;
    PlayerInventoryRef inventory = player.inventory();
    ItemRef[] storage = CraftingBulkArtisan.storageContents(inventory);
    for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
      ItemRef item = storage[slot];
      if (item == null || !item.isSimilar(unit)) {
        continue;
      }

      int consumed = Math.min(remaining, item.amount());
      remaining -= consumed;
      if (consumed == item.amount()) {
        inventory.clear(slot);
      } else {
        ItemRef kept = item.copy();
        kept.setAmount(item.amount() - consumed);
        inventory.setItem(slot, kept);
      }
    }
    return remaining == 0;
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  private record CompactEntry(Id unit, Id block, int unitsPerBlock) {
  }

  @ConfigDescription("Sneak and swap hands while aiming at a Crafting Table to compact full stacks of ingots, gems, and raw ores into blocks.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = COMPACTOR_LEVELS;
      initialCost = 1;
    }
  }
}
