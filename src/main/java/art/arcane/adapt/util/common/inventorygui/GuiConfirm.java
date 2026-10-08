package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class GuiConfirm {
  private GuiConfirm() {
  }

  public static void open(
      Player player,
      String title,
      String message,
      Runnable onConfirm,
      Runnable onCancel
  ) {
    if (player == null) {
      return;
    }

    if (!J.isPrimaryThread()) {
      J.runEntity(player, () -> open(player, title, message, onConfirm, onCancel));
      return;
    }

    UIWindow w = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(w, "confirm");
    w.setViewportHeight(3);

    w.setElement(0, 0, GuiTheme.element("confirm-msg", Material.PAPER, "gui", "confirmation", "message")
        .setName(C.WHITE + (title == null ? AdaptLanguage.text(GuiMessages.CONFIRM) : title))
        .addLore(C.GRAY + (message == null ? AdaptLanguage.text(GuiMessages.APPLY_CHANGE) : message)));

    w.setElement(-2, 1, GuiTheme.element("confirm-yes", Material.LIME_STAINED_GLASS_PANE, "gui", "confirmation", "confirm")
        .setName(C.GREEN + AdaptLanguage.text(GuiMessages.CONFIRM))
        .onLeftClick((e) -> {
          w.close();
          if (onConfirm != null) {
            onConfirm.run();
          }
        }));

    w.setElement(2, 1, GuiTheme.element("confirm-no", Material.RED_STAINED_GLASS_PANE, "gui", "confirmation", "cancel")
        .setName(C.RED + AdaptLanguage.text(GuiMessages.CANCEL))
        .onLeftClick((e) -> {
          w.close();
          if (onCancel != null) {
            onCancel.run();
          }
        }));

    w.setTitle(C.GRAY + AdaptLanguage.text(GuiMessages.CONFIRM));
    w.onClosed((window) -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), w));
    w.open();
    Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
  }
}
