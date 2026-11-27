package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.rift.RiftAnchors;
import art.arcane.adapt.content.menu.MenuEntry;
import art.arcane.adapt.content.menu.MenuSheet;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.List;


/**
 * The owner's anchors in one row. From an anchor ({@code here}), each other anchor travels, opens its container or
 * takes this anchor's items; from a Rift Eye they only travel.
 */
final class RiftAnchorMenu {
  private static final Id LODESTONE = Id.minecraft("lodestone");
  private static final Id WAYSTONE = Id.minecraft("compass");

  private RiftAnchorMenu() {
  }

  static void open(PocketRealm realm, PlayerRef player, @Nullable RiftAnchors.Anchor here, List<RiftAnchors.Anchor> anchors,
                   int level) {
    MenuSheet sheet = MenuSheet.themed(1).layout(MenuLayout.LIST);
    if (anchors.isEmpty()) {
      sheet.set(0, 0, MenuEntry.of(Id.minecraft("paper"))
          .name(TextFormat.GRAY + Text.key("adapt.tool.rift.anchor.empty").resolve(player)).role(MenuRole.INFO));
    }
    int shown = Math.min(MenuSheet.WIDTH, anchors.size());
    for (int index = 0; index < shown; index++) {
      sheet.set(MenuSheet.centeredPosition(index, shown), 0, entry(realm, player, here, anchors.get(index), index, level));
    }
    sheet.title(Text.key("adapt.tool.rift.anchor.title").resolve(player)).open(player);
  }

  private static MenuEntry entry(PocketRealm realm, PlayerRef player, @Nullable RiftAnchors.Anchor here,
                                 RiftAnchors.Anchor anchor, int index, int level) {
    WorldRef world = anchor.world();
    String place = (world == null ? "?" : world.name()) + " " + anchor.x() + ", " + anchor.y() + ", " + anchor.z();
    String name = anchor.blockless() ? anchor.name()
        : Text.key("adapt.tool.rift.anchor.entry").withMarkup("number", String.valueOf(index + 1)).resolve(player);
    MenuEntry element = MenuEntry.of(anchor.blockless() ? WAYSTONE : LODESTONE)
        .name(TextFormat.AQUA + name)
        .lore(TextFormat.GRAY + place)
        .role(MenuRole.ENTRY)
        .value(place);
    if (anchor.equals(here)) {
      return element.glint(true).lore(TextFormat.GREEN + Text.key("adapt.tool.rift.anchor.here").resolve(player));
    }
    if (anchor.blockless()) {
      String remove = Text.key("adapt.tool.rift.anchor.remove").resolve(player);
      element.on(ClickKind.SHIFT_RIGHT, (MenuClick click) -> {
        MenuSheet.close(click);
        realm.removeWaystone(player, anchor);
      }).label(ClickKind.SHIFT_RIGHT, remove).lore(TextFormat.RED + remove);
    }
    if (!realm.reaches(player, anchor, level)) {
      return element.lore(TextFormat.RED + Text.key("adapt.tool.rift.anchor.other_dimension").withMarkup("level", String.valueOf(realm.getConfig().crossDimensionLevel)).resolve(player));
    }
    String travel = Text.key("adapt.tool.rift.anchor.travel").resolve(player);
    element.on(ClickKind.LEFT, (MenuClick click) -> {
      MenuSheet.close(click);
      realm.travel(player, anchor);
    }).label(ClickKind.LEFT, travel).lore(TextFormat.GREEN + travel);
    if (here == null || anchor.blockless()) {
      return element;
    }
    String open = Text.key("adapt.tool.rift.anchor.open").resolve(player);
    String send = Text.key("adapt.tool.rift.anchor.send").resolve(player);
    return element
        .on(ClickKind.RIGHT, (MenuClick click) -> {
          MenuSheet.close(click);
          realm.openContainer(player, here, anchor);
        })
        .label(ClickKind.RIGHT, open)
        .lore(TextFormat.GREEN + open)
        .on(ClickKind.SHIFT_LEFT, (MenuClick click) -> {
          MenuSheet.close(click);
          realm.send(player, here, anchor);
        })
        .label(ClickKind.SHIFT_LEFT, send)
        .lore(TextFormat.GREEN + send);
  }
}
