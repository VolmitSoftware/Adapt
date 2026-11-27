package art.arcane.adapt.engine.fx;

/**
 * Block displays only one player can see, grouped by channel (normally the adaptation name) and key. The
 * fallback is {@link PrivateBlockDisplays}, built on the display and viewer SPI; Bukkit installs its
 * display director.
 */
public interface ViewerDisplayPort extends art.arcane.adapt.api.viewer.ViewerDisplayPort {
  ViewerDisplayPort NONE = PrivateBlockDisplays.INSTANCE;
}
