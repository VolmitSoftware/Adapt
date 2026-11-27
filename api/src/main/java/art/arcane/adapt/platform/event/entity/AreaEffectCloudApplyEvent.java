package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

/**
 * A lingering effect cloud is about to apply its effects to the living entities inside it, once per application round.
 * Cancelling skips the round: no entity receives the effects this time.
 */
@ApiStatus.AvailableSince("1.0.0")
public class AreaEffectCloudApplyEvent extends CancellableEvent {
  private final EntityRef cloud;
  private final EntityRef source;
  private final Id basePotion;
  private final List<EffectInstance> customEffects;
  private final List<LivingRef> affected;

  public AreaEffectCloudApplyEvent(EntityRef cloud, EntityRef source, Id basePotion, List<EffectInstance> customEffects, List<LivingRef> affected) {
    this.cloud = Objects.requireNonNull(cloud, "cloud");
    this.source = source;
    this.basePotion = basePotion;
    this.customEffects = List.copyOf(customEffects);
    this.affected = List.copyOf(affected);
  }

  /** The effect cloud entity. */
  public EntityRef cloud() {
    return cloud;
  }

  /** The entity that threw the potion behind the cloud, or null when it has none or it is not an entity. */
  public EntityRef source() {
    return source;
  }

  /** Id of the cloud's base potion type, such as {@code minecraft:poison}, or null when it has none. */
  public Id basePotion() {
    return basePotion;
  }

  /** Effects added on top of the base potion, read-only; empty when there are none. */
  public List<EffectInstance> customEffects() {
    return customEffects;
  }

  /** Living entities this round reaches, read-only. */
  public List<LivingRef> affected() {
    return affected;
  }
}
