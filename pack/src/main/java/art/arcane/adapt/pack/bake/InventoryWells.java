package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;

/**
 * The player's inventory slots drawn as wells on a top-rows-only page, where those rows show the real inventory: the
 * tome's tile piece nine-sliced to 18x18 round each slot, and to {@link #HOTBAR_HEIGHT} on the hotbar row so the foot
 * scene below it stays clear.
 */
public record InventoryWells(Raster well, Raster hotbarWell) {
    static final int WELL = 18;
    static final int HOTBAR_HEIGHT = 14;
    static final int FIRST_HOTBAR_SLOT = 81;

    public static InventoryWells of(byte[] tilePng) {
        return new InventoryWells(KitBake.nine(tilePng, WELL, WELL, 4), KitBake.nine(tilePng, WELL, HOTBAR_HEIGHT, 4));
    }

    /** A copy of {@code page}, whose top left is container pixel ({@code left}, {@code top}), with every well drawn over it. */
    public Raster over(Raster page, int left, int top) {
        Raster out = page.copy();
        for (int slot = KitBake.FIRST_INVENTORY_SLOT; slot < SlotGrid.SLOTS; slot++) {
            out.paste(slot >= FIRST_HOTBAR_SLOT ? hotbarWell : well, SlotGrid.x(slot) - 1 - left, SlotGrid.y(slot) - 1 - top);
        }
        return out;
    }
}
