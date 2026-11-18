package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A splash potion breaks. affected() maps each hit entity to its intensity (0 to 1) and is
 * read-only. Mutable: cancelled.
 */
public class PotionSplashEvent extends CancellableEvent {
  private final ProjectileRef potion;
  private final ItemRef item;
  private final List<EffectInstance> effects;
  private final Map<LivingRef, Double> affected;

  public PotionSplashEvent(ProjectileRef potion, ItemRef item, List<EffectInstance> effects, Map<LivingRef, Double> affected) {
    this.potion = Objects.requireNonNull(potion, "potion");
    this.item = Objects.requireNonNull(item, "item");
    this.effects = List.copyOf(effects);
    this.affected = Collections.unmodifiableMap(new LinkedHashMap<>(affected));
  }

  public ProjectileRef potion() {
    return potion;
  }

  public ItemRef item() {
    return item;
  }

  public List<EffectInstance> effects() {
    return effects;
  }

  public Map<LivingRef, Double> affected() {
    return affected;
  }

  public double intensity(LivingRef entity) {
    Double intensity = affected.get(entity);
    return intensity == null ? 0.0D : intensity;
  }
}
