package art.arcane.adapt.api.fx;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

/**
 * One-shot effects owned by a skill or adaptation. The owner's particle and sound settings and its skill's colour
 * apply. Inside a component, prefer its {@code fx(...)} helpers.
 */
@ApiStatus.Experimental
public final class Fx {
  private Fx() {
  }

  /** An emitter anchored at {@code location}; one that draws nothing when the owner's effects are off. */
  public static FxEmitter now(ContentComponent owner, Loc location, FxPriority priority) {
    return Engine.bridge().fx().now(owner, location, priority);
  }

  /** An emitter anchored at {@code entity}'s position now. */
  public static FxEmitter now(ContentComponent owner, EntityRef entity, FxPriority priority) {
    return Engine.bridge().fx().now(owner, entity, priority);
  }

  /**
   * Plays {@code sound} for {@code viewer} alone at {@code location}, where Adapt last saw the viewer; for a player
   * who has just teleported, whom an emitter at the new location would miss.
   */
  public static void sound(Adaptation<?> owner, PlayerRef viewer, Loc location, Id sound, float volume,
                           float pitch) {
    Engine.bridge().fx().sound(owner, viewer, location, sound, volume, pitch);
  }
}
