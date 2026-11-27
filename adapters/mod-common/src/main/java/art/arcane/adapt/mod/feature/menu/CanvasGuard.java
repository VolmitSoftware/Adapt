package art.arcane.adapt.mod.feature.menu;

/**
 * Which real-inventory updates a client must not receive while a pack canvas covers the player slots: the client
 * would draw them over the stand-ins. Closing the canvas resends the whole inventory.
 */
final class CanvasGuard {
    static final int INVENTORY_MENU = 0;
    static final int PLAYER_INVENTORY = -2;

    private CanvasGuard() {
    }

    /** A slot update of {@code containerId}: the inventory menu's main and hotbar rows, or any player inventory slot. */
    static boolean hidesSlot(int containerId, int slot) {
        if (containerId == INVENTORY_MENU) {
            return slot >= 9 && slot <= 44;
        }
        return containerId == PLAYER_INVENTORY && hidesInventorySlot(slot);
    }

    /** A direct player inventory update ({@code ClientboundSetPlayerInventoryPacket} slot numbering). */
    static boolean hidesInventorySlot(int slot) {
        return slot >= 0 && slot < 36;
    }

    /** A full content update of {@code containerId}. */
    static boolean hidesContent(int containerId) {
        return containerId == INVENTORY_MENU;
    }
}
