package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.content.adaptation.rift.RiftAnchors;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.volmlib.util.localization.LanguageAudience;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

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
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.uuid())) {
      GuiWindow window = new GuiWindow(player);
      GuiTheme.apply(window, "rift-anchors");
      window.setViewportHeight(1);
      window.setLayout(MenuLayout.LIST);
      if (anchors.isEmpty()) {
        window.setElement(0, 0, new GuiElement("anchor-empty").setMaterial(Id.minecraft("paper"))
            .setName(C.GRAY + AdaptLanguage.text(RiftMessages.ANCHOR_EMPTY)).setRole(MenuRole.INFO));
      }
      int shown = Math.min(GuiLayout.WIDTH, anchors.size());
      for (int index = 0; index < shown; index++) {
        window.setElement(GuiLayout.centeredPosition(index, shown), 0,
            entry(realm, player, window, here, anchors.get(index), index, level));
      }
      window.setTitle(AdaptLanguage.text(RiftMessages.ANCHOR_TITLE));
      window.open();
    }
  }

  private static GuiElement entry(PocketRealm realm, PlayerRef player, GuiWindow window, @Nullable RiftAnchors.Anchor here,
                                  RiftAnchors.Anchor anchor, int index, int level) {
    WorldRef world = anchor.world();
    String place = (world == null ? "?" : world.name()) + " " + anchor.x() + ", " + anchor.y() + ", " + anchor.z();
    String name = anchor.blockless() ? anchor.name()
        : AdaptLanguage.text(RiftMessages.ANCHOR_ENTRY, trusted("number", index + 1));
    GuiElement element = new GuiElement("anchor-" + index)
        .setMaterial(anchor.blockless() ? WAYSTONE : LODESTONE)
        .setName(C.AQUA + name)
        .addLore(C.GRAY + place)
        .setRole(MenuRole.ENTRY)
        .setValue(place);
    if (anchor.equals(here)) {
      return element.setEnchanted(true).addLore(C.GREEN + AdaptLanguage.text(RiftMessages.ANCHOR_HERE));
    }
    if (anchor.blockless()) {
      String remove = AdaptLanguage.text(RiftMessages.ANCHOR_REMOVE);
      element.onShiftRightClick(click -> {
        window.close();
        realm.removeWaystone(player, anchor);
      }).setActionLabel(ClickKind.SHIFT_RIGHT, remove).addLore(C.RED + remove);
    }
    if (!realm.reaches(player, anchor, level)) {
      return element.addLore(C.RED + AdaptLanguage.text(RiftMessages.ANCHOR_OTHER_DIMENSION,
          trusted("level", realm.getConfig().crossDimensionLevel)));
    }
    String travel = AdaptLanguage.text(RiftMessages.ANCHOR_TRAVEL);
    element.onLeftClick(click -> {
      window.close();
      realm.travel(player, anchor);
    }).setActionLabel(ClickKind.LEFT, travel).addLore(C.GREEN + travel);
    if (here == null || anchor.blockless()) {
      return element;
    }
    String open = AdaptLanguage.text(RiftMessages.ANCHOR_OPEN);
    String send = AdaptLanguage.text(RiftMessages.ANCHOR_SEND);
    return element
        .onRightClick(click -> {
          window.close();
          realm.openContainer(player, here, anchor);
        })
        .setActionLabel(ClickKind.RIGHT, open)
        .addLore(C.GREEN + open)
        .onShiftLeftClick(click -> {
          window.close();
          realm.send(player, here, anchor);
        })
        .setActionLabel(ClickKind.SHIFT_LEFT, send)
        .addLore(C.GREEN + send);
  }
}
