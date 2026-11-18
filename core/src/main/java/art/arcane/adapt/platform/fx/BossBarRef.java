package art.arcane.adapt.platform.fx;

import art.arcane.adapt.platform.entity.PlayerRef;

/** Server-side boss bar. Thread-safe; adapters queue packet work as needed. */
public interface BossBarRef {
  void setTitle(String markup);

  /** 0 to 1. */
  void setProgress(float progress);

  void setColor(BossBarColor color);

  void setStyle(BossBarStyle style);

  void show(PlayerRef viewer);

  void hide(PlayerRef viewer);

  /** Hides from everyone and releases the bar. */
  void dispose();
}
