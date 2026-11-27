package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.pack.link.MenuStyle;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

/**
 * Book settings behind the Skills screen's settings slot: the player's choice of book menus, chest menus, or book
 * menus while the pack is loaded, as an ordinary chest menu the ledger skin dresses. A choice applies at once and
 * reopens Skills in the menus it picks.
 */
final class PackBookSettings {
  static final int ROWS = 2;
  private static final Id ARROW = Id.minecraft("arrow");

  private PackBookSettings() {
  }

  static void open(PackScreens screens, PlayerRef viewer) {
    screens.menu(viewer, menu(screens, viewer));
  }

  static ChestMenu menu(PackScreens screens, PlayerRef viewer) {
    MenuStyle current = screens.views().menuStyle(viewer);
    ChestMenu.Builder menu = ChestMenu.builder(AdaptLanguage.text(GuiMessages.PACK_SETTINGS), ROWS)
        .breadcrumb(List.of(AdaptLanguage.text(GuiMessages.SKILLS)));
    choice(menu, screens, viewer, 2, Id.minecraft("book"), GuiMessages.PACK_MENUS_BOOK, MenuStyle.BOOK, current);
    choice(menu, screens, viewer, 4, Id.minecraft("chest"), GuiMessages.PACK_MENUS_CHEST, MenuStyle.CHEST, current);
    choice(menu, screens, viewer, 6, Id.minecraft("compass"), GuiMessages.PACK_MENUS_AUTO, null, current);
    menu.set(ChestMenu.slot(ROWS - 1, 0), new MenuButton(MenuIcon.of(ARROW, AdaptLanguage.text(GuiMessages.BACK), List.of()),
        (MenuClick click) -> screens.route(viewer, new Route.Skills())).withHint(MenuHint.navigation("back")));
    return menu.build();
  }

  private static void choice(ChestMenu.Builder menu, PackScreens screens, PlayerRef viewer, int slot, Id item, TextKey label,
                             MenuStyle style, MenuStyle current) {
    boolean chosen = style == current;
    MenuIcon icon = MenuIcon.of(item, PackLabels.coloured(chosen ? PackLabels.BLUE : PackLabels.INK,
        (chosen ? "> " : "") + AdaptLanguage.text(label)), List.of()).withGlint(chosen);
    menu.set(slot, new MenuButton(icon, (MenuClick click) -> screens.menus(viewer, style)));
  }
}
