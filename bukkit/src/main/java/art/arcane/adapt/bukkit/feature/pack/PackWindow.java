package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.pack.surface.TitleRun;

import java.util.List;

/** The platform window under a pack session: a canvas, or a plain chest over the live inventory. */
sealed interface PackWindow permits CanvasPackWindow, ChestPackWindow {
  /** True when the window shows the 36 player slots as stand-ins. */
  boolean canvas();

  int rows();

  boolean isOpen();

  /** Routes this window's clicks and close to {@code session} from now on. */
  void owner(BukkitPackSession session);

  /** Sets a window slot; null empties it. */
  void set(int windowSlot, PackFrame.Shown shown);

  void flush();

  /** True when {@link #retitle} keeps the window; false when a new title needs a new window. */
  boolean retitlesInPlace();

  void retitle(List<TitleRun> title);

  /** Hands the window over without its close callback; {@code restoreInventory} when the next window is not a canvas. */
  void retire(boolean restoreInventory);

  void close();
}
