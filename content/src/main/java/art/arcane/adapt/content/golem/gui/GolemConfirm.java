package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;

public final class GolemConfirm {
  private GolemConfirm() {
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

    if (!AdaptApi.scheduler().isTickThread()) {
      AdaptApi.scheduler().runEntity(player, () -> open(player, title, message, onConfirm, onCancel));
      return;
    }

    GolemWindow w = new GolemWindow(player);
    GolemTheme.apply(w, "golemancy-confirm");
    w.setViewportHeight(3);
    w.setLayout(MenuLayout.LIST);

    w.setElement(0, 0, GolemTheme.element("confirm-msg", Id.minecraft("paper"), "gui", "confirmation", "message")
        .setName(TextFormat.WHITE + (title == null ? Text.key("adapt.gui.common.confirm").resolve() : title))
        .addLore(TextFormat.GRAY + (message == null ? Text.key("adapt.gui.common.apply_change").resolve() : message))
        .setRole(MenuRole.HEADER));

    w.setElement(-2, 1, GolemTheme.element("confirm-yes", Id.minecraft("lime_stained_glass_pane"), "gui", "confirmation", "confirm")
        .setName(TextFormat.GREEN + Text.key("adapt.gui.common.confirm").resolve())
        .setHint(MenuHint.destructive(Text.key("adapt.gui.common.confirm").resolve()))
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick e) -> {
          w.close();
          if (onConfirm != null) {
            onConfirm.run();
          }
        }));

    w.setElement(2, 1, GolemTheme.element("confirm-no", Id.minecraft("red_stained_glass_pane"), "gui", "confirmation", "cancel")
        .setName(TextFormat.RED + Text.key("adapt.gui.common.cancel").resolve())
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick e) -> {
          w.close();
          if (onCancel != null) {
            onCancel.run();
          }
        }));

    w.setTitle(TextFormat.GRAY + Text.key("adapt.gui.common.confirm").resolve());
    w.open();
  }
}
