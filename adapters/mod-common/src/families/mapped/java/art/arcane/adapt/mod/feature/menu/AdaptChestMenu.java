package art.arcane.adapt.mod.feature.menu;

import art.arcane.adapt.platform.menu.ClickKind;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side chest window that never lets an item move. Every click is refused before vanilla handles it,
 * then the whole window and cursor are resent so a client that predicted a pickup is corrected.
 */
public final class AdaptChestMenu extends ChestMenu {
    @FunctionalInterface
    interface Clicks {
        void clicked(AdaptChestMenu menu, int slot, ClickKind kind);
    }

    @FunctionalInterface
    interface Closes {
        void closed(AdaptChestMenu menu);
    }

    private final int size;
    private final Clicks clicks;
    private final Closes closes;
    private boolean replaced;
    private boolean freePlayerInventory;

    private AdaptChestMenu(MenuType<ChestMenu> type, int id, Inventory inventory, SimpleContainer container, int rows, Clicks clicks, Closes closes) {
        super(type, id, inventory, container, rows);
        this.size = rows * 9;
        this.clicks = clicks;
        this.closes = closes;
    }

    static AdaptChestMenu create(int id, Inventory inventory, int rows, Clicks clicks, Closes closes) {
        return new AdaptChestMenu(type(rows), id, inventory, new SimpleContainer(rows * 9), rows, clicks, closes);
    }

    static MenuType<ChestMenu> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    int windowSize() {
        return size;
    }

    void show(int slot, ItemStack stack) {
        getContainer().setItem(slot, stack);
    }

    void setFreePlayerInventory(boolean free) {
        freePlayerInventory = free;
    }

    /** True when the viewer may move items in their own inventory while the menu is open. */
    public boolean freePlayerInventory() {
        return freePlayerInventory;
    }

    void markReplaced() {
        replaced = true;
    }

    @Override
    public void clicked(int slot, int button, ClickType input, Player player) {
        if (freePlayerInventory && allowsPlayerInventoryClick(slot, size, slots.size(), button, input)) {
            super.clicked(slot, button, input, player);
            return;
        }
        ClickKind kind = kind(slot, size, button, input);
        sendAllDataToRemote();
        if (kind != null) {
            clicks.clicked(this, slot, kind);
        }
    }

    /** A plain pick-up, place or swap with the cursor on one of the player's own slots below the window. */
    static boolean allowsPlayerInventoryClick(int slot, int size, int totalSlots, int button, ClickType input) {
        return slot >= size && slot < totalSlots && input == ClickType.PICKUP && (button == 0 || button == 1);
    }

    /** Maps a click on the window's own slots; drags and clicks outside or in the player's rows map to null. */
    static ClickKind kind(int slot, int size, int button, ClickType input) {
        if (slot < 0 || slot >= size) {
            return null;
        }
        return switch (input) {
            case PICKUP -> button == 0 ? ClickKind.LEFT : button == 1 ? ClickKind.RIGHT : null;
            case QUICK_MOVE -> button == 0 ? ClickKind.SHIFT_LEFT : button == 1 ? ClickKind.SHIFT_RIGHT : null;
            case SWAP -> button == 40 ? ClickKind.SWAP_OFFHAND : button >= 0 && button <= 8 ? ClickKind.NUMBER_KEY : null;
            case CLONE -> ClickKind.MIDDLE;
            case THROW -> button == 0 ? ClickKind.DROP : ClickKind.CONTROL_DROP;
            case PICKUP_ALL -> ClickKind.DOUBLE_CLICK;
            default -> null;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!replaced) {
            replaced = true;
            closes.closed(this);
        }
    }
}
