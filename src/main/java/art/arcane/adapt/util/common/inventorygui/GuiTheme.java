package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.Window;
import art.arcane.volmlib.util.inventorygui.WindowResolution;
import org.bukkit.Material;

public final class GuiTheme {
  private GuiTheme() {
  }

  public static void apply(Window window, String tag) {
    if (window == null) {
      return;
    }

    window.setResolution(WindowResolution.W9_H6);
    window.setViewportHeight(WindowResolution.W9_H6.getMaxHeight());
    if (tag != null) {
      window.setTag(tag);
    }

    window.setDecorator((w, position, row) -> element("bg-" + row + "-" + position, background(row, position, w.getViewportHeight()),
        "gui", "background", backgroundKey(row, position, w.getViewportHeight())).setName(" "));
  }

  public static UIElement element(String id, Material fallback, String... path) {
    CustomModel model = CustomModel.get(fallback, path);
    return new UIElement(id).setMaterial(new MaterialBlock(model.material())).setBaseItemStack(model.toItemStack());
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

  public static Material background(int row, int position, int rows) {
    int height = Math.max(1, Math.min(GuiLayout.MAX_ROWS, rows));
    if (row == 0 && height >= 3) {
      return position % 2 == 0 ? Material.GRAY_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    }

    if (row == 1 && height >= 4) {
      return Material.BLACK_STAINED_GLASS_PANE;
    }

    return position % 2 == 0 ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
  }
}
