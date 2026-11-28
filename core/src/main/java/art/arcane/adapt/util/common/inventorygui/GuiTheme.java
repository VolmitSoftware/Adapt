package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;

public final class GuiTheme {
  private static final Set<String> NAVIGATION_TARGETS = Set.of("back", "previous", "next", "first", "last", "page-info");

  private GuiTheme() {
  }

  public static GuiElement element(String id, Id fallback, String... path) {
    CustomModel model = CustomModel.get(fallback, path);
    GuiElement element = new GuiElement(id).setMaterial(model.material()).setModel(model);
    String target = path.length == 0 ? null : path[path.length - 1];
    if (target != null && NAVIGATION_TARGETS.contains(target)) {
      element.setHint(MenuHint.navigation(target));
    }
    return element;
  }
}
