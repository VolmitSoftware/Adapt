package art.arcane.adapt.api.world;

import art.arcane.adapt.api.fx.LayeredViewerGlow;
import art.arcane.adapt.api.fx.PrivateBlockDisplays;
import java.util.UUID;

/**
 * Per-viewer display, glow and open-window state a platform keeps outside core. {@link #NONE} clears the core
 * display and glow fallbacks.
 */
public interface PlayerSessionPort {
  PlayerSessionPort NONE = new PlayerSessionPort() {
    @Override
    public void retireViewer(UUID playerId) {
      PrivateBlockDisplays.INSTANCE.clearViewer(playerId);
    }

    @Override
    public void clearViewer(UUID playerId) {
      PrivateBlockDisplays.INSTANCE.clearViewer(playerId);
    }

    @Override
    public void discardGlowViewer(UUID playerId) {
      LayeredViewerGlow.INSTANCE.discardViewer(playerId);
    }
  };

  void retireViewer(UUID playerId);

  void clearViewer(UUID playerId);

  void discardGlowViewer(UUID playerId);
}
