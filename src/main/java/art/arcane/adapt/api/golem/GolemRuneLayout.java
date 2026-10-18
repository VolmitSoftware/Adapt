package art.arcane.adapt.api.golem;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record GolemRuneLayout(int unlockedSlots, List<Integer> slotUnlockLevels, int maxTier, List<Integer> tierUnlockLevels,
                              Set<GolemRuneType> blocked) {
  public static final int MAX_SLOTS = 9;
  public static final GolemRuneLayout NONE = new GolemRuneLayout(0, List.of(), 0, List.of(), Set.of());

  public GolemRuneLayout {
    slotUnlockLevels = List.copyOf(slotUnlockLevels);
    tierUnlockLevels = List.copyOf(tierUnlockLevels);
    blocked = blocked.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(blocked));
    unlockedSlots = Math.clamp(unlockedSlots, 0, slotUnlockLevels.size());
    maxTier = Math.clamp(maxTier, 0, GolemRuneType.MAX_TIER);
  }

  public int maxSlots() {
    return slotUnlockLevels.size();
  }

  public int slotUnlockLevel(int index) {
    return index >= 0 && index < slotUnlockLevels.size() ? slotUnlockLevels.get(index) : 0;
  }

  public int tierUnlockLevel(int tier) {
    return GolemRuneType.isTier(tier) && tier <= tierUnlockLevels.size() ? tierUnlockLevels.get(tier - 1) : 0;
  }

  public boolean blocks(GolemRuneType aspect) {
    return blocked.contains(aspect);
  }

  public Status status(List<GolemRune> runes, int index) {
    GolemRune rune = runes.get(index);
    if (index >= unlockedSlots) {
      return Status.LOCKED_SLOT;
    }
    if (blocks(rune.aspect())) {
      return Status.BLOCKED;
    }
    for (int earlier = 0; earlier < index; earlier++) {
      if (runes.get(earlier).aspect() == rune.aspect()) {
        return Status.DUPLICATE;
      }
    }
    return rune.tier() > maxTier ? Status.OVER_TIER : Status.ACTIVE;
  }

  public Map<GolemRuneType, Integer> active(List<GolemRune> runes) {
    Map<GolemRuneType, Integer> active = new EnumMap<>(GolemRuneType.class);
    for (int index = 0; index < runes.size(); index++) {
      if (status(runes, index) == Status.ACTIVE) {
        GolemRune rune = runes.get(index);
        active.put(rune.aspect(), rune.tier());
      }
    }
    return active;
  }

  public Optional<Refusal> refuse(List<GolemRune> runes, GolemRuneType aspect) {
    if (blocks(aspect)) {
      return Optional.of(Refusal.BLOCKED);
    }
    for (GolemRune rune : runes) {
      if (rune.aspect() == aspect) {
        return Optional.of(Refusal.DUPLICATE);
      }
    }
    return runes.size() >= unlockedSlots ? Optional.of(Refusal.NO_SLOT) : Optional.empty();
  }

  public enum Status {
    ACTIVE,
    LOCKED_SLOT,
    BLOCKED,
    DUPLICATE,
    OVER_TIER
  }

  public enum Refusal {
    NO_SLOT,
    DUPLICATE,
    BLOCKED
  }
}
