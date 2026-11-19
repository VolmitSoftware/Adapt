package art.arcane.adapt.mod.platform.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Item data inside the stack's tag, under the key Bukkit uses for item persistent data on this version. */
public final class ItemDataStore implements NbtDataHolder.Store {
    public static final String ROOT = "PublicBukkitValues";
    private final Supplier<ItemStack> stack;

    public ItemDataStore(Supplier<ItemStack> stack) {
        this.stack = stack;
    }

    @Override
    public CompoundTag read() {
        CompoundTag root = stack.get().getTag();
        if (root == null || !root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }
        return root.getCompound(ROOT).copy();
    }

    @Override
    public void write(NbtDataHolder.Edit edit) {
        ItemStack item = stack.get();
        if (item.isEmpty()) {
            return;
        }
        CompoundTag root = item.getOrCreateTag();
        CompoundTag values = root.getCompound(ROOT);
        edit.apply(values);
        if (values.isEmpty()) {
            root.remove(ROOT);
        } else {
            root.put(ROOT, values);
        }
        if (root.isEmpty()) {
            item.setTag(null);
        }
    }
}
