package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileAction;
import art.arcane.adapt.client.server.panel.PanelViews;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuPaging;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rebuilds the chest menu a mocked {@link GolemWindow} was given, so tests can read it as a native panel. */
public final class MenuReplay {
  private static final int CENTER = GolemLayout.WIDTH / 2;

  private MenuReplay() {
  }

  public static ChestMenu menu(GolemWindow window) {
    Map<Integer, GolemElement> elements = new HashMap<>();
    String title = "";
    int rows = ChestMenu.MAX_ROWS;
    MenuLayout layout = null;
    MenuPaging paging = null;
    List<String> breadcrumb = List.of();
    for (Invocation invocation : Mockito.mockingDetails(window).getInvocations()) {
      String method = invocation.getMethod().getName();
      switch (method) {
        case "setElement", "updateElement" -> {
          int slot = ChestMenu.slot(invocation.<Integer>getArgument(1), invocation.<Integer>getArgument(0) + CENTER);
          GolemElement element = invocation.getArgument(2);
          if (element == null) {
            elements.remove(slot);
          } else {
            elements.put(slot, element);
          }
        }
        case "setTitle" -> title = invocation.getArgument(0);
        case "setViewportHeight" -> rows = invocation.<Integer>getArgument(0);
        case "setLayout" -> layout = invocation.getArgument(0);
        case "setPaging" -> paging = invocation.getArgument(0);
        case "setBreadcrumb" -> breadcrumb = invocation.getArgument(0);
        default -> {
        }
      }
    }
    ChestMenu.Builder builder = ChestMenu.builder(title, rows);
    for (Map.Entry<Integer, GolemElement> entry : elements.entrySet()) {
      builder.set(entry.getKey(), entry.getValue().button());
    }
    return builder.layout(layout).paging(paging).breadcrumb(breadcrumb).build();
  }

  public static PanelView view(GolemWindow window) {
    return PanelViews.view(1, 0, menu(window));
  }

  public static PanelTile tile(PanelView view, int position, int row) {
    int slot = ChestMenu.slot(row, position + CENTER);
    return view.tiles().stream().filter((PanelTile tile) -> tile.slot() == slot).findFirst().orElseThrow();
  }

  public static Map<ClickKind, String> actions(PanelTile tile) {
    Map<ClickKind, String> actions = new EnumMap<>(ClickKind.class);
    for (TileAction action : tile.actions()) {
      actions.put(action.click(), action.label());
    }
    return actions;
  }
}
