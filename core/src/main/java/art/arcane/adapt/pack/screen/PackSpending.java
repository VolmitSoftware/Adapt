package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * The spending list behind the Skills chips: every adaptation the player works with and what it costs, as an ordinary
 * chest menu that the ledger skin dresses. Entries open their adaptation.
 */
final class PackSpending {
  static final int ENTRIES = 45;
  private static final Id ARROW = Id.minecraft("arrow");

  private PackSpending() {
  }

  static void open(PackScreens screens, PlayerRef viewer) {
    show(screens, viewer, 0);
  }

  static ChestMenu menu(PackScreens screens, PlayerRef viewer, int page) {
    CatalogIndex catalog = screens.views().catalog(viewer);
    List<SpendView> spending = screens.views().spending(viewer);
    int pages = Math.max(1, (spending.size() + ENTRIES - 1) / ENTRIES);
    int current = Math.max(0, Math.min(page, pages - 1));
    ChestMenu.Builder menu = ChestMenu.builder(AdaptLanguage.text(GuiMessages.PACK_SPENDING), PageBuilder.ROWS)
        .layout(MenuLayout.LIST).paging(new MenuPaging(current, pages, spending.size()))
        .breadcrumb(List.of(AdaptLanguage.text(GuiMessages.SKILLS)));
    for (int slot = 0; slot < ENTRIES; slot++) {
      int index = (current * ENTRIES) + slot;
      if (index >= spending.size()) {
        break;
      }
      SpendView spend = spending.get(index);
      AdaptationView adaptation = catalog == null ? null : catalog.adaptation(spend.adaptationId());
      if (adaptation == null) {
        continue;
      }
      List<String> lore = new ArrayList<>();
      lore.add(PackLabels.GREY + AdaptLanguage.text(GuiMessages.PACK_LEVEL, trusted("level", spend.level())));
      if (spend.knowledge() > 0) {
        lore.add(PackLabels.GREY + AdaptLanguage.text(GuiMessages.PACK_REFUND, trusted("refund", spend.knowledge())));
      }
      if (spend.power() > 0) {
        lore.add(PackLabels.GREY + AdaptLanguage.text(GuiMessages.POWER_DRAIN, trusted("power", spend.power())));
      }
      menu.set(slot, new MenuButton(PageBuilder.icon(adaptation.icon(), adaptation.name(), lore),
          (MenuClick click) -> screens.route(viewer, new Route.Adaptation(adaptation.id()))));
    }
    menu.set(ChestMenu.slot(5, 0), new MenuButton(MenuIcon.of(ARROW, AdaptLanguage.text(GuiMessages.BACK), List.of()),
        (MenuClick click) -> screens.route(viewer, new Route.Skills())).withHint(MenuHint.navigation("back")));
    if (current > 0) {
      menu.set(ChestMenu.slot(5, 3), new MenuButton(MenuIcon.of(ARROW, AdaptLanguage.text(GuiMessages.PREVIOUS), List.of()),
          (MenuClick click) -> show(screens, viewer, current - 1)).withHint(MenuHint.navigation("previous")));
    }
    if (current < pages - 1) {
      menu.set(ChestMenu.slot(5, 5), new MenuButton(MenuIcon.of(ARROW, AdaptLanguage.text(GuiMessages.NEXT), List.of()),
          (MenuClick click) -> show(screens, viewer, current + 1)).withHint(MenuHint.navigation("next")));
    }
    return menu.build();
  }

  private static void show(PackScreens screens, PlayerRef viewer, int page) {
    screens.menu(viewer, menu(screens, viewer, page));
  }
}
