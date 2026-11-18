package art.arcane.adapt.mod.platform.world;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.mixin.FurnaceDataAccess;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.FurnaceRef;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class ModFurnaceRef implements FurnaceRef {
    private static final int SLOT_INPUT = 0;
    private static final int DATA_LIT_TIME = 0;
    private static final int DATA_COOKING_PROGRESS = 2;
    private static final int DATA_COOKING_TOTAL_TIME = 3;

    private final ModBlockRef block;

    public ModFurnaceRef(ModBlockRef block) {
        this.block = block;
    }

    @Override
    public BlockRef block() {
        return block;
    }

    @Override
    public int burnTime() {
        return read(DATA_LIT_TIME);
    }

    @Override
    public int cookTime() {
        return read(DATA_COOKING_PROGRESS);
    }

    @Override
    public void setCookTime(int ticks) {
        AbstractFurnaceBlockEntity furnace = furnace();
        if (furnace != null) {
            data(furnace).set(DATA_COOKING_PROGRESS, ticks);
            furnace.setChanged();
        }
    }

    @Override
    public int cookTimeTotal() {
        return read(DATA_COOKING_TOTAL_TIME);
    }

    @Override
    public ItemRef smelting() {
        AbstractFurnaceBlockEntity furnace = furnace();
        return ModRefs.item(furnace == null ? ItemStack.EMPTY : furnace.getItem(SLOT_INPUT));
    }

    private int read(int index) {
        AbstractFurnaceBlockEntity furnace = furnace();
        return furnace == null ? 0 : data(furnace).get(index);
    }

    private AbstractFurnaceBlockEntity furnace() {
        return block.level().getBlockEntity(block.blockPos()) instanceof AbstractFurnaceBlockEntity furnace ? furnace : null;
    }

    private static ContainerData data(AbstractFurnaceBlockEntity furnace) {
        return ((FurnaceDataAccess) furnace).adapt$dataAccess();
    }
}
