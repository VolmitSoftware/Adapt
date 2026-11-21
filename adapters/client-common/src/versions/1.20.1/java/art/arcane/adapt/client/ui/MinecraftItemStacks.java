package art.arcane.adapt.client.ui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Builds the stacks {@link MinecraftUiDraw} draws as icons. */
final class MinecraftItemStacks {
    private static final String CUSTOM_MODEL_DATA = "CustomModelData";
    private static final String ENCHANTMENTS = "Enchantments";

    private MinecraftItemStacks() {
    }

    static ItemStack create(String itemId, int customModelData, boolean glint) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item.get());
        if (customModelData != 0) {
            stack.getOrCreateTag().putInt(CUSTOM_MODEL_DATA, customModelData);
        }
        if (glint) {
            // A non-empty enchantment list is what turns the foil on; the empty entry names no enchantment.
            ListTag enchantments = new ListTag();
            enchantments.add(new CompoundTag());
            stack.getOrCreateTag().put(ENCHANTMENTS, enchantments);
        }
        return stack;
    }
}
