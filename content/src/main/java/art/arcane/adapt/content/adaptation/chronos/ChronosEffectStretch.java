package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.ObjIntConsumer;

/**
 * Overtime's stretch: a beneficial effect applied to a player is re-applied a tick later with extra duration, under a
 * guard so the re-application is not stretched again.
 */
public final class ChronosEffectStretch {
  private static final Set<UUID> RESTORING = ConcurrentHashMap.newKeySet();
  private final Adaptation<?> owner;
  private final Map<UUID, Boolean> reapplying;
  private final ObjIntConsumer<PlayerRef> onExtended;

  /**
   * How much an effect is stretched: a share of its duration for the owner's level, capped, for base durations within
   * the bounds.
   */
  public record Stretch(IntToDoubleFunction extensionPercent, int maxBonusTicks, int minimumDurationTicks,
                        int maximumBaseDurationTicks) {
  }

  /** {@code onExtended} receives the bonus ticks of each stretch; {@code reapplying} comes from player state. */
  public ChronosEffectStretch(Adaptation<?> owner, Map<UUID, Boolean> reapplying, ObjIntConsumer<PlayerRef> onExtended) {
    this.owner = owner;
    this.reapplying = reapplying;
    this.onExtended = onExtended;
  }

  public boolean isReapplying(UUID id) {
    return reapplying.containsKey(id);
  }

  public void forget(UUID id) {
    reapplying.remove(id);
  }

  /** Applies {@code effect} without it being seen as a new effect to stretch or shorten. */
  public void reapply(PlayerRef p, EffectInstance effect) {
    UUID id = p.uuid();
    reapplying.put(id, true);
    try {
      p.addEffect(effect);
    } finally {
      reapplying.remove(id);
    }
  }

  /** Gives back an effect that was held aside; no stretch of any owner lengthens it again. */
  public static void restore(PlayerRef p, EffectInstance effect) {
    UUID id = p.uuid();
    RESTORING.add(id);
    try {
      p.addEffect(effect);
    } finally {
      RESTORING.remove(id);
    }
  }

  /** Schedules the stretch for an added or changed beneficial effect that {@code allowed} lets through. */
  public void stretch(EntityPotionEffectEvent e, BiPredicate<PlayerRef, Id> allowed, Stretch stretch) {
    EntityPotionEffectEvent.Action action = e.action();
    if (action != EntityPotionEffectEvent.Action.ADDED && action != EntityPotionEffectEvent.Action.CHANGED) {
      return;
    }
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    if (reapplying.containsKey(p.uuid()) || RESTORING.contains(p.uuid())) {
      return;
    }

    EffectInstance newEffect = e.newEffect();
    if (newEffect == null) {
      return;
    }

    int duration = newEffect.durationTicks();
    if (duration < stretch.minimumDurationTicks() || duration > stretch.maximumBaseDurationTicks()) {
      return;
    }

    Id type = newEffect.type();
    if (!ChronosOvertime.BENEFICIAL.contains(type) || !allowed.test(p, type)) {
      return;
    }

    int level = owner.getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int bonus = (int) Math.min(stretch.maxBonusTicks(),
        Math.round(duration * stretch.extensionPercent().applyAsDouble(level)));
    if (bonus <= 0) {
      return;
    }

    int amplifier = newEffect.amplifier();
    int targetDuration = duration + bonus;
    Runnable extend = () -> {
      if (!p.isOnline() || p.isDead()) {
        return;
      }
      EffectInstance current = p.effect(type);
      if (current == null
          || current.amplifier() != amplifier
          || current.durationTicks() <= 0
          || current.durationTicks() >= targetDuration) {
        return;
      }
      reapply(p, EffectInstance.of(type, targetDuration, amplifier).withAmbient(current.ambient()).withParticles(current.particles()).withIcon(current.icon()));
      onExtended.accept(p, bonus);
      ChronosOvertime.stretchFx(owner, p, bonus >= stretch.maxBonusTicks() * 0.6D);
    };
    if (AdaptApi.scheduler().isRegionized()) {
      AdaptApi.scheduler().runEntity(p, extend, 1);
    } else {
      AdaptApi.scheduler().runGlobal(extend, 1);
    }
  }
}
