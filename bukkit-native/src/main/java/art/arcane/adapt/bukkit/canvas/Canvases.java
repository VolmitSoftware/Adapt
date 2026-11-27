package art.arcane.adapt.bukkit.canvas;

import org.bukkit.entity.Player;

/** Opens canvas windows. The implementation is compiled against the server and loaded reflectively. */
public interface Canvases {
  /** Implementation class, loaded by name so an unsupported server only loses canvas windows. */
  String IMPLEMENTATION = "art.arcane.adapt.bukkit.canvas.server.ServerCanvases";

  /** Throws when this server build lacks something a canvas needs. */
  void verify(Player sample);

  /**
   * Opens a canvas over whatever the viewer has open. {@code items} holds {@code rows * 9 + 36} entries, null for empty.
   * {@code closed} runs once when the window closes for any reason but {@link CanvasWindow#retire}. Returns null when
   * the open was refused.
   */
  CanvasWindow open(Player viewer, int rows, String titleJson, CanvasItem[] items, CanvasClicks clicks, Runnable closed);
}
