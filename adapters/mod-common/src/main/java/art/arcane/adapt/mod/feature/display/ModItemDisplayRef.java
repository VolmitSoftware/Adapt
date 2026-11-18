package art.arcane.adapt.mod.feature.display;

import art.arcane.adapt.mod.feature.mixin.ItemDisplayDataAccess;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.display.ItemDisplayMode;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class ModItemDisplayRef extends ModDisplayRef<Display.ItemDisplay> implements ItemDisplayRef {
    ModItemDisplayRef(Display.ItemDisplay display) {
        super(display);
    }

    @Override
    public ItemRef item() {
        return ModRefs.item(synced().get(ItemDisplayDataAccess.adapt$itemStack()).copy());
    }

    @Override
    public void setItem(ItemRef item) {
        ItemStack stack = item == null ? ItemStack.EMPTY : ModRefs.item(item);
        synced().set(ItemDisplayDataAccess.adapt$itemStack(), stack == null ? ItemStack.EMPTY : stack.copy());
    }

    @Override
    public ItemDisplayMode displayMode() {
        byte id = synced().get(ItemDisplayDataAccess.adapt$itemContext());
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            if (context.getId() == id) {
                for (ItemDisplayMode mode : ItemDisplayMode.values()) {
                    if (mode.name().equals(context.name())) {
                        return mode;
                    }
                }
            }
        }
        return ItemDisplayMode.NONE;
    }

    @Override
    public void setDisplayMode(ItemDisplayMode mode) {
        ItemDisplayContext context = ItemDisplayContext.valueOf(mode.name());
        synced().set(ItemDisplayDataAccess.adapt$itemContext(), context.getId());
    }
}
