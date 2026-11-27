package art.arcane.adapt.pack.bake;

/**
 * The vanilla 9x6 chest's slots in container pixels, where each slot's item draws its 16x16 square: six chest rows,
 * three inventory rows and the hotbar, numbered as the menu numbers them (0 to 53 chest, 54 to 80 inventory, 81 to 89
 * hotbar).
 */
public final class SlotGrid {
    public static final int CONTAINER_WIDTH = 176;
    public static final int CONTAINER_HEIGHT = 222;
    public static final int SLOTS = 90;
    public static final int COLUMNS = 9;
    /** Where the container title's text starts. */
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 6;
    static final int PITCH = 18;
    static final int CHEST_ROWS = 6;
    static final int CHEST_Y = 18;
    /** The client's chest menu puts the inventory 13 below the last chest row and the hotbar 58 below the inventory. */
    static final int INVENTORY_Y = CHEST_Y + PITCH * CHEST_ROWS + 13;
    static final int HOTBAR_Y = INVENTORY_Y + 58;

    private SlotGrid() {
    }

    public static int x(int slot) {
        check(slot);
        return 8 + PITCH * (slot % COLUMNS);
    }

    public static int y(int slot) {
        check(slot);
        int row = slot / COLUMNS;
        if (row < CHEST_ROWS) {
            return CHEST_Y + PITCH * row;
        }
        if (row < CHEST_ROWS + 3) {
            return INVENTORY_Y + PITCH * (row - CHEST_ROWS);
        }
        return HOTBAR_Y;
    }

    private static void check(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            throw new IllegalArgumentException("no slot " + slot);
        }
    }
}
