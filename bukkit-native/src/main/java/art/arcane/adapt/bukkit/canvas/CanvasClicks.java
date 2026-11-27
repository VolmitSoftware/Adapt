package art.arcane.adapt.bukkit.canvas;

/** Receives every click on a canvas window, on the viewer's owning thread. Nothing in the window has moved. */
@FunctionalInterface
public interface CanvasClicks {
  void clicked(int windowSlot, int button, CanvasInput input);
}
