package com.volmit.adapt.util;

import java.util.List;

public final class GuiEffects {
    private GuiEffects() {
    }

    public static void applyReveal(Window window, List<Placement> placements) {
        if (window == null || placements == null || placements.isEmpty()) {
            return;
        }

        for (Placement placement : placements) {
            if (placement == null || placement.element() == null) {
                continue;
            }
            window.setElement(placement.position(), placement.row(), placement.element());
        }
    }

    public record Placement(int position, int row, Element element) {
    }
}
