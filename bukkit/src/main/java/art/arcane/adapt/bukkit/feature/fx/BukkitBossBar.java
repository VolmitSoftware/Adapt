package art.arcane.adapt.bukkit.feature.fx;

import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.BossBarColor;
import art.arcane.adapt.platform.fx.BossBarRef;
import art.arcane.adapt.platform.fx.BossBarStyle;
import art.arcane.adapt.util.common.compat.PaperApi;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Adventure boss bar; Paper sends its packets from whichever thread changes it. */
final class BukkitBossBar implements BossBarRef {
  private final BossBar bar;
  private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
  private volatile boolean disposed;

  BukkitBossBar(String titleMarkup, BossBarColor color, BossBarStyle style) {
    this.bar = BossBar.bossBar(BukkitText.component(titleMarkup), 1.0F, color(color), overlay(style));
  }

  @Override
  public void setTitle(String markup) {
    bar.name(BukkitText.component(markup));
  }

  @Override
  public void setProgress(float progress) {
    bar.progress(Math.max(0.0F, Math.min(1.0F, progress)));
  }

  @Override
  public void setColor(BossBarColor color) {
    bar.color(color(color));
  }

  @Override
  public void setStyle(BossBarStyle style) {
    bar.overlay(overlay(style));
  }

  @Override
  public void show(PlayerRef viewer) {
    if (disposed) {
      return;
    }
    Player player = Bukkit.getPlayer(viewer.uuid());
    if (player != null && player.isOnline()) {
      viewers.add(player.getUniqueId());
      PaperApi.showBossBar(player, bar);
    }
  }

  @Override
  public void hide(PlayerRef viewer) {
    viewers.remove(viewer.uuid());
    Player player = Bukkit.getPlayer(viewer.uuid());
    if (player != null) {
      PaperApi.hideBossBar(player, bar);
    }
  }

  @Override
  public void dispose() {
    disposed = true;
    for (UUID id : Set.copyOf(viewers)) {
      viewers.remove(id);
      Player player = Bukkit.getPlayer(id);
      if (player != null) {
        PaperApi.hideBossBar(player, bar);
      }
    }
  }

  static BossBar.Color color(BossBarColor color) {
    return switch (color) {
      case PINK -> BossBar.Color.PINK;
      case BLUE -> BossBar.Color.BLUE;
      case RED -> BossBar.Color.RED;
      case GREEN -> BossBar.Color.GREEN;
      case YELLOW -> BossBar.Color.YELLOW;
      case PURPLE -> BossBar.Color.PURPLE;
      case WHITE -> BossBar.Color.WHITE;
    };
  }

  static BossBar.Overlay overlay(BossBarStyle style) {
    return switch (style) {
      case SOLID -> BossBar.Overlay.PROGRESS;
      case SEGMENTED_6 -> BossBar.Overlay.NOTCHED_6;
      case SEGMENTED_10 -> BossBar.Overlay.NOTCHED_10;
      case SEGMENTED_12 -> BossBar.Overlay.NOTCHED_12;
      case SEGMENTED_20 -> BossBar.Overlay.NOTCHED_20;
    };
  }
}
