package art.arcane.adapt.client.items;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Item facts the index needs, written once per item data model (components, here NBT tags on 1.20.1). */
final class ItemStacks {
    private static final String DISPLAY = "display";

    private ItemStacks() {
    }

    /** What makes a stack an Adapt item: its tag without name and lore, else its whole tag; null for a plain stack. */
    static Object identityKey(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) {
            return null;
        }
        CompoundTag data = tag.copy();
        data.remove(DISPLAY);
        return data.isEmpty() ? tag.copy() : data;
    }

    static Optional<Item> item(String id) {
        Id parsed = Id.tryParse(id);
        return parsed == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(ModConversions.identifier(parsed));
    }

    static String id(Item item) {
        return ModConversions.id(BuiltInRegistries.ITEM.getKey(item)).toString();
    }
}
