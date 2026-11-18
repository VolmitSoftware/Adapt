package art.arcane.adapt.platform.menu;

import art.arcane.adapt.platform.entity.PlayerRef;

/** One click in an open chest menu. Delivered on the viewer's owning thread. */
public record MenuClick(PlayerRef viewer, MenuSession session, int slot, ClickKind kind) {
}
