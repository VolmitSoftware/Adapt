package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.event.AdaptationUseEvent;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;

/** Posts {@link AdaptationUseEvent} on the core bus when an adaptation asks whether it may act for a player. */
final class AdaptationUseEvents {
  private AdaptationUseEvents() {
  }

  /** False when a listener cancelled the use. Nothing is allocated while no listener is subscribed. */
  static boolean allows(Adaptation<?> adaptation, AdaptPlayer player, int level) {
    AdaptCore core = AdaptCore.current();
    PlayerRef platformPlayer = player.getPlayer();
    if (core == null || platformPlayer == null) {
      return true;
    }
    AdaptEventBus bus = core.getBus();
    if (!bus.hasListeners(AdaptationUseEvent.class)) {
      return true;
    }
    return !bus.post(new AdaptationUseEvent(platformPlayer, adaptation.getId(), adaptation.getSkill().getId(), level))
        .isCancelled();
  }
}
