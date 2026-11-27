package art.arcane.adapt.pack.menu;

import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.ScreenLayout;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.TitleComposer;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuAction;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.menu.MenuSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dresses any chest menu as a ledger page: the same slots and click handlers, the title replaced by the ledger page
 * for its row count with the breadcrumb and title on its ribbon, background fillers turned into items that draw and
 * say nothing, back and paging given pack icons (greyed while they do nothing), and every other item, the page
 * information among them, in the tome's tooltip frame. The player inventory
 * stays real and sits in the page's wells.
 */
public final class LedgerSkin {
  public static final String SCREEN = "ledger";
  private static final String KIT = PackArt.TOME;
  /** Navigation targets drawn by the pack, with their art when clickable and when not. */
  private static final Map<String, List<String>> CHROME = Map.of("back", List.of(PackArt.BACK, PackArt.BACK_OFF),
      "previous", List.of(PackArt.PREVIOUS, PackArt.PREVIOUS_OFF), "first", List.of(PackArt.PREVIOUS, PackArt.PREVIOUS_OFF),
      "next", List.of(PackArt.NEXT, PackArt.NEXT_OFF), "last", List.of(PackArt.NEXT, PackArt.NEXT_OFF));

  private LedgerSkin() {
  }

  /** The skinned menu and its surface; clicks reach the original handlers with {@code exposed} as their session. */
  public static Skinned skin(ChestMenu menu, PackResources resources, MenuSession exposed, Consumer<MenuSession> closed) {
    ScreenLayout layout = resources.layout().screen(SCREEN + menu.rows(), false);
    TitleComposer title = resources.composer();
    resources.page(title, layout, new PackResources.PageKit(KIT, false));
    resources.crumbs(title, title, layout.text("crumbs"), KIT, crumbs(menu));
    PackSurface.Builder surface = PackSurface.builder().topRowsOnly(true);
    ChestMenu.Builder skinned = menu.toBuilder().onClose((MenuSession session) -> closed.accept(exposed));
    for (Map.Entry<Integer, MenuButton> entry : menu.buttons().entrySet()) {
      int slot = entry.getKey();
      MenuButton button = entry.getValue();
      PackSlot packSlot = PackSlot.top(slot);
      String chrome = chrome(button);
      MenuButton bound = rebound(button, exposed);
      if (decoration(button)) {
        surface.look(packSlot, resources.blank());
        bound = carried(bound, resources);
      } else if (chrome != null) {
        surface.look(packSlot, resources.item(CHROME.get(chrome).get(button.clickable() ? 0 : 1), KIT));
        bound = carried(bound, resources);
      } else {
        surface.look(packSlot, resources.framed(KIT));
      }
      skinned.set(slot, bound);
    }
    return new Skinned(skinned.build(), surface.title(title.runs()).build());
  }

  /** The breadcrumb, then the title. */
  static List<String> crumbs(ChestMenu menu) {
    List<String> crumbs = new ArrayList<>(menu.breadcrumb());
    crumbs.add(menu.title());
    return crumbs;
  }

  /** Background filler: marked so, or a blank display-only button with no hint. */
  static boolean decoration(MenuButton button) {
    MenuRole role = button.meta().role();
    if (role != null) {
      return role == MenuRole.DECORATION;
    }
    if (button.hint() != null || button.clickable()) {
      return false;
    }
    MenuIcon icon = button.icon();
    String name = icon.name() != null ? icon.name() : icon.item() == null ? "" : icon.item().displayName();
    List<String> lore = !icon.lore().isEmpty() || icon.item() == null ? icon.lore() : icon.item().lore();
    if (!blank(name)) {
      return false;
    }
    for (String line : lore) {
      if (!blank(line)) {
        return false;
      }
    }
    return true;
  }

  /** The target a navigation button the pack draws stands for ({@code back}, {@code next} ...), or null. */
  static String chrome(MenuButton button) {
    MenuHint hint = button.hint();
    if (hint == null || hint.kind() != MenuHint.Kind.NAVIGATION) {
      return null;
    }
    String target = hint.value().toLowerCase(Locale.ROOT);
    return CHROME.containsKey(target) ? target : null;
  }

  private static boolean blank(String text) {
    return text == null || LegacyText.plain(text).isBlank();
  }

  /** The button on the carrier item, keeping its name, lore and count, since the pack draws it. */
  private static MenuButton carried(MenuButton button, PackResources resources) {
    MenuIcon icon = button.icon();
    String name = icon.name() != null ? icon.name() : icon.item() == null ? null : icon.item().displayName();
    List<String> lore = !icon.lore().isEmpty() || icon.item() == null ? icon.lore() : icon.item().lore();
    MenuIcon carried = MenuIcon.of(resources.carrier(), name, lore).withAmount(icon.amount());
    return new MenuButton(carried, button.action(), button.hint(), button.meta());
  }

  private static MenuButton rebound(MenuButton button, MenuSession exposed) {
    MenuAction action = button.action();
    if (action == null) {
      return button;
    }
    MenuAction bound = (MenuClick click) -> action.click(new MenuClick(click.viewer(), exposed, click.slot(), click.kind()));
    return new MenuButton(button.icon(), bound, button.hint(), button.meta());
  }

  public record Skinned(ChestMenu menu, PackSurface surface) {
  }
}
