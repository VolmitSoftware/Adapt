package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.UUID;

/** Per-viewer block displays through the Bukkit display director (viewer limits, orphan cleanup). */
final class BukkitViewerDisplayPort implements ViewerDisplayPort {
  @Override
  public boolean showBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block,
                           Rgb glowColor, int durationTicks) {
    Player player = BukkitRefs.player(viewer);
    if (player == null || location == null || block == null) {
      return false;
    }
    Color color = glowColor == null ? null : Color.fromRGB(glowColor.red(), glowColor.green(), glowColor.blue());
    return ViewerDisplayDirector.showBlock(channel, key, player, BukkitRefs.location(location),
        BukkitRefs.blockData(block), color, durationTicks);
  }

  @Override
  public boolean showLine(String channel, String key, PlayerRef viewer, Loc start, Loc end, BlockStateRef block,
                          Rgb glowColor, double thickness, int durationTicks) {
    Player player = BukkitRefs.player(viewer);
    if (player == null || start == null || end == null || block == null) {
      return false;
    }
    Color color = glowColor == null ? null : Color.fromRGB(glowColor.red(), glowColor.green(), glowColor.blue());
    return ViewerDisplayDirector.showLine(channel, key, player, BukkitRefs.location(start), BukkitRefs.location(end),
        BukkitRefs.blockData(block), color, thickness, durationTicks);
  }

  @Override
  public boolean showCuboid(String channel, String key, PlayerRef viewer, Loc minimum, Vec3 size, BlockStateRef block,
                            Rgb glowColor, boolean inside, int durationTicks) {
    Player player = BukkitRefs.player(viewer);
    if (player == null || minimum == null || size == null || block == null) {
      return false;
    }
    Color color = glowColor == null ? null : Color.fromRGB(glowColor.red(), glowColor.green(), glowColor.blue());
    return ViewerDisplayDirector.showCuboid(channel, key, player, BukkitRefs.location(minimum),
        new Vector(size.x(), size.y(), size.z()), BukkitRefs.blockData(block), color, inside, durationTicks);
  }

  @Override
  public boolean showPersistentBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block,
                                     Rgb glowColor) {
    Player player = BukkitRefs.player(viewer);
    if (player == null || location == null || block == null) {
      return false;
    }
    Color color = glowColor == null ? null : Color.fromRGB(glowColor.red(), glowColor.green(), glowColor.blue());
    return ViewerDisplayDirector.showPersistentBlock(channel, key, player, BukkitRefs.location(location),
        BukkitRefs.blockData(block), color);
  }

  @Override
  public boolean isShowing(String channel, String key, UUID viewerId, Loc location) {
    return location != null && ViewerDisplayDirector.isShowing(channel, key, viewerId, BukkitRefs.location(location));
  }

  @Override
  public void clearViewer(String channel, UUID viewerId) {
    ViewerDisplayDirector.clearViewer(channel, viewerId);
  }

  @Override
  public void clearViewerKey(String channel, String key, UUID viewerId) {
    ViewerDisplayDirector.clearViewerKey(channel, key, viewerId);
  }

  @Override
  public void clearChannel(String channel) {
    ViewerDisplayDirector.clearChannel(channel);
  }
}
