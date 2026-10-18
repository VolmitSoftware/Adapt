package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import org.bukkit.DyeColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class OverseerRules {
  static final double MIN_RADIUS = 1D;
  static final double MAX_RADIUS = 128D;
  private static final double MIN_SIC_RANGE = 1D;
  private static final double MAX_SIC_RANGE = 64D;
  private static final double DEFAULT_SIC_RANGE = 24D;
  private static final double MIN_RING_RADIUS = 1D;
  private static final double MAX_RING_RADIUS = 8D;
  private static final double DEFAULT_RING_RADIUS = 2.5D;
  private static final double MAX_ARMOR = 30D;
  private static final List<GolemSignal> MOVEMENT_CYCLE = List.of(GolemSignal.FOLLOW, GolemSignal.HOLD, GolemSignal.HOME, GolemSignal.WORK);
  private static final List<GolemSignal> FULL_CYCLE = List.of(GolemSignal.FOLLOW, GolemSignal.HOLD, GolemSignal.HOME, GolemSignal.WORK, GolemSignal.SIC);

  private OverseerRules() {
  }

  static boolean unlocked(int level, int unlockLevel) {
    return level >= Math.max(1, unlockLevel);
  }

  static double radius(List<Integer> radiusByLevel, int level) {
    Integer radius = AnimusBindingRules.atLevel(radiusByLevel, level, null);
    return radius == null ? 0D : Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
  }

  static GolemSignal current(GolemSignal stored, int level, int sicLevel) {
    if (stored == null || (stored == GolemSignal.SIC && !unlocked(level, sicLevel))) {
      return GolemSignal.FOLLOW;
    }
    return stored;
  }

  static GolemSignal next(GolemSignal stored, int level, int sicLevel) {
    List<GolemSignal> cycle = unlocked(level, sicLevel) ? FULL_CYCLE : MOVEMENT_CYCLE;
    int index = cycle.indexOf(current(stored, level, sicLevel));
    return cycle.get((index + 1) % cycle.size());
  }

  static DyeColor crookChannel(int level, int filterLevel, Optional<DyeColor> stored) {
    return unlocked(level, filterLevel) ? stored.orElse(null) : null;
  }

  static boolean combatCapable(GolemBody body, List<GolemCoreType> cores) {
    if (!body.fights() || cores.isEmpty()) {
      return false;
    }
    return cores.getFirst() == GolemCoreType.BLANK || cores.contains(GolemCoreType.GUARDIAN);
  }

  static Answer answer(Hearing hearing) {
    if (!hearing.heard() || !hearing.sameWorld()) {
      return Answer.SKIP;
    }
    Optional<GolemMode> mode = hearing.signal().mode();
    if (mode.isEmpty()) {
      return hearing.inRange() && combatCapable(hearing.body(), hearing.cores()) ? Answer.COMMAND : Answer.SKIP;
    }
    if (!GolemMode.selectableFor(hearing.core()).contains(mode.get())) {
      return Answer.SKIP;
    }
    if (hearing.inRange()) {
      return Answer.MODE;
    }
    return hearing.signal() == GolemSignal.FOLLOW && hearing.rallies() ? Answer.RALLY : Answer.SKIP;
  }

  static Offset slot(int index, int count, double radius) {
    double angle = 2D * Math.PI * index / Math.max(1, count);
    return new Offset(radius * Math.cos(angle), radius * Math.sin(angle));
  }

  static List<UUID> ring(List<UUID> previous, Collection<UUID> members) {
    Set<UUID> present = new LinkedHashSet<>(members);
    List<UUID> ring = new ArrayList<>(present.size());
    for (UUID member : previous) {
      if (present.remove(member)) {
        ring.add(member);
      }
    }
    List<UUID> newcomers = new ArrayList<>(present);
    newcomers.sort(null);
    ring.addAll(newcomers);
    return List.copyOf(ring);
  }

  static double formationArmor(int near, double perGolem, double max) {
    return Math.min(Math.max(0, near) * Math.max(0D, perGolem), Math.max(0D, max));
  }

  static ArmorChange armorChange(double current, double desired) {
    boolean present = !Double.isNaN(current);
    if (desired <= 0D) {
      return present ? ArmorChange.REMOVE : ArmorChange.KEEP;
    }
    return present && current == desired ? ArmorChange.KEEP : ArmorChange.APPLY;
  }

  static double sicRange(double value) {
    return Double.isFinite(value) ? Math.clamp(value, MIN_SIC_RANGE, MAX_SIC_RANGE) : DEFAULT_SIC_RANGE;
  }

  static double ringRadius(double value) {
    return Double.isFinite(value) ? Math.clamp(value, MIN_RING_RADIUS, MAX_RING_RADIUS) : DEFAULT_RING_RADIUS;
  }

  static double armorMax(double value) {
    return Math.min(nonNegative(value), MAX_ARMOR);
  }

  static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  static int holdSeconds(int value) {
    return Math.max(1, value);
  }

  enum Answer {
    SKIP,
    MODE,
    RALLY,
    COMMAND
  }

  enum ArmorChange {
    KEEP,
    APPLY,
    REMOVE
  }

  record Hearing(GolemSignal signal, GolemBody body, GolemCoreType core, List<GolemCoreType> cores, boolean heard, boolean sameWorld,
                 boolean inRange, boolean rallies) {
  }

  record Offset(double x, double z) {
  }
}
