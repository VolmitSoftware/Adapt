package art.arcane.adapt.client.ui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;
import java.util.Optional;

/** Builds the stacks {@link MinecraftUiDraw} draws as icons. */
final class MinecraftItemStacks {
    private MinecraftItemStacks() {
    }

    static ItemStack create(String itemId, String model, int customModelData, boolean glint) {
        Identifier id = Identifier.tryParse(itemId);
        Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item.get());
        Identifier modelId = model == null ? null : Identifier.tryParse(model);
        if (modelId != null) {
            stack.set(DataComponents.ITEM_MODEL, modelId);
        }
        if (customModelData != 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) customModelData), List.of(), List.of(), List.of()));
        }
        if (glint) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }
}
