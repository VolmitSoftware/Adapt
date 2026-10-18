package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class GolemStats {
  private static final String NAMESPACE = "adapt";
  private static final String PREFIX = "golem_";
  private static final Pattern SOURCE_ID = Pattern.compile("[a-z0-9_]+");
  private static final Map<String, GolemStatSource> SOURCES = new ConcurrentHashMap<>();
  private static final Set<String> FAILURES_LOGGED = ConcurrentHashMap.newKeySet();

  private GolemStats() {
  }

  public static void register(GolemStatSource source) {
    SOURCES.put(requireSourceId(source.id()), source);
    FAILURES_LOGGED.remove(source.id());
  }

  public static void unregister(GolemStatSource source) {
    SOURCES.remove(source.id(), source);
  }

  public static Collection<GolemStatSource> sources() {
    return List.copyOf(SOURCES.values());
  }

  public static NamespacedKey key(String source, GolemStat stat) {
    return new NamespacedKey(NAMESPACE, PREFIX + requireSourceId(source) + "_" + stat.id());
  }

  public static boolean isStatKey(NamespacedKey key) {
    return key != null && NAMESPACE.equals(key.getNamespace()) && key.getKey().startsWith(PREFIX)
        && !key.equals(GolemScale.MODIFIER_KEY) && !key.equals(GolemScale.RITUAL_KEY);
  }

  public static double healthAfter(double health, double previousMaximum, double nextMaximum, double highestMaximum) {
    if (health <= 0D || !Double.isFinite(previousMaximum) || !Double.isFinite(nextMaximum)) {
      return health;
    }
    double gained = nextMaximum - Math.max(previousMaximum, highestMaximum);
    double healed = gained > 0D ? health + gained : health;
    return Math.max(0D, Math.min(healed, nextMaximum));
  }

  public static void recompute(GolemHandle handle) {
    if (handle.isRetired() || !handle.platform().ownerReady(handle.owner())) {
      return;
    }
    Optional<GolemStatPlan> next = plan(handle);
    if (next.isEmpty() || next.get().equals(handle.appliedStats())) {
      return;
    }
    apply(handle, next.get());
  }

  static void clear(GolemHandle handle) {
    apply(handle, GolemStatPlan.EMPTY);
  }

  static Optional<GolemStatPlan> plan(GolemHandle handle) {
    Map<String, List<GolemStatDelta>> deltas = new HashMap<>();
    for (GolemStatSource source : SOURCES.values()) {
      try {
        deltas.put(source.id(), source.deltas(handle));
      } catch (RuntimeException failure) {
        if (FAILURES_LOGGED.add(source.id())) {
          Adapt.warn("Golem stat source " + source.id() + " failed for bound golem " + handle.id() + "; its stats are left unchanged.", failure);
        }
        return Optional.empty();
      }
    }
    return Optional.of(GolemStatPlan.of(deltas));
  }

  private static void apply(GolemHandle handle, GolemStatPlan plan) {
    Mob golem = handle.entity();
    GolemPlatform platform = handle.platform();
    AttributeInstance maximum = platform.attribute(golem, GolemStat.MAX_HEALTH);
    double previousMaximum = maximum == null ? Double.NaN : maximum.getValue();
    for (GolemStat stat : GolemStat.values()) {
      AttributeInstance instance = platform.attribute(golem, stat);
      if (instance != null) {
        sync(instance, plan.modifiers(stat));
      }
    }
    handle.setAppliedStats(plan);
    if (maximum != null) {
      settleHealth(handle, previousMaximum, maximum.getValue());
    }
  }

  private static void settleHealth(GolemHandle handle, double previousMaximum, double nextMaximum) {
    Mob golem = handle.entity();
    GolemState state = handle.state();
    double highest = state.maxHealthMark();
    if (!golem.isDead()) {
      double health = golem.getHealth();
      double settled = healthAfter(health, previousMaximum, nextMaximum, highest);
      if (settled != health) {
        golem.setHealth(settled);
      }
    }
    if (Double.isFinite(nextMaximum) && nextMaximum > highest) {
      state.setMaxHealthMark(nextMaximum);
      handle.flush();
    }
  }

  private static void sync(AttributeInstance instance, Map<NamespacedKey, GolemStatDelta> wanted) {
    List<AttributeModifier> current = new ArrayList<>(instance.getModifiers());
    Map<NamespacedKey, GolemStatDelta> missing = new HashMap<>(wanted);
    for (AttributeModifier modifier : current) {
      NamespacedKey key = modifier.getKey();
      if (!isStatKey(key)) {
        continue;
      }
      if (matches(modifier, wanted.get(key))) {
        missing.remove(key);
      } else {
        instance.removeModifier(modifier);
      }
    }
    for (Map.Entry<NamespacedKey, GolemStatDelta> entry : missing.entrySet()) {
      GolemStatDelta delta = entry.getValue();
      instance.addModifier(new AttributeModifier(entry.getKey(), delta.amount(), delta.operation(), EquipmentSlotGroup.ANY));
    }
  }

  private static boolean matches(AttributeModifier modifier, GolemStatDelta delta) {
    return delta != null && modifier.getOperation() == delta.operation() && Double.compare(modifier.getAmount(), delta.amount()) == 0;
  }

  private static String requireSourceId(String source) {
    if (source == null || !SOURCE_ID.matcher(source).matches()) {
      throw new IllegalArgumentException("Golem stat source ids use lowercase letters, digits and underscores, found " + source);
    }
    return source;
  }
}
