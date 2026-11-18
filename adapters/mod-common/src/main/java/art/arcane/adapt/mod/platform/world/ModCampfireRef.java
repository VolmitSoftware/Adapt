package art.arcane.adapt.mod.platform.world;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.mixin.CampfireAccess;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.CampfireRef;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;

public final class ModCampfireRef implements CampfireRef {
    private final ModBlockRef block;

    public ModCampfireRef(ModBlockRef block) {
        this.block = block;
    }

    @Override
    public BlockRef block() {
        return block;
    }

    @Override
    public int size() {
        CampfireBlockEntity campfire = campfire();
        return campfire == null ? 0 : campfire.getItems().size();
    }

    @Override
    public ItemRef item(int slot) {
        CampfireBlockEntity campfire = campfire();
        return ModRefs.item(campfire == null ? ItemStack.EMPTY : campfire.getItems().get(slot));
    }

    @Override
    public int cookTime(int slot) {
        CampfireBlockEntity campfire = campfire();
        return campfire == null ? 0 : ((CampfireAccess) campfire).adapt$cookingProgress()[slot];
    }

    @Override
    public void setCookTime(int slot, int ticks) {
        CampfireBlockEntity campfire = campfire();
        if (campfire != null) {
            ((CampfireAccess) campfire).adapt$cookingProgress()[slot] = ticks;
            campfire.setChanged();
        }
    }

    @Override
    public int cookTimeTotal(int slot) {
        CampfireBlockEntity campfire = campfire();
        return campfire == null ? 0 : ((CampfireAccess) campfire).adapt$cookingTime()[slot];
    }

    private CampfireBlockEntity campfire() {
        return block.level().getBlockEntity(block.blockPos()) instanceof CampfireBlockEntity campfire ? campfire : null;
    }
}
