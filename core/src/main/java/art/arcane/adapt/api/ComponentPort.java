package art.arcane.adapt.api;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

/**
 * Player and item operations {@link Component} needs that the SPI does not cover or that a platform answers
 * through third-party plugins (MagicCosmetics, pickup protection probes, Paper durability). The neutral
 * fallbacks work on SPI types alone.
 */
public interface ComponentPort {
  ComponentPort NONE = new ComponentPort() {
  };

  /** True when a cosmetic plugin hides the armour piece in {@code slot}, so it counts as unarmoured. */
  default boolean cosmeticCovers(PlayerRef player, EquipmentSlot slot) {
    return false;
  }

  /** Damages the item in {@code slot} like vanilla tool use (Unbreaking, item damage event, break). */
  default void damageItem(PlayerRef player, EquipmentSlot slot, int amount) {
    if (player == null || slot == null || amount <= 0) {
      return;
    }
    ItemRef item = player.equipment(slot);
    if (item == null || item.isEmpty() || item.isUnbreakable() || item.maxDamage() <= 0) {
      return;
    }
    int next = item.damage() + amount;
    if (next >= item.maxDamage()) {
      player.setEquipment(slot, null);
      return;
    }
    item.setDamage(next);
    player.setEquipment(slot, item);
  }

  /** True when another plugin marks the item entity as not collectable (shop displays, holograms, NPC loot). */
  default boolean isPickupLocked(ItemEntityRef item) {
    return false;
  }

  /** Runs the platform's pickup checks for {@code player} taking {@code stack} from {@code item}. */
  default boolean attemptItemPickup(PlayerRef player, ItemEntityRef item, ItemRef stack) {
    return true;
  }

  /** Gives vanilla experience points through the platform's experience-change hook; returns the points given. */
  default int awardExperience(PlayerRef player, int points) {
    if (player == null || points <= 0) {
      return 0;
    }
    player.giveExp(points);
    return points;
  }

  /** Overwrites the player's total experience counter (score), leaving level and progress alone. */
  default void setTotalExperience(PlayerRef player, int total) {
  }

  /**
   * Sets level and bar progress to what {@code total} experience points are worth, leaving the total experience
   * counter alone.
   */
  default void setExperiencePoints(PlayerRef player, int total) {
    int points = Math.max(0, total);
    int level = 0;
    int remaining = points;
    while (true) {
      int toNext = level >= 30 ? 9 * level - 158 : level >= 15 ? 5 * level - 38 : 2 * level + 7;
      if (remaining < toNext) {
        player.setLevel(level);
        player.setExpProgress((float) remaining / toNext);
        return;
      }
      remaining -= toNext;
      level++;
    }
  }

  /** Experience points the player's level and bar progress are worth, in constant time. */
  default int totalExperiencePoints(PlayerRef player) {
    long level = Math.max(0, player.level());
    long atLevel = level <= 16
        ? level * level + 6L * level
        : level <= 31 ? (5L * level * level - 81L * level + 720L) / 2L : (9L * level * level - 325L * level + 4440L) / 2L;
    long toNext = level >= 30 ? 9L * level - 158L : level >= 15 ? 5L * level - 38L : 2L * level + 7L;
    long progress = Math.round(toNext * (double) player.expProgress());
    return (int) Math.min(Integer.MAX_VALUE, atLevel + progress);
  }
}
