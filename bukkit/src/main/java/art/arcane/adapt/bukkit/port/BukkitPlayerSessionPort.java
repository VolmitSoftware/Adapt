package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.engine.fx.ViewerDisplayDirector;
import art.arcane.adapt.engine.fx.ViewerGlowCoordinator;
import art.arcane.adapt.engine.world.PlayerSessionPort;

import java.util.UUID;

public final class BukkitPlayerSessionPort implements PlayerSessionPort {
  @Override
  public void retireViewer(UUID playerId) {
    ViewerDisplayDirector.retireViewer(playerId);
  }

  @Override
  public void clearViewer(UUID playerId) {
    ViewerDisplayDirector.clearViewer(playerId);
  }

  @Override
  public void discardGlowViewer(UUID playerId) {
    Adapt plugin = Adapt.instance;
    ViewerGlowCoordinator glow = plugin == null ? null : plugin.getViewerGlowCoordinator();
    if (glow != null) {
      glow.discardViewer(playerId);
    }
  }
}
