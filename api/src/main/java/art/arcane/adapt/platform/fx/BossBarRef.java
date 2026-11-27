package art.arcane.adapt.platform.fx;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/** Server-side boss bar. Thread-safe; adapters queue packet work as needed. */
@ApiStatus.AvailableSince("1.0.0")
public interface BossBarRef {
  /** Replaces the title. */
  void setTitle(String markup);

  /** Fill of the bar, clamped to 0 to 1. */
  void setProgress(float progress);

  /** Replaces the bar colour. */
  void setColor(BossBarColor color);

  /** Replaces the bar style. */
  void setStyle(BossBarStyle style);

  /** Shows the bar to an online player; no-op after {@link #dispose()}. */
  void show(PlayerRef viewer);

  /** Hides the bar from the player. */
  void hide(PlayerRef viewer);

  /** Hides from everyone and releases the bar. */
  void dispose();
}
