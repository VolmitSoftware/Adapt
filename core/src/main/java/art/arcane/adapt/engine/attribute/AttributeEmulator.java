package art.arcane.adapt.engine.attribute;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * Stands in for player attributes an older Minecraft version does not have, so adaptations built on them keep a close
 * effect there. Adapt's modifiers on a missing attribute are kept here and turned into what the old version can do:
 * <ul>
 *   <li>{@code block_break_speed}, {@code submerged_mining_speed}: Haste, or Mining Fatigue below normal speed</li>
 *   <li>{@code jump_strength}: Jump Boost (increases only)</li>
 *   <li>{@code safe_fall_distance}, {@code fall_damage_multiplier}: fall damage rescaled in the damage event</li>
 *   <li>{@code oxygen_bonus}: each tick an air loss is undone with chance {@code bonus / (bonus + 1)}, as 1.21 does</li>
 *   <li>{@code sneaking_speed}: Speed while sneaking</li>
 *   <li>{@code movement_efficiency}: Speed sized to cancel the soul sand slowdown</li>
 *   <li>{@code water_movement_efficiency}: Dolphin's Grace</li>
 *   <li>{@code gravity}: Slow Falling while gravity is reduced; increases have no substitute</li>
 *   <li>{@code max_absorption}: tracked only, because absorption has no cap there</li>
 * </ul>
 * Effects it adds are infinite, ambient and hidden, and it removes only an effect that is still the one it added. An
 * attribute the server has is never emulated, so 26.x always uses the real attribute.
 */
public final class AttributeEmulator {
  private static final Id HASTE = Id.minecraft("haste");
  private static final Id MINING_FATIGUE = Id.minecraft("mining_fatigue");
  private static final Id JUMP_BOOST = Id.minecraft("jump_boost");
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id DOLPHINS_GRACE = Id.minecraft("dolphins_grace");
  private static final Id SLOW_FALLING = Id.minecraft("slow_falling");
  private static final List<Id> EFFECT_TYPES = List.of(HASTE, MINING_FATIGUE, JUMP_BOOST, SPEED, DOLPHINS_GRACE, SLOW_FALLING);
  private static final double HASTE_STEP = 0.2D;
  private static final double FATIGUE_FACTOR = 0.3D;
  private static final int MAX_FATIGUE_LEVELS = 4;
  private static final double JUMP_BOOST_STEP = 0.1D;
  private static final double SPEED_STEP = 0.2D;
  private static final double SOUL_SAND_SPEED_FACTOR = 0.4D;
  private static final double EPSILON = 1.0E-6D;
  private static final Map<Id, Double> PLAYER_BASES = Map.ofEntries(
      Map.entry(Attributes.BLOCK_BREAK_SPEED, 1.0D),
      Map.entry(Attributes.SUBMERGED_MINING_SPEED, 0.2D),
      Map.entry(Attributes.JUMP_STRENGTH, 0.42D),
      Map.entry(Attributes.SAFE_FALL_DISTANCE, 3.0D),
      Map.entry(Attributes.FALL_DAMAGE_MULTIPLIER, 1.0D),
      Map.entry(Attributes.OXYGEN_BONUS, 0.0D),
      Map.entry(Attributes.SNEAKING_SPEED, 0.3D),
      Map.entry(Attributes.MOVEMENT_EFFICIENCY, 0.0D),
      Map.entry(Attributes.WATER_MOVEMENT_EFFICIENCY, 0.0D),
      Map.entry(Attributes.GRAVITY, 0.08D),
      Map.entry(Attributes.MAX_ABSORPTION, 0.0D));

  private final Predicate<Id> nativeAttribute;
  private final DoubleSupplier random;
  private final Map<UUID, State> states = new ConcurrentHashMap<>();

  /**
   * @param nativeAttribute true for attributes the server has; those are never emulated
   * @param random          uniform values in [0, 1) for the oxygen roll
   */
  public AttributeEmulator(Predicate<Id> nativeAttribute, DoubleSupplier random) {
    this.nativeAttribute = nativeAttribute;
    this.random = random;
  }

  /** True for attributes this class can stand in for when the server lacks them. */
  public static boolean emulates(Id attribute) {
    return PLAYER_BASES.containsKey(attribute);
  }

  /** Vanilla player base value of an emulated attribute, or NaN. */
  public static double base(Id attribute) {
    Double base = PLAYER_BASES.get(attribute);
    return base == null ? Double.NaN : base;
  }

  /** Records the modifier when the attribute is emulated here; false when the server should handle it. */
  public boolean apply(LivingRef target, Id attribute, AttributeMod modifier) {
    if (target == null || !emulates(attribute) || nativeAttribute.test(attribute)) {
      return false;
    }
    State state = states.computeIfAbsent(target.uuid(), (UUID ignored) -> new State());
    state.modifiers.computeIfAbsent(attribute, (Id ignored) -> new LinkedHashMap<>()).put(modifier.key(), modifier);
    refresh(target, state);
    return true;
  }

  public void remove(LivingRef target, Id attribute, Id key) {
    State state = target == null ? null : states.get(target.uuid());
    if (state == null) {
      return;
    }
    Map<Id, AttributeMod> modifiers = state.modifiers.get(attribute);
    if (modifiers == null || modifiers.remove(key) == null) {
      return;
    }
    if (modifiers.isEmpty()) {
      state.modifiers.remove(attribute);
    }
    refresh(target, state);
  }

  /** Drops every emulated modifier of the entity and the effects added for them; returns how many were dropped. */
  public int clear(LivingRef target) {
    State state = target == null ? null : states.remove(target.uuid());
    if (state == null) {
      return 0;
    }
    int removed = 0;
    for (Map<Id, AttributeMod> modifiers : state.modifiers.values()) {
      removed += modifiers.size();
    }
    state.modifiers.clear();
    applyEffects(target, state, Map.of());
    stopTicking(state);
    return removed;
  }

  /** True when the server lacks at least one attribute this class stands in for. */
  public boolean active() {
    for (Id attribute : PLAYER_BASES.keySet()) {
      if (!nativeAttribute.test(attribute)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes effects this class added in an earlier server run that ended without cleanup (a crash): infinite,
   * ambient, hidden effects of the types it uses on an entity it is not tracking. Returns how many were removed.
   */
  public int dropLeftoverEffects(LivingRef target) {
    if (target == null || states.containsKey(target.uuid())) {
      return 0;
    }
    int removed = 0;
    for (Id type : EFFECT_TYPES) {
      EffectInstance effect = target.effect(type);
      if (effect != null && effect.isInfinite() && effect.ambient() && !effect.particles() && !effect.icon()) {
        target.removeEffect(type);
        removed++;
      }
    }
    return removed;
  }

  /** Forgets an entity that is gone without touching it. */
  public void forget(UUID entityId) {
    State state = states.remove(entityId);
    if (state != null) {
      stopTicking(state);
    }
  }

  /** Emulated value of the attribute with Adapt's modifiers, or NaN when the attribute is not emulated for it. */
  public double value(LivingRef target, Id attribute) {
    State state = target == null ? null : states.get(target.uuid());
    if (state != null && state.modifiers.containsKey(attribute)) {
      return state.value(attribute);
    }
    return emulates(attribute) && !nativeAttribute.test(attribute) ? base(attribute) : Double.NaN;
  }

  /** Rescales fall damage by the emulated safe fall distance and fall damage multiplier. */
  public void onDamage(EntityDamageEvent event) {
    if (event.cause() != DamageCause.FALL) {
      return;
    }
    State state = states.get(event.entity().uuid());
    if (state == null || !state.modifiers.containsKey(Attributes.SAFE_FALL_DISTANCE)
        && !state.modifiers.containsKey(Attributes.FALL_DAMAGE_MULTIPLIER)) {
      return;
    }
    double extraSafeBlocks = state.value(Attributes.SAFE_FALL_DISTANCE) - base(Attributes.SAFE_FALL_DISTANCE);
    double damage = Math.max(0D, event.damage() - extraSafeBlocks) * Math.max(0D, state.value(Attributes.FALL_DAMAGE_MULTIPLIER));
    if (damage <= EPSILON) {
      event.setCancelled(true);
    } else {
      event.setDamage(damage);
    }
  }

  /** Number of entities with emulated modifiers. */
  public int trackedEntities() {
    return states.size();
  }

  private void refresh(LivingRef target, State state) {
    if (state.modifiers.isEmpty()) {
      states.remove(target.uuid(), state);
      applyEffects(target, state, Map.of());
      stopTicking(state);
      return;
    }
    applyEffects(target, state, desiredEffects(target, state));
    if (needsTicking(state)) {
      startTicking(target, state);
    } else {
      stopTicking(state);
    }
  }

  private void tick(LivingRef target, State state) {
    if (states.get(target.uuid()) != state || !target.isValid()) {
      return;
    }
    if (state.modifiers.containsKey(Attributes.OXYGEN_BONUS)) {
      int air = target.remainingAir();
      double bonus = state.value(Attributes.OXYGEN_BONUS);
      if (state.lastAir != Integer.MIN_VALUE && air < state.lastAir && bonus > 0D
          && random.getAsDouble() < bonus / (bonus + 1D)) {
        air = state.lastAir;
        target.setRemainingAir(air);
      }
      state.lastAir = air;
    }
    if (state.modifiers.containsKey(Attributes.SNEAKING_SPEED)) {
      applyEffects(target, state, desiredEffects(target, state));
    }
  }

  private static Map<Id, Integer> desiredEffects(LivingRef target, State state) {
    Map<Id, Integer> desired = new HashMap<>();
    if (state.modifiers.containsKey(Attributes.BLOCK_BREAK_SPEED) || state.modifiers.containsKey(Attributes.SUBMERGED_MINING_SPEED)) {
      double multiplier = state.value(Attributes.BLOCK_BREAK_SPEED) / base(Attributes.BLOCK_BREAK_SPEED)
          * state.value(Attributes.SUBMERGED_MINING_SPEED) / base(Attributes.SUBMERGED_MINING_SPEED);
      if (multiplier >= 1D) {
        want(desired, HASTE, (int) Math.round((multiplier - 1D) / HASTE_STEP));
      } else if (multiplier > 0D) {
        int levels = (int) Math.round(Math.log(multiplier) / Math.log(FATIGUE_FACTOR));
        want(desired, MINING_FATIGUE, Math.min(MAX_FATIGUE_LEVELS, levels));
      } else {
        want(desired, MINING_FATIGUE, MAX_FATIGUE_LEVELS);
      }
    }
    if (state.modifiers.containsKey(Attributes.JUMP_STRENGTH)) {
      double extra = state.value(Attributes.JUMP_STRENGTH) - base(Attributes.JUMP_STRENGTH);
      want(desired, JUMP_BOOST, (int) Math.round(extra / JUMP_BOOST_STEP));
    }
    if (state.modifiers.containsKey(Attributes.SNEAKING_SPEED) && target instanceof PlayerRef player && player.isSneaking()) {
      double multiplier = state.value(Attributes.SNEAKING_SPEED) / base(Attributes.SNEAKING_SPEED);
      want(desired, SPEED, (int) Math.round((multiplier - 1D) / SPEED_STEP));
    }
    if (state.modifiers.containsKey(Attributes.MOVEMENT_EFFICIENCY)) {
      double efficiency = Math.max(0D, Math.min(1D, state.value(Attributes.MOVEMENT_EFFICIENCY)));
      double multiplier = (SOUL_SAND_SPEED_FACTOR + (1D - SOUL_SAND_SPEED_FACTOR) * efficiency) / SOUL_SAND_SPEED_FACTOR;
      want(desired, SPEED, (int) Math.round((multiplier - 1D) / SPEED_STEP));
    }
    if (state.modifiers.containsKey(Attributes.WATER_MOVEMENT_EFFICIENCY)
        && state.value(Attributes.WATER_MOVEMENT_EFFICIENCY) > EPSILON) {
      want(desired, DOLPHINS_GRACE, 1);
    }
    if (state.modifiers.containsKey(Attributes.GRAVITY)
        && state.value(Attributes.GRAVITY) < base(Attributes.GRAVITY) - EPSILON) {
      want(desired, SLOW_FALLING, 1);
    }
    return desired;
  }

  private static void want(Map<Id, Integer> desired, Id effect, int levels) {
    if (levels >= 1) {
      desired.merge(effect, levels - 1, Math::max);
    }
  }

  private static void applyEffects(LivingRef target, State state, Map<Id, Integer> desired) {
    for (Map.Entry<Id, EffectInstance> applied : Map.copyOf(state.effects).entrySet()) {
      Integer amplifier = desired.get(applied.getKey());
      if (amplifier != null && amplifier == applied.getValue().amplifier()) {
        continue;
      }
      if (applied.getValue().equals(target.effect(applied.getKey()))) {
        target.removeEffect(applied.getKey());
      }
      state.effects.remove(applied.getKey());
    }
    for (Map.Entry<Id, Integer> wanted : desired.entrySet()) {
      EffectInstance ours = state.effects.get(wanted.getKey());
      if (ours != null && ours.equals(target.effect(wanted.getKey()))) {
        continue;
      }
      EffectInstance effect = EffectInstance.of(wanted.getKey(), EffectInstance.INFINITE, wanted.getValue()).withAmbient(true).withParticles(false).withIcon(false);
      if (target.addEffect(effect)) {
        state.effects.put(wanted.getKey(), effect);
      }
    }
  }

  private static boolean needsTicking(State state) {
    return state.modifiers.containsKey(Attributes.OXYGEN_BONUS) || state.modifiers.containsKey(Attributes.SNEAKING_SPEED);
  }

  private void startTicking(LivingRef target, State state) {
    if (state.ticker != null) {
      return;
    }
    state.lastAir = Integer.MIN_VALUE;
    state.ticker = Tasks.repeatEntity(target, () -> tick(target, state), 1, 1, () -> forget(target.uuid()));
  }

  private static void stopTicking(State state) {
    TaskHandle ticker = state.ticker;
    state.ticker = null;
    if (ticker != null) {
      ticker.cancel();
    }
  }

  private static final class State {
    private final Map<Id, Map<Id, AttributeMod>> modifiers = new HashMap<>();
    private final Map<Id, EffectInstance> effects = new HashMap<>();
    private volatile TaskHandle ticker;
    private int lastAir = Integer.MIN_VALUE;

    private double value(Id attribute) {
      double base = base(attribute);
      Map<Id, AttributeMod> applied = modifiers.get(attribute);
      if (applied == null) {
        return base;
      }
      double added = base;
      double multiplier = 1D;
      for (AttributeMod modifier : applied.values()) {
        switch (modifier.operation()) {
          case ADD_VALUE -> added += modifier.amount();
          case ADD_MULTIPLIED_BASE -> added += base * modifier.amount();
          case ADD_MULTIPLIED_TOTAL -> multiplier *= 1D + modifier.amount();
        }
      }
      return added * multiplier;
    }
  }
}
