package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.layout.PackGrid;
import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.surface.PackSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotGridTest {
    @Test
    void slotsSitWhereTheClientChestMenuPutsThem() {
        assertEquals(8, SlotGrid.x(0));
        assertEquals(18, SlotGrid.y(0));
        assertEquals(152, SlotGrid.x(53));
        assertEquals(108, SlotGrid.y(53));
        assertEquals(139, SlotGrid.y(54));
        assertEquals(175, SlotGrid.y(80));
        assertEquals(197, SlotGrid.y(81));
        assertEquals(152, SlotGrid.x(89));
    }

    @Test
    void theBakeAndTheRendererShareOneGrid() {
        PackGrid grid = PackLayoutReader.builtIn().grid();
        for (int slot = 0; slot < SlotGrid.SLOTS; slot++) {
            PackSlot packSlot = slot < KitBake.FIRST_INVENTORY_SLOT ? PackSlot.top(slot) : PackSlot.lower(slot - KitBake.FIRST_INVENTORY_SLOT);
            assertEquals(SlotGrid.x(slot), grid.x(packSlot), "x of slot " + slot);
            assertEquals(SlotGrid.y(slot), grid.y(packSlot), "y of slot " + slot);
        }
    }
}
