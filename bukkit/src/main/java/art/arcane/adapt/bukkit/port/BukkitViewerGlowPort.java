package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
import art.arcane.adapt.api.fx.ViewerGlowPort;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.viewer.GlowColor;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Core glow layers on the plugin's {@link ViewerGlowCoordinator}, sharing one layer stack with Bukkit-side skills. */
final class BukkitViewerGlowPort implements ViewerGlowPort {
  @Override
  public boolean isAvailable() {
    ViewerGlowCoordinator coordinator = coordinator();
    return coordinator != null && coordinator.isAvailable();
  }

  @Override
  public boolean set(Layer layer, EntityRef entity, PlayerRef viewer, GlowColor color) {
    ViewerGlowCoordinator coordinator = coordinator();
    Entity target = entity == null ? null : BukkitRefs.entity(entity);
    Player player = viewer == null ? null : BukkitRefs.player(viewer);
    return coordinator != null && layer != null && color != null
        && coordinator.set(layer(layer), target, player, ChatColor.valueOf(color.name()));
  }

  @Override
  public boolean unset(Layer layer, EntityRef entity, PlayerRef viewer) {
    ViewerGlowCoordinator coordinator = coordinator();
    Entity target = entity == null ? null : BukkitRefs.entity(entity);
    Player player = viewer == null ? null : BukkitRefs.player(viewer);
    return coordinator != null && layer != null && coordinator.unset(layer(layer), target, player);
  }

  @Override
  public boolean unset(Layer layer, UUID targetId, int runtimeEntityId, PlayerRef viewer) {
    ViewerGlowCoordinator coordinator = coordinator();
    Player player = viewer == null ? null : BukkitRefs.player(viewer);
    return coordinator != null && layer != null && coordinator.unset(layer(layer), targetId, runtimeEntityId, player);
  }

  @Override
  public void clearLayer(Layer layer) {
    ViewerGlowCoordinator coordinator = coordinator();
    if (coordinator != null && layer != null) {
      coordinator.clearLayer(layer(layer));
    }
  }

  @Override
  public void discardViewer(UUID viewerId) {
    ViewerGlowCoordinator coordinator = coordinator();
    if (coordinator != null) {
      coordinator.discardViewer(viewerId);
    }
  }

  private static ViewerGlowCoordinator.Layer layer(Layer layer) {
    return ViewerGlowCoordinator.Layer.valueOf(layer.name());
  }

  private static ViewerGlowCoordinator coordinator() {
    Adapt plugin = Adapt.instance;
    return plugin == null ? null : plugin.getViewerGlowCoordinator();
  }
}
