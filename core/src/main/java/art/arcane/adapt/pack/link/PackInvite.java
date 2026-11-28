package art.arcane.adapt.pack.link;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.misc.MenuSounds;
import art.arcane.volmlib.util.localization.MessageArgs;

import java.util.ArrayList;
import java.util.List;

/**
 * The Get the book menus item of the chest menus, for a player who could have the pack and does not. A click offers
 * the pack again, or prints how to let the client accept server packs when it refuses them on this connection.
 */
public final class PackInvite {
  public static final Id ICON = Id.minecraft("writable_book");

  private PackInvite() {
  }

  /** The chest menus' footer element for {@code viewer} (see {@link #item(PlayerRef)}), or null. */
  public static GuiElement element(PlayerRef viewer) {
    Item item = item(viewer);
    if (item == null) {
      return null;
    }
    List<String> lore = new ArrayList<>(item.lore().size());
    for (String line : item.lore()) {
      lore.add(TextFormat.GRAY + line);
    }
    return new GuiElement("pack-invite").setMaterial(item.icon()).setName(item.name()).addLore(lore)
        .onLeftClick((MenuClick click) -> {
          MenuSounds.play(viewer, MenuSounds.Action.OPEN);
          clicked(viewer);
        });
  }

  /** The item for {@code viewer} while {@code pack.menuItem} is on and they stand to get the pack, else null. */
  public static Item item(PlayerRef viewer) {
    return item(viewer, PackDelivery.standing(viewer), AdaptConfig.get().getPack().isMenuItem());
  }

  static Item item(PlayerRef viewer, PackOffers.Standing standing, boolean shown) {
    if (viewer == null || standing == PackOffers.Standing.NONE || !shown) {
      return null;
    }
    return new Item(ICON, AdaptLanguage.text(viewer.uuid(), GuiMessages.PACK_GET_BOOK, MessageArgs.empty()),
        PackPitch.lore(viewer.uuid(), standing == PackOffers.Standing.REFUSING));
  }

  /** The item's click: offers the pack again, or prints the steps when the client refuses server packs here. */
  public static void clicked(PlayerRef viewer) {
    PackDelivery delivery = PackDelivery.current();
    if (delivery == null || viewer == null) {
      return;
    }
    if (PackDelivery.standing(viewer) == PackOffers.Standing.REFUSING
        || delivery.request(viewer) == PackOffers.Result.CLIENT_DECLINED) {
      viewer.sendMessage(PackPitch.steps(viewer.uuid()));
    }
  }

  /** What the item shows. */
  public record Item(Id icon, String name, List<String> lore) {
  }
}
