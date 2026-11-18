package art.arcane.adapt.mod.feature.menu;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.platform.menu.MenuIcon;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

final class MenuIcons {
    private static final Style PLAIN = Style.EMPTY.withItalic(false);

    private MenuIcons() {
    }

    static ItemStack stack(MenuIcon icon) {
        if (icon.item() != null) {
            ItemStack prepared = ModRefs.item(icon.item());
            return prepared == null ? ItemStack.EMPTY : prepared.copy();
        }
        Item item = BuiltInRegistries.ITEM.getValue(ModRefs.identifier(icon.type()));
        if (item == null || item == Items.AIR) {
            item = Items.BARRIER;
        }
        ItemStack stack = new ItemStack(item, icon.amount());
        if (icon.name() != null) {
            stack.set(DataComponents.CUSTOM_NAME, plain(icon.name()));
        }
        if (!icon.lore().isEmpty()) {
            List<Component> lines = new ArrayList<>(icon.lore().size());
            for (String line : icon.lore()) {
                lines.add(plain(line));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        if (icon.glint()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        }
        if (icon.itemModel() != null) {
            stack.set(DataComponents.ITEM_MODEL, ModRefs.identifier(icon.itemModel()));
        }
        return stack;
    }

    static Component plain(String markup) {
        MutableComponent root = Component.empty().withStyle(PLAIN);
        root.append(ModText.component(markup));
        return root;
    }
}
