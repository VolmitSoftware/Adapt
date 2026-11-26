package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/**
 * Compacts full stacks of ingots, gems and raw ores in a player's inventory into blocks, honouring the owner's
 * materials, batch and reserve preferences.
 */
public final class Compaction {
  private static final int FULL_STACK = 64;
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
  private final Adaptation<?> owner;

  public Compaction(Adaptation<?> owner) {
    this.owner = owner;
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

  static int blocksFor(int available, int unitsPerBlock) {
    return available <= 0 || unitsPerBlock <= 0 ? 0 : available / unitsPerBlock;
  }

  static int unitsConsumed(int blocks, int unitsPerBlock) {
    return blocks <= 0 || unitsPerBlock <= 0 ? 0 : blocks * unitsPerBlock;
  }

  /** Compacts what the player carries and returns the blocks made. */
  public int compact(PlayerRef p) {
    int totalBlocks = 0;
    for (CompactEntry entry : ENTRIES) {
      if (!owner.preference(p, CraftingCompactor.MATERIALS).allows(entry.unit())) {
        continue;
      }
      int available = Math.max(0, availablePlain(p, entry.unit()) - owner.preference(p, CraftingCompactor.RESERVE).amount());
      if (available < FULL_STACK) {
        continue;
      }
      int blocks = (int) Math.floor(blocksFor(available, entry.unitsPerBlock()) * owner.preference(p, CraftingCompactor.BATCH).multiplier());
      if (blocks <= 0) {
        continue;
      }
      int consumed = unitsConsumed(blocks, entry.unitsPerBlock());
      if (!owner.payItemCost(p, "materials", items().create(entry.unit(), 1), consumed,
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

    return totalBlocks;
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
}
