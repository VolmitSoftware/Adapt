package art.arcane.adapt.client.ui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.Optional;

/** Builds the stacks {@link MinecraftUiDraw} draws as icons. */
final class MinecraftItemStacks {
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
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(customModelData));
        }
        if (glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }
}
