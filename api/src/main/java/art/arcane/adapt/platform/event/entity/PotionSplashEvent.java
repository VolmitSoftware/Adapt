package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.CancellableEvent;
import art.arcane.adapt.platform.item.ItemRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A splash potion breaks. Cancelling keeps it from affecting anyone. affected() maps each hit entity to its intensity
 * (0 to 1) and is read-only. Mutable: cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
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

  /** The thrown potion. */
  public ProjectileRef potion() {
    return potion;
  }

  /** The potion item; empty when the platform has none. */
  public ItemRef item() {
    return item;
  }

  /** Every effect the potion applies, read-only. */
  public List<EffectInstance> effects() {
    return effects;
  }

  /** Each living entity the splash reaches, mapped to its intensity from 0 to 1. */
  public Map<LivingRef, Double> affected() {
    return affected;
  }

  /** Intensity for {@code entity} from 0 to 1; 0 when the splash does not reach it. */
  public double intensity(LivingRef entity) {
    Double intensity = affected.get(entity);
    return intensity == null ? 0.0D : intensity;
  }
}
