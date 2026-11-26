package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.platform.entity.EntityRef;
import org.jetbrains.annotations.Nullable;

/**
 * What a Strike or Boon did. A lasting effect names the entity it follows and how long it runs, so the engine can play
 * the quiet lingering tick for exactly that long.
 */
public record Outcome(boolean landed, @Nullable EntityRef lingerOn, int lastingTicks) {
  public static final Outcome MISSED = new Outcome(false, null, 0);
  public static final Outcome INSTANT = new Outcome(true, null, 0);

  public static Outcome lasting(EntityRef entity, int ticks) {
    return new Outcome(true, entity, Math.max(1, ticks));
  }

  public boolean lasts() {
    return landed && lingerOn != null && lastingTicks > 0;
  }
}
