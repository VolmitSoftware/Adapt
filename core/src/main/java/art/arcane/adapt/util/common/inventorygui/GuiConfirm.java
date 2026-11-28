package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
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
    GuiFrame.apply(w, "confirm", 1);
    w.setLayout(MenuLayout.LIST);

    GuiFrame.subject(w, GuiTheme.element("confirm-msg", Id.minecraft("paper"), "gui", "confirmation", "message")
        .setName(C.WHITE + (title == null ? AdaptLanguage.text(GuiMessages.CONFIRM) : title))
        .addLore(C.GRAY + (message == null ? AdaptLanguage.text(GuiMessages.APPLY_CHANGE) : message))
        .setRole(MenuRole.HEADER));

    GuiFrame.body(w, 0, 2, GuiTheme.element("confirm-yes", Id.minecraft("lime_stained_glass_pane"), "gui", "confirmation", "confirm")
        .setName(C.GREEN + AdaptLanguage.text(GuiMessages.CONFIRM))
        .setHint(MenuHint.destructive(AdaptLanguage.text(GuiMessages.CONFIRM)))
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick e) -> {
          w.close();
          if (onConfirm != null) {
            onConfirm.run();
          }
        }));

    GuiFrame.body(w, 0, 6, GuiTheme.element("confirm-no", Id.minecraft("red_stained_glass_pane"), "gui", "confirmation", "cancel")
        .setName(C.RED + AdaptLanguage.text(GuiMessages.CANCEL))
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick e) -> {
          w.close();
          if (onCancel != null) {
            onCancel.run();
          }
        }));

    w.setTitle(AdaptLanguage.text(GuiMessages.CONFIRM));
    w.open();
  }
}
