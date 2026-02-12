package com.volmit.adapt.util;

import org.bukkit.Material;

public final class GuiTheme {
    private GuiTheme() {
    }

    public static void apply(Window window, String tag) {
        if (window == null) {
            return;
        }

        window.setResolution(WindowResolution.W9_H6);
        window.setViewportHeight(WindowResolution.W9_H6.getMaxHeight());
        if (tag != null) {
            window.setTag(tag);
        }

        window.setDecorator((w, position, row) -> new UIElement("bg-" + row + "-" + position)
                .setName(" ")
                .setMaterial(new MaterialBlock(background(row, position))));
    }

    public static Material background(int row, int position) {
        if (row == 0) {
            return position % 2 == 0 ? Material.GRAY_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
        }

        if (row == 1) {
            return Material.BLACK_STAINED_GLASS_PANE;
        }

        return position % 2 == 0 ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
    }
}
