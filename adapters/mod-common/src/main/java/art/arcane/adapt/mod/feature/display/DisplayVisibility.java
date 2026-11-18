package art.arcane.adapt.mod.feature.display;

import art.arcane.adapt.mod.feature.viewer.ModViewerHost;
import art.arcane.adapt.mod.feature.viewer.ViewerStates;
import net.minecraft.world.entity.Entity;

/** Visible-by-default flag of display entities, enforced per viewer by the viewer host's tracker hook. */
final class DisplayVisibility {
    private DisplayVisibility() {
    }

    static boolean isVisibleByDefault(Entity entity) {
        return !ViewerStates.isHiddenByDefault(entity.getId());
    }

    static void setVisibleByDefault(Entity entity, boolean visible) {
        if (isVisibleByDefault(entity) == visible) {
            return;
        }
        ViewerStates.setHiddenByDefault(entity.getId(), !visible);
        ModViewerHost.refreshAll(entity);
    }
}
