package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Fisher's Fantasy's bonus catch: one extra fishing drop plus vanilla XP at the angler. */
public final class SeaborneCatches {
  private static final List<Id> FISHING_DROPS = fishingDrops();

  private SeaborneCatches() {
  }

  public static void award(PlayerRef player, int vanillaXp) {
    Id drop = FISHING_DROPS.get(ThreadLocalRandom.current().nextInt(FISHING_DROPS.size()));
    ItemRef item = AdaptApi.platform().host().items().create(drop, 1);
    player.world().dropItemNaturally(player.location().position(), item);
    if (vanillaXp > 0) {
      player.world().spawnExperience(player.location().position(), vanillaXp);
    }
  }

  public static double bonusChance(int level, int maxLevel, double atLevelOne, double atMaxLevel) {
    if (maxLevel <= 1) {
      return Math.max(0D, Math.min(1D, atMaxLevel));
    }
    double progress = (double) Math.max(0, Math.min(level, maxLevel) - 1) / (double) (maxLevel - 1);
    double chance = atLevelOne + ((atMaxLevel - atLevelOne) * progress);
    return Math.max(0D, Math.min(1D, chance));
  }

  public static int rewardXp(int level, int atLevelOne, int perAdditionalLevel, int maximum) {
    long reward = Math.max(0, atLevelOne) + ((long) Math.max(0, level - 1) * Math.max(0, perAdditionalLevel));
    return (int) Math.min(Math.max(0, maximum), reward);
  }

  private static List<Id> fishingDrops() {
    List<Id> drops = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      drops.addAll(Collections.nCopies(6, Id.minecraft("cod")));
      drops.addAll(Collections.nCopies(4, Id.minecraft("salmon")));
      drops.add(Id.minecraft("pufferfish"));
      drops.add(Id.minecraft("tropical_fish"));
    }
    for (String path : List.of("bow", "fishing_rod", "name_tag", "nautilus_shell", "saddle",
        "lily_pad", "bowl", "leather", "stick", "rotten_flesh", "string", "glass_bottle", "bone", "ink_sac", "tripwire_hook")) {
      drops.add(Id.minecraft(path));
    }
    return List.copyOf(drops);
  }
}
