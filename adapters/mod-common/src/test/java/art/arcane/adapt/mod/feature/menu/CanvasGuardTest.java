package art.arcane.adapt.mod.feature.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasGuardTest {
    @Test
    void inventoryMenuRowsAndHotbarAreHidden() {
        for (int slot = 9; slot <= 44; slot++) {
            assertTrue(CanvasGuard.hidesSlot(0, slot), "slot " + slot);
        }
        for (int slot = 0; slot < 9; slot++) {
            assertFalse(CanvasGuard.hidesSlot(0, slot), "slot " + slot);
        }
        assertFalse(CanvasGuard.hidesSlot(0, 45));
    }

    @Test
    void playerInventorySlotsAreHidden() {
        assertTrue(CanvasGuard.hidesSlot(-2, 0));
        assertTrue(CanvasGuard.hidesSlot(-2, 35));
        assertFalse(CanvasGuard.hidesSlot(-2, 36));
        assertFalse(CanvasGuard.hidesSlot(-2, 40));
        assertTrue(CanvasGuard.hidesInventorySlot(0));
        assertTrue(CanvasGuard.hidesInventorySlot(35));
        assertFalse(CanvasGuard.hidesInventorySlot(36));
        assertFalse(CanvasGuard.hidesInventorySlot(-1));
    }

    @Test
    void otherWindowsAndTheCursorPass() {
        assertFalse(CanvasGuard.hidesSlot(3, 20));
        assertFalse(CanvasGuard.hidesSlot(-1, -1));
        assertTrue(CanvasGuard.hidesContent(0));
        assertFalse(CanvasGuard.hidesContent(3));
    }
}
