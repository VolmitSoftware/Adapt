package com.volmit.adapt.util;

import org.bukkit.Material;

import java.util.List;

public final class GuiEffects {
    private GuiEffects() {
    }

    public static void applyReveal(Window window, List<Placement> placements) {
        if (window == null || placements == null || placements.isEmpty()) {
            return;
        }

        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            window.setElement(p.position(), p.row(), new UIElement("reveal-" + i)
                    .setMaterial(new MaterialBlock(Material.PAPER))
                    .setName(" "));
        }

        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            int delay = Math.max(0, Math.min(8, i / 4));
            J.s(() -> {
                if (window.isVisible()) {
                    window.setElement(p.position(), p.row(), p.element());
                }
            }, delay);
        }
    }

    public record Placement(int position, int row, Element element) {
    }
}
