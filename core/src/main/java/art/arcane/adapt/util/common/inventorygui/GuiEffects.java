package art.arcane.adapt.util.common.inventorygui;

import java.util.List;

public final class GuiEffects {
  private GuiEffects() {
  }

  public static void applyReveal(GuiWindow window, List<Placement> placements) {
    if (window == null || placements == null || placements.isEmpty()) {
      return;
    }

    for (Placement placement : placements) {
      if (placement == null || placement.element() == null) {
        continue;
      }
      window.setElement(placement.position(), placement.row(), placement.element());
    }
  }

  public record Placement(int position, int row, GuiElement element) {
  }
}
