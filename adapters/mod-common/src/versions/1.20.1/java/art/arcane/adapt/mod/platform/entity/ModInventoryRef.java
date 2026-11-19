package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Slots of a vanilla container. Slot numbers are the container's own indices. */
public class ModInventoryRef implements InventoryRef {
    private final Supplier<Container> container;

    public ModInventoryRef(Supplier<Container> container) {
        this.container = container;
    }

    public Container container() {
        return container.get();
    }

    /** Called after every write through this ref. */
    protected void changed(Container target) {
        target.setChanged();
    }

    /** Slots that bulk operations (add, remove, contains, first) cover. */
    protected int storageSize() {
        return size();
    }

    @Override
    public int size() {
        return container().getContainerSize();
    }

    @Override
    public int maxStackSize() {
        return container().getMaxStackSize();
    }

    @Override
    public ItemRef item(int slot) {
        Container target = container();
        if (slot < 0 || slot >= target.getContainerSize()) {
            return ModRefs.item(ItemStack.EMPTY);
        }
        return ModRefs.item(target.getItem(slot));
    }

    @Override
    public void setItem(int slot, ItemRef item) {
        Container target = container();
        if (slot < 0 || slot >= target.getContainerSize()) {
            return;
        }
        target.setItem(slot, item == null ? ItemStack.EMPTY : ModRefs.item(item).copy());
        changed(target);
    }

    @Override
    public List<ItemRef> addItem(ItemRef item) {
        ItemStack remaining = ModRefs.item(item).copy();
        Container target = container();
        int slots = storageSize();
        for (int slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
            ItemStack present = target.getItem(slot);
            if (!present.isEmpty() && ItemStack.isSameItemSameTags(present, remaining)) {
                int limit = Math.min(target.getMaxStackSize(), present.getMaxStackSize());
                int moved = Math.min(limit - present.getCount(), remaining.getCount());
                if (moved > 0) {
                    present.grow(moved);
                    remaining.shrink(moved);
                }
            }
        }
        for (int slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
            if (target.getItem(slot).isEmpty() && target.canPlaceItem(slot, remaining)) {
                int limit = Math.min(target.getMaxStackSize(), remaining.getMaxStackSize());
                target.setItem(slot, remaining.split(Math.min(limit, remaining.getCount())));
            }
        }
        changed(target);
        return remaining.isEmpty() ? List.of() : List.of(ModRefs.item(remaining));
    }

    @Override
    public int removeItem(Id type, int amount) {
        Item item = item(type);
        if (item == null || amount <= 0) {
            return 0;
        }
        Container target = container();
        int removed = 0;
        int slots = storageSize();
        for (int slot = 0; slot < slots && removed < amount; slot++) {
            ItemStack present = target.getItem(slot);
            if (present.is(item)) {
                int taken = Math.min(amount - removed, present.getCount());
                present.shrink(taken);
                removed += taken;
                if (present.isEmpty()) {
                    target.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
        if (removed > 0) {
            changed(target);
        }
        return removed;
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
        Container target = container();
        int found = 0;
        int slots = storageSize();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack present = target.getItem(slot);
            if (ItemStack.isSameItemSameTags(present, wanted)) {
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
        Container target = container();
        int removed = 0;
        int slots = storageSize();
        for (int slot = 0; slot < slots && removed < amount; slot++) {
            ItemStack present = target.getItem(slot);
            if (ItemStack.isSameItemSameTags(present, wanted)) {
                int taken = Math.min(amount - removed, present.getCount());
                present.shrink(taken);
                removed += taken;
                if (present.isEmpty()) {
                    target.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
        if (removed > 0) {
            changed(target);
        }
        return removed;
    }

    @Override
    public boolean contains(Id type, int amount) {
        Item item = item(type);
        if (item == null) {
            return false;
        }
        Container target = container();
        int found = 0;
        int slots = storageSize();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack present = target.getItem(slot);
            if (present.is(item)) {
                found += present.getCount();
                if (found >= amount) {
                    return true;
                }
            }
        }
        return amount <= 0;
    }

    @Override
    public int first(Id type) {
        Item item = item(type);
        if (item == null) {
            return -1;
        }
        Container target = container();
        int slots = storageSize();
        for (int slot = 0; slot < slots; slot++) {
            if (target.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public List<ItemRef> contents() {
        Container target = container();
        int size = target.getContainerSize();
        List<ItemRef> out = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            out.add(ModRefs.item(target.getItem(slot).copy()));
        }
        return out;
    }

    @Override
    public void clear(int slot) {
        Container target = container();
        if (slot >= 0 && slot < target.getContainerSize()) {
            target.setItem(slot, ItemStack.EMPTY);
            changed(target);
        }
    }

    private static Item item(Id type) {
        return BuiltInRegistries.ITEM.getOptional(ModConversions.identifier(type)).orElse(null);
    }
}
