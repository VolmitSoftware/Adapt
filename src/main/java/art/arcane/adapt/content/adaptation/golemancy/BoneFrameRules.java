package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemScale;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.api.minion.ServantOrder;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

final class BoneFrameRules {
  static final int MAX_LATTICE_SLOTS = 7;
  private static final double SCALE_EPSILON = 1.0e-9D;

  private BoneFrameRules() {
  }

  static int latticeSlots(List<Integer> slotsByLevel, int level) {
    return Math.clamp(AnimusBindingRules.atLevel(slotsByLevel, level, 0), 0, MAX_LATTICE_SLOTS);
  }

  static int mostSlots(List<Integer> slotsByLevel, int maxLevel) {
    int most = 0;
    for (int level = 1; level <= maxLevel; level++) {
      most = Math.max(most, latticeSlots(slotsByLevel, level));
    }
    return most;
  }

  static int slotUnlockLevel(List<Integer> slotsByLevel, int maxLevel, int index) {
    for (int level = 1; level <= maxLevel; level++) {
      if (latticeSlots(slotsByLevel, level) > index) {
        return level;
      }
    }
    return 0;
  }

  static double share(List<Double> shareByLevel, int level) {
    double share = AnimusBindingRules.atLevel(shareByLevel, level, 0D);
    return Double.isFinite(share) ? Math.max(0D, share) : 0D;
  }

  static Set<GolemRuneType> aspects(List<String> ids, Consumer<String> warn) {
    Set<GolemRuneType> aspects = EnumSet.noneOf(GolemRuneType.class);
    for (String id : ids) {
      String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
      Optional<GolemRuneType> aspect = GolemRuneType.fromId(normalized);
      if (aspect.isEmpty()) {
        warn.accept("Bone Frame latticeAspects lists unknown rune aspect '" + id + "'; it is ignored.");
        continue;
      }
      aspects.add(aspect.get());
    }
    return aspects;
  }

  static int effectiveTier(int runeTier, int maxTier) {
    return Math.max(0, Math.min(runeTier, maxTier));
  }

  static Map<GolemRuneType, Integer> active(List<GolemRune> lattice, int slots, Set<GolemRuneType> allowed, int maxTier) {
    Map<GolemRuneType, Integer> active = new EnumMap<>(GolemRuneType.class);
    int usable = Math.min(slots, lattice.size());
    for (int index = 0; index < usable; index++) {
      GolemRune rune = lattice.get(index);
      int tier = effectiveTier(rune.tier(), maxTier);
      if (tier > 0 && allowed.contains(rune.aspect())) {
        active.putIfAbsent(rune.aspect(), tier);
      }
    }
    return active;
  }

  static Optional<Refusal> refuse(List<GolemRune> lattice, GolemRune rune, int slots, Set<GolemRuneType> allowed) {
    if (!allowed.contains(rune.aspect())) {
      return Optional.of(Refusal.ASPECT);
    }
    for (GolemRune slotted : lattice) {
      if (slotted.aspect() == rune.aspect()) {
        return Optional.of(Refusal.DUPLICATE);
      }
    }
    return lattice.size() >= slots ? Optional.of(Refusal.NO_SLOT) : Optional.empty();
  }

  static OptionalDouble scaleAmount(int level, double scale) {
    if (level < 1 || !Double.isFinite(scale)) {
      return OptionalDouble.empty();
    }
    double amount = GolemScale.modifierAmount(scale);
    return Math.abs(amount) > SCALE_EPSILON ? OptionalDouble.of(amount) : OptionalDouble.empty();
  }

  static boolean obeys(GolemSignal signal) {
    return switch (signal) {
      case FOLLOW, HOLD, SIC -> true;
      case HOME, WORK -> false;
    };
  }

  static Optional<ServantOrder> answer(ServantOrder current, GolemSignal signal, Location at, LivingEntity target, long untilMillis) {
    return switch (signal) {
      case FOLLOW -> Optional.of(ServantOrder.follow());
      case HOLD -> Optional.of(ServantOrder.hold(at));
      case SIC -> target == null ? Optional.empty() : Optional.of(current.command(target, untilMillis));
      case HOME, WORK -> Optional.empty();
    };
  }

  enum Refusal {
    ASPECT,
    DUPLICATE,
    NO_SLOT
  }
}
