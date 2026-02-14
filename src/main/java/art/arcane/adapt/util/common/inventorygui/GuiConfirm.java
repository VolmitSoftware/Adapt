package art.arcane.adapt.util.common.inventorygui;

import art.arcane.adapt.Adapt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.math.MaterialBlock;
import art.arcane.adapt.util.common.scheduling.J;

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

        if (!Bukkit.isPrimaryThread()) {
            J.s(() -> open(player, title, message, onConfirm, onCancel));
            return;
        }

        Window w = new UIWindow(player);
        GuiTheme.apply(w, "confirm");
        w.setViewportHeight(3);

        w.setElement(0, 0, new UIElement("confirm-msg")
                .setMaterial(new MaterialBlock(Material.PAPER))
                .setName(C.WHITE + (title == null ? "Confirm" : title))
                .addLore(C.GRAY + (message == null ? "Apply this change?" : message)));

        w.setElement(-2, 1, new UIElement("confirm-yes")
                .setMaterial(new MaterialBlock(Material.LIME_STAINED_GLASS_PANE))
                .setName(C.GREEN + "Confirm")
                .onLeftClick((e) -> {
                    w.close();
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                }));

        w.setElement(2, 1, new UIElement("confirm-no")
                .setMaterial(new MaterialBlock(Material.RED_STAINED_GLASS_PANE))
                .setName(C.RED + "Cancel")
                .onLeftClick((e) -> {
                    w.close();
                    if (onCancel != null) {
                        onCancel.run();
                    }
                }));

        w.setTitle(C.GRAY + "Confirm");
        w.onClosed((window) -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString()));
        w.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }
}
