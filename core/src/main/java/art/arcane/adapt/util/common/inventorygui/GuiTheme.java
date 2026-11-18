package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.platform.value.Id;

public final class GuiTheme {
  private static final Id BLACK_PANE = Id.minecraft("black_stained_glass_pane");
  private static final Id GRAY_PANE = Id.minecraft("gray_stained_glass_pane");
  private static final Id LIGHT_GRAY_PANE = Id.minecraft("light_gray_stained_glass_pane");

  private GuiTheme() {
  }

  public static void apply(GuiWindow window, String tag) {
    if (window == null) {
      return;
    }

    window.setViewportHeight(GuiLayout.MAX_ROWS);
    if (tag != null) {
      window.setTag(tag);
    }

    if (tag != null && tag.startsWith("golemancy-")) {
      window.setDecorator((GuiWindow w, int position, int row) -> element("bg-" + row + "-" + position, BLACK_PANE,
          "gui", "golemancy", "background").setName(" "));
      return;
    }

    window.setDecorator((GuiWindow w, int position, int row) -> element("bg-" + row + "-" + position,
        background(row, position, w.getViewportHeight()),
        "gui", "background", backgroundKey(row, position, w.getViewportHeight())).setName(" "));
  }

  public static GuiElement element(String id, Id fallback, String... path) {
    CustomModel model = CustomModel.get(fallback, path);
    return new GuiElement(id).setMaterial(model.material()).setModel(model);
  }

  public static String backgroundKey(int row, int position, int rows) {
    int height = Math.max(1, Math.min(GuiLayout.MAX_ROWS, rows));
    if (row == 0 && height >= 3) {
      return position % 2 == 0 ? "header-even" : "header-odd";
    }
    if (row == 1 && height >= 4) {
      return "separator";
    }
    return position % 2 == 0 ? "body-even" : "body-odd";
  }

  public static Id background(int row, int position, int rows) {
    int height = Math.max(1, Math.min(GuiLayout.MAX_ROWS, rows));
    if (row == 0 && height >= 3) {
      return position % 2 == 0 ? GRAY_PANE : LIGHT_GRAY_PANE;
    }

    if (row == 1 && height >= 4) {
      return BLACK_PANE;
    }

    return position % 2 == 0 ? BLACK_PANE : GRAY_PANE;
  }
}
