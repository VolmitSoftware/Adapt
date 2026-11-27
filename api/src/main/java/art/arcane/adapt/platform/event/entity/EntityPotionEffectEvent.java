package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A living entity's potion effect is about to be added, changed, removed or cleared, or has run out. Cancelling keeps
 * the effect as it was; a cancelled expiry keeps it for another tick.
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /** The entity whose effects change. */
  public LivingRef entity() {
    return entity;
  }

  /** Id of the effect type, such as {@code minecraft:speed}. */
  public Id modifiedType() {
    return modifiedType;
  }

  /** The effect before the change, or null when it is being added. */
  public EffectInstance oldEffect() {
    return oldEffect;
  }

  /** The effect after the change, or null when it is being removed or cleared. */
  public EffectInstance newEffect() {
    return newEffect;
  }

  /** What happens to the effect. */
  public Action action() {
    return action;
  }

  /** What triggered the change; {@link Cause#UNKNOWN} when the platform cannot tell. */
  public Cause cause() {
    return cause;
  }

  /**
   * What happens to the effect.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Action {
    /** The entity did not have the effect before. */
    ADDED,
    /** An effect of the same type is replaced. */
    CHANGED,
    /** All effects are removed at once; posted once per active effect. */
    CLEARED,
    /** One effect is removed or ran out. */
    REMOVED
  }

  /** Effect change causes with Paper's names. */
  public enum Cause {
    AREA_EFFECT_CLOUD,
    /** Applied by another entity. */
    ATTACK,
    BEACON,
    COMMAND,
    CONDUIT,
    /** The effect ran out. */
    EXPIRATION,
    FOOD,
    ILLUSION,
    MILK,
    /** Changed through Adapt's or a plugin's API. */
    PLUGIN,
    POTION_DRINK,
    POTION_SPLASH,
    TOTEM,
    WITHER_ROSE,
    /** The platform could not tell the cause. */
    UNKNOWN
  }
}
