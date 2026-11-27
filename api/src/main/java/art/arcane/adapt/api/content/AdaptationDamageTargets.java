package art.arcane.adapt.api.content;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import org.jetbrains.annotations.ApiStatus;

/** Which entities an adaptation's own damage may hit. */
@ApiStatus.Experimental
public final class AdaptationDamageTargets {
  private AdaptationDamageTargets() {
  }

  /**
   * True when the target may be hit. With {@code ignorePassiveMobs} only players and hostile mobs qualify, and
   * neutral mobs such as endermen and piglins do not.
   */
  public static boolean allows(EntityRef target, boolean ignorePassiveMobs) {
    return Engine.bridge().world().allowsDamageTarget(target, ignorePassiveMobs);
  }
}
