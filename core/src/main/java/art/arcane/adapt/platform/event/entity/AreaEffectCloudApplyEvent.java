package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Objects;

/**
 * An effect cloud applies its effects. source() is the thrower or null. The affected list is
 * read-only. Mutable: cancelled.
 */
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

  public EntityRef cloud() {
    return cloud;
  }

  public EntityRef source() {
    return source;
  }

  public Id basePotion() {
    return basePotion;
  }

  public List<EffectInstance> customEffects() {
    return customEffects;
  }

  public List<LivingRef> affected() {
    return affected;
  }
}
