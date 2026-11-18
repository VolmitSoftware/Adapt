package art.arcane.adapt.mod.platform.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Supplier;

/** Item data inside the custom data component, under the key Bukkit uses for item persistent data. */
public final class ItemDataStore implements NbtDataHolder.Store {
    public static final String ROOT = "PublicBukkitValues";
    private final Supplier<ItemStack> stack;

    public ItemDataStore(Supplier<ItemStack> stack) {
        this.stack = stack;
    }

    @Override
    public CompoundTag read() {
        CustomData data = stack.get().get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        return data.copyTag().getCompound(ROOT).orElse(null);
    }

    @Override
    public void write(NbtDataHolder.Edit edit) {
        ItemStack item = stack.get();
        if (item.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, item, (CompoundTag root) -> {
            CompoundTag values = root.getCompoundOrEmpty(ROOT);
            edit.apply(values);
            if (values.isEmpty()) {
                root.remove(ROOT);
            } else {
                root.put(ROOT, values);
            }
        });
    }
}
