package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRuneType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

final class RunicAugmentsRules {

  private RunicAugmentsRules() {
  }

  static int extraSlots(List<Integer> extraSlotsByLevel, int level) {
    return Math.max(0, AnimusBindingRules.atLevel(extraSlotsByLevel, level, 0));
  }

  static int maxTier(List<Integer> maxTierByLevel, int level) {
    return Math.clamp(AnimusBindingRules.atLevel(maxTierByLevel, level, 0), 0, GolemRuneType.MAX_TIER);
  }

  static <T> T atTier(List<T> valuesByTier, int tier, T fallback) {
    return AnimusBindingRules.atLevel(valuesByTier, tier, fallback);
  }

  static GolemRuneLayout layout(int baseSlots, List<Integer> extraSlotsByLevel, List<Integer> maxTierByLevel, int maxLevel,
                                Set<GolemRuneType> blocked, int level, int masterworkSlots) {
    int base = Math.min(GolemRuneLayout.MAX_SLOTS, Math.clamp(baseSlots, 0, GolemRuneLayout.MAX_SLOTS)
        + Math.clamp(masterworkSlots, 0, GolemRuneLayout.MAX_SLOTS));
    int mostExtra = 0;
    for (int candidate = 1; candidate <= maxLevel; candidate++) {
      mostExtra = Math.max(mostExtra, extraSlots(extraSlotsByLevel, candidate));
    }
    int maxSlots = Math.min(GolemRuneLayout.MAX_SLOTS, base + mostExtra);
    List<Integer> slotLevels = new ArrayList<>(maxSlots);
    for (int index = 0; index < maxSlots; index++) {
      slotLevels.add(slotUnlockLevel(base, extraSlotsByLevel, maxLevel, index));
    }
    List<Integer> tierLevels = new ArrayList<>(GolemRuneType.MAX_TIER);
    for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
      tierLevels.add(tierUnlockLevel(maxTierByLevel, maxLevel, tier));
    }
    int unlocked = level < 1 ? 0 : Math.min(maxSlots, base + extraSlots(extraSlotsByLevel, level));
    return new GolemRuneLayout(unlocked, slotLevels, maxTier(maxTierByLevel, level), tierLevels, blocked);
  }

  static Map<GolemBody, Set<GolemRuneType>> bodyBlocks(List<String> specs, Consumer<String> warn) {
    Map<GolemBody, Set<GolemRuneType>> blocks = new EnumMap<>(GolemBody.class);
    if (specs == null) {
      return blocks;
    }
    for (String spec : specs) {
      Optional<Block> block = block(spec);
      if (block.isEmpty()) {
        warn.accept("Runic Augments ignores runeBodyBlocks entry '" + spec + "'; use aspect:body, such as ember:frost.");
        continue;
      }
      blocks.computeIfAbsent(block.get().body(), ignored -> EnumSet.noneOf(GolemRuneType.class)).add(block.get().aspect());
    }
    return blocks;
  }

  private static int slotUnlockLevel(int base, List<Integer> extraSlotsByLevel, int maxLevel, int index) {
    for (int level = 1; level <= maxLevel; level++) {
      if (Math.min(GolemRuneLayout.MAX_SLOTS, base + extraSlots(extraSlotsByLevel, level)) > index) {
        return level;
      }
    }
    return 0;
  }

  private static int tierUnlockLevel(List<Integer> maxTierByLevel, int maxLevel, int tier) {
    for (int level = 1; level <= maxLevel; level++) {
      if (maxTier(maxTierByLevel, level) >= tier) {
        return level;
      }
    }
    return 0;
  }

  private static Optional<Block> block(String spec) {
    if (spec == null) {
      return Optional.empty();
    }
    String[] parts = spec.split(":", -1);
    if (parts.length != 2) {
      return Optional.empty();
    }
    Optional<GolemRuneType> aspect = GolemRuneType.fromId(parts[0].trim().toLowerCase(Locale.ROOT));
    Optional<GolemBody> body = GolemBody.fromId(parts[1].trim().toLowerCase(Locale.ROOT));
    if (aspect.isEmpty() || body.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Block(aspect.get(), body.get()));
  }

  private record Block(GolemRuneType aspect, GolemBody body) {
  }
}
