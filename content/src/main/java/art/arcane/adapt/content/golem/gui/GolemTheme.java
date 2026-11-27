package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.item.ItemArt;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;

public final class GolemTheme {
  private static final Id BLACK_PANE = Id.minecraft("black_stained_glass_pane");
  private static final Set<String> NAVIGATION_TARGETS = Set.of("back", "previous", "next", "first", "last", "page-info");

  private GolemTheme() {
  }

  /** Golemancy's look: every empty slot a black pane, six rows unless the window asks for fewer. */
  public static void apply(GolemWindow window, String tag) {
    if (window == null) {
      return;
    }
    window.setViewportHeight(GolemLayout.MAX_ROWS);
    if (tag != null) {
      window.setTag(tag);
    }
    window.setDecorator((GolemWindow w, int position, int row) -> element("bg-" + row + "-" + position, BLACK_PANE,
        "gui", "golemancy", "background").setName(" ").setRole(MenuRole.DECORATION));
  }

  public static GolemElement element(String id, Id fallback, String... path) {
    ItemRef model = ItemArt.modelled(fallback, path);
    GolemElement element = new GolemElement(id).setMaterial(model.type()).setModel(model);
    String target = path.length == 0 ? null : path[path.length - 1];
    if (target != null && NAVIGATION_TARGETS.contains(target)) {
      element.setHint(MenuHint.navigation(target));
    }
    return element;
  }
}
