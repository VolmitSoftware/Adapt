package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;

public final class GuiConfirm {
  private GuiConfirm() {
  }

  public static void open(
      PlayerRef player,
      String title,
      String message,
      Runnable onConfirm,
      Runnable onCancel
  ) {
    if (player == null) {
      return;
    }

    if (!Tasks.isPrimaryThread()) {
      Tasks.runEntity(player, () -> open(player, title, message, onConfirm, onCancel));
      return;
    }

    GuiWindow w = new GuiWindow(player);
    GuiTheme.apply(w, "golemancy-confirm");
    w.setViewportHeight(3);

    w.setElement(0, 0, GuiTheme.element("confirm-msg", Id.minecraft("paper"), "gui", "confirmation", "message")
        .setName(C.WHITE + (title == null ? AdaptLanguage.text(GuiMessages.CONFIRM) : title))
        .addLore(C.GRAY + (message == null ? AdaptLanguage.text(GuiMessages.APPLY_CHANGE) : message)));

    w.setElement(-2, 1, GuiTheme.element("confirm-yes", Id.minecraft("lime_stained_glass_pane"), "gui", "confirmation", "confirm")
        .setName(C.GREEN + AdaptLanguage.text(GuiMessages.CONFIRM))
        .onLeftClick((MenuClick e) -> {
          w.close();
          if (onConfirm != null) {
            onConfirm.run();
          }
        }));

    w.setElement(2, 1, GuiTheme.element("confirm-no", Id.minecraft("red_stained_glass_pane"), "gui", "confirmation", "cancel")
        .setName(C.RED + AdaptLanguage.text(GuiMessages.CANCEL))
        .onLeftClick((MenuClick e) -> {
          w.close();
          if (onCancel != null) {
            onCancel.run();
          }
        }));

    w.setTitle(C.GRAY + AdaptLanguage.text(GuiMessages.CONFIRM));
    w.open();
  }
}
