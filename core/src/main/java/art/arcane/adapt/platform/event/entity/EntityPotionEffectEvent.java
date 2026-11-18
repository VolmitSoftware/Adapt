package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/** A potion effect is added, changed, removed or cleared. Mutable: cancelled. */
public class EntityPotionEffectEvent extends CancellableEvent {
  private final LivingRef entity;
  private final Id modifiedType;
  private final EffectInstance oldEffect;
  private final EffectInstance newEffect;
  private final Action action;
  private final Cause cause;

  public EntityPotionEffectEvent(LivingRef entity, Id modifiedType, EffectInstance oldEffect, EffectInstance newEffect,
                                 Action action, Cause cause) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.modifiedType = Objects.requireNonNull(modifiedType, "modifiedType");
    this.oldEffect = oldEffect;
    this.newEffect = newEffect;
    this.action = Objects.requireNonNull(action, "action");
    this.cause = cause == null ? Cause.UNKNOWN : cause;
  }

  public LivingRef entity() {
    return entity;
  }

  public Id modifiedType() {
    return modifiedType;
  }

  public EffectInstance oldEffect() {
    return oldEffect;
  }

  public EffectInstance newEffect() {
    return newEffect;
  }

  public Action action() {
    return action;
  }

  public Cause cause() {
    return cause;
  }

  public enum Action {
    ADDED,
    CHANGED,
    CLEARED,
    REMOVED
  }

  public enum Cause {
    AREA_EFFECT_CLOUD,
    ATTACK,
    BEACON,
    COMMAND,
    CONDUIT,
    EXPIRATION,
    FOOD,
    ILLUSION,
    MILK,
    PLUGIN,
    POTION_DRINK,
    POTION_SPLASH,
    TOTEM,
    WITHER_ROSE,
    UNKNOWN
  }
}
