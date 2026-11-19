package art.arcane.adapt.mod.feature.menu;

import art.arcane.adapt.mod.platform.ModContentAliases;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.menu.MenuIcon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Menu icon stacks written as item NBT. The glint is an enchantment list holding one empty entry, which renders the
 * glint without a tooltip line; item model overrides do not exist on this version, so icons keep their item's model.
 */
final class MenuIcons {
    private static final Style PLAIN = Style.EMPTY.withItalic(false);

    private MenuIcons() {
    }

    static ItemStack stack(MenuIcon icon) {
        if (icon.item() != null) {
            ItemStack prepared = ModRefs.item(icon.item());
            return prepared == null ? ItemStack.EMPTY : prepared.copy();
        }
        ResourceLocation location = ModContentAliases.location(BuiltInRegistries.ITEM, RegistryKind.ITEM, icon.type());
        Item item = location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
        if (item == Items.AIR) {
            item = Items.BARRIER;
        }
        ItemStack stack = new ItemStack(item, icon.amount());
        if (icon.name() != null) {
            stack.setHoverName(plain(icon.name()));
        }
        if (!icon.lore().isEmpty()) {
            ListTag lines = new ListTag();
            for (String line : icon.lore()) {
                lines.add(StringTag.valueOf(Component.Serializer.toJson(plain(line))));
            }
            stack.getOrCreateTagElement(ItemStack.TAG_DISPLAY).put(ItemStack.TAG_LORE, lines);
        }
        if (icon.glint()) {
            ListTag glint = new ListTag();
            glint.add(new CompoundTag());
            stack.getOrCreateTag().put(ItemStack.TAG_ENCH, glint);
        }
        return stack;
    }

    static Component plain(String markup) {
        MutableComponent root = Component.empty().withStyle(PLAIN);
        root.append(ModText.component(markup));
        return root;
    }
}
