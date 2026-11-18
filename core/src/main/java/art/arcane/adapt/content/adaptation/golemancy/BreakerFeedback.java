package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockStateRef;

final class BreakerFeedback {
  private static final int PARTICLES = 12;
  private static final double SPREAD = 0.25D;
  private static final double SPEED = 0.05D;
  private static final float VOLUME = 0.8F;
  private static final float PITCH = 0.9F;

  private BreakerFeedback() {
  }

  static void play(Adaptation<?> source, Loc at, BlockStateRef broken) {
    Fx.now(source, at, FxPriority.AMBIENT)
        .sound(broken.breakSound(), VOLUME, PITCH)
        .particle(Particles.blockCrack(broken), PARTICLES, 0D, 0D, 0D, SPREAD, SPEED);
  }
}
