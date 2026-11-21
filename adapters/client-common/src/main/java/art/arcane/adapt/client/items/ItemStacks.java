package art.arcane.adapt.client.items;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;

/** Item facts the index needs, written once per item data model (components here, NBT tags on 1.20.1). */
final class ItemStacks {
    private ItemStacks() {
    }

    /** What makes a stack an Adapt item: its custom data, else all its components; null for a plain stack. */
    static Object identityKey(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            return data;
        }
        DataComponentPatch patch = stack.getComponentsPatch();
        return patch.isEmpty() ? null : patch;
    }

    static Optional<Item> item(String id) {
        Id parsed = Id.tryParse(id);
        return parsed == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(ModConversions.identifier(parsed));
    }

    static String id(Item item) {
        return ModConversions.id(BuiltInRegistries.ITEM.getKey(item)).toString();
    }
}
