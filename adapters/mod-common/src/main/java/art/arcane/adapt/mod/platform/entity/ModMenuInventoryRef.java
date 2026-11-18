package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Top slots of an open menu, in raw-slot order: every leading slot that does not belong to the player's inventory.
 * Unlike a single container this covers result slots too (anvil and grindstone output). Equal while it is the same menu.
 */
public final class ModMenuInventoryRef implements InventoryRef {
    private final AbstractContainerMenu menu;

    public ModMenuInventoryRef(AbstractContainerMenu menu) {
        this.menu = menu;
    }

    private List<Slot> slots() {
        List<Slot> top = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                break;
            }
            top.add(slot);
        }
        return top;
    }

    @Override
    public int size() {
        return slots().size();
    }

    @Override
    public int maxStackSize() {
        List<Slot> top = slots();
        return top.isEmpty() ? Item.ABSOLUTE_MAX_STACK_SIZE : top.getFirst().getMaxStackSize();
    }

    @Override
    public ItemRef item(int slot) {
        List<Slot> top = slots();
        if (slot < 0 || slot >= top.size()) {
            return ModRefs.item(ItemStack.EMPTY);
        }
        return ModRefs.item(top.get(slot).getItem());
    }

    @Override
    public void setItem(int slot, ItemRef item) {
        List<Slot> top = slots();
        if (slot < 0 || slot >= top.size()) {
            return;
        }
        top.get(slot).set(item == null ? ItemStack.EMPTY : ModRefs.item(item).copy());
    }

    @Override
    public List<ItemRef> addItem(ItemRef item) {
        ItemStack remaining = ModRefs.item(item).copy();
        List<Slot> top = slots();
        for (Slot slot : top) {
            if (remaining.isEmpty()) {
                break;
            }
            ItemStack present = slot.getItem();
            if (!present.isEmpty() && ItemStack.isSameItemSameComponents(present, remaining)) {
                int moved = Math.min(slot.getMaxStackSize(present) - present.getCount(), remaining.getCount());
                if (moved > 0) {
                    present.grow(moved);
                    remaining.shrink(moved);
                    slot.setChanged();
                }
            }
        }
        for (Slot slot : top) {
            if (remaining.isEmpty()) {
                break;
            }
            if (!slot.hasItem() && slot.mayPlace(remaining)) {
                slot.set(remaining.split(Math.min(slot.getMaxStackSize(remaining), remaining.getCount())));
            }
        }
        return remaining.isEmpty() ? List.of() : List.of(ModRefs.item(remaining));
    }

    @Override
    public int removeItem(Id type, int amount) {
        Item wanted = item(type);
        if (wanted == null || amount <= 0) {
            return 0;
        }
        int removed = 0;
        for (Slot slot : slots()) {
            if (removed >= amount) {
                break;
            }
            ItemStack present = slot.getItem();
            if (present.is(wanted)) {
                int taken = Math.min(amount - removed, present.getCount());
                slot.set(present.copyWithCount(present.getCount() - taken));
                removed += taken;
            }
        }
        return removed;
    }

    @Override
    public boolean contains(Id type, int amount) {
        Item wanted = item(type);
        if (wanted == null) {
            return false;
        }
        int found = 0;
        for (Slot slot : slots()) {
            ItemStack present = slot.getItem();
            if (present.is(wanted)) {
                found += present.getCount();
                if (found >= amount) {
                    return true;
                }
            }
        }
        return amount <= 0;
    }

    @Override
    public boolean containsAtLeast(ItemRef item, int amount) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (amount <= 0) {
            return true;
        }
        ItemStack wanted = ModRefs.item(item);
        int found = 0;
        for (Slot slot : slots()) {
            ItemStack present = slot.getItem();
            if (ItemStack.isSameItemSameComponents(present, wanted)) {
                found += present.getCount();
                if (found >= amount) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int removeSimilar(ItemRef item, int amount) {
        if (item == null || item.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemStack wanted = ModRefs.item(item);
        int removed = 0;
        for (Slot slot : slots()) {
            if (removed >= amount) {
                break;
            }
            ItemStack present = slot.getItem();
            if (ItemStack.isSameItemSameComponents(present, wanted)) {
                int taken = Math.min(amount - removed, present.getCount());
                slot.set(present.copyWithCount(present.getCount() - taken));
                removed += taken;
            }
        }
        return removed;
    }

    @Override
    public int first(Id type) {
        Item wanted = item(type);
        if (wanted == null) {
            return -1;
        }
        List<Slot> top = slots();
        for (int index = 0; index < top.size(); index++) {
            if (top.get(index).getItem().is(wanted)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    public List<ItemRef> contents() {
        List<Slot> top = slots();
        List<ItemRef> out = new ArrayList<>(top.size());
        for (Slot slot : top) {
            out.add(ModRefs.item(slot.getItem().copy()));
        }
        return out;
    }

    @Override
    public void clear(int slot) {
        setItem(slot, null);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModMenuInventoryRef that && menu == that.menu;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(menu);
    }

    private static Item item(Id type) {
        return BuiltInRegistries.ITEM.getOptional(ModConversions.identifier(type)).orElse(null);
    }
}
