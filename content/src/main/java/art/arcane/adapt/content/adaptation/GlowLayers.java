package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.api.viewer.GlowLayer;
import art.arcane.adapt.platform.value.Id;

/** The glow layers built-in adaptations draw on; a higher priority wins on an entity both layers mark. */
public final class GlowLayers {
  public static final GlowLayer GOLEMANCY = layer("golemancy", 50);
  public static final GlowLayer HUNTER = layer("hunter", 75);
  public static final GlowLayer STEALTH_SIGHT = layer("stealth_sight", 100);
  public static final GlowLayer TRAGOUL_DEATH_SENSE = layer("tragoul_death_sense", 200);
  public static final GlowLayer RANGED_TRAJECTORY_SIGHT = layer("ranged_trajectory_sight", 300);
  public static final GlowLayer STEALTH_THREAT = layer("stealth_threat", 400);
  public static final GlowLayer TAMING_ALPHAS_COMMAND = layer("taming_alphas_command", 600);
  public static final GlowLayer RANGED_HEARTSEEKER = layer("ranged_heartseeker", 700);
  public static final GlowLayer GOLEMANCY_LOCATE = layer("golemancy_locate", 800);

  private GlowLayers() {
  }

  private static GlowLayer layer(String name, int priority) {
    return new GlowLayer(Id.adapt("glow/" + name), priority);
  }
}
