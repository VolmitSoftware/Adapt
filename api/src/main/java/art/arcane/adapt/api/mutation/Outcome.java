package art.arcane.adapt.api.mutation;

import art.arcane.adapt.platform.entity.EntityRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * What a Strike, Boon or signature did. A lasting effect names the entity it follows and how long it runs, so the
 * engine can play the quiet lingering tick for exactly that long.
 *
 * <p>A run in which nothing landed and no Fuel was paid starts no cooldown and plays no cue.
 *
 * @param landed       whether the effect did anything
 * @param lingerOn     the entity a lasting effect follows; null for an instant one
 * @param lastingTicks how many ticks a lasting effect runs; 0 for an instant one
 */
@ApiStatus.Experimental
public record Outcome(boolean landed, @Nullable EntityRef lingerOn, int lastingTicks) {
  /** Nothing happened, such as no valid target in reach. */
  public static final Outcome MISSED = new Outcome(false, null, 0);
  /** The effect happened at once and is over. */
  public static final Outcome INSTANT = new Outcome(true, null, 0);

  /** An effect that landed and runs on {@code entity} for {@code ticks} ticks, at least 1. */
  public static Outcome lasting(EntityRef entity, int ticks) {
    return new Outcome(true, entity, Math.max(1, ticks));
  }

  /** Whether this is a landed effect with an entity to follow and a duration above 0. */
  public boolean lasts() {
    return landed && lingerOn != null && lastingTicks > 0;
  }
}
