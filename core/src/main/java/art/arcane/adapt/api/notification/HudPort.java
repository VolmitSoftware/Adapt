package art.arcane.adapt.api.notification;

import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * The action bar compositor {@link AdaptHud} publishes to. Bukkit installs one over VolmLib's shared HUD so other
 * Volmit plugins' segments merge with Adapt's; without one, {@link AdaptHud} composes Adapt's own segments.
 */
public interface HudPort {
  void publish(PlayerRef player, HudSegment segment);

  void clear(PlayerRef player, String purpose);

  void clearAll(PlayerRef player);

  void shutdown();
}
