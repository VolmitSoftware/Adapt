package art.arcane.adapt.platform.menu;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * One click in an open chest menu. Delivered on the viewer's owning thread.
 *
 * @param viewer the player who clicked
 * @param session the menu session the click belongs to
 * @param slot the clicked slot, from 0
 * @param kind how they clicked
 */
@ApiStatus.AvailableSince("1.0.0")
public record MenuClick(PlayerRef viewer, MenuSession session, int slot, ClickKind kind) {
}
