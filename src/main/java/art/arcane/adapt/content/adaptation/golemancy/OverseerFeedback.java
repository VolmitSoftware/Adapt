package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.util.reflect.registries.Particles;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

final class OverseerFeedback {
  private static final float CHIME_VOLUME = 0.8F;
  private static final float CYCLE_VOLUME = 0.4F;
  private static final float CYCLE_PITCH = 1.6F;
  private static final double PULSE_RADIUS = 0.8D;
  private static final int PULSE_POINTS = 12;
  private static final double PULSE_LIFT = 0.5D;

  private OverseerFeedback() {
  }

  static void cycled(Adaptation<?> source, Player owner) {
    Fx.now(source, owner, FxPriority.GAMEPLAY).only(owner).sound(Sound.UI_BUTTON_CLICK, CYCLE_VOLUME, CYCLE_PITCH);
  }

  static void sent(Adaptation<?> source, Player owner, GolemSignal signal) {
    Fx.now(source, owner, FxPriority.GAMEPLAY).only(owner).sound(Sound.BLOCK_NOTE_BLOCK_CHIME, CHIME_VOLUME, pitch(signal));
  }

  static void pulse(Adaptation<?> source, Entity golem) {
    Fx.now(source, golem, FxPriority.AMBIENT).ring(Particles.END_ROD, PULSE_RADIUS, PULSE_POINTS, golem.getHeight() * PULSE_LIFT);
  }

  static float pitch(GolemSignal signal) {
    return switch (signal) {
      case FOLLOW -> 1.0F;
      case HOLD -> 0.8F;
      case HOME -> 1.2F;
      case WORK -> 1.4F;
      case SIC -> 0.6F;
    };
  }
}
