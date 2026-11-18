package art.arcane.adapt.mod.platform.world;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.mixin.BrewingStandDataAccess;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import java.util.ArrayList;
import java.util.List;

/** Brewing stand over the live block entity: slots 0-2 bottles, 3 ingredient, 4 fuel; fuel and brew time via its data. */
public final class ModBrewingStandRef implements BrewingStandRef {
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;

    private final ModBlockRef block;

    public ModBrewingStandRef(ModBlockRef block) {
        this.block = block;
    }

    @Override
    public BlockRef block() {
        return block;
    }

    @Override
    public ItemRef bottle(int slot) {
        return item(slot);
    }

    @Override
    public void setBottle(int slot, ItemRef item) {
        setItem(slot, item);
    }

    @Override
    public ItemRef ingredient() {
        return item(INGREDIENT_SLOT);
    }

    @Override
    public void setIngredient(ItemRef item) {
        setItem(INGREDIENT_SLOT, item);
    }

    @Override
    public ItemRef fuel() {
        return item(FUEL_SLOT);
    }

    @Override
    public void setFuel(ItemRef item) {
        setItem(FUEL_SLOT, item);
    }

    @Override
    public int fuelLevel() {
        BrewingStandBlockEntity stand = stand();
        return stand == null ? 0 : data(stand).get(BrewingStandBlockEntity.DATA_FUEL_USES);
    }

    @Override
    public void setFuelLevel(int level) {
        BrewingStandBlockEntity stand = stand();
        if (stand != null) {
            data(stand).set(BrewingStandBlockEntity.DATA_FUEL_USES, level);
            stand.setChanged();
        }
    }

    @Override
    public int brewingTime() {
        BrewingStandBlockEntity stand = stand();
        return stand == null ? 0 : data(stand).get(BrewingStandBlockEntity.DATA_BREW_TIME);
    }

    @Override
    public void setBrewingTime(int ticks) {
        BrewingStandBlockEntity stand = stand();
        if (stand != null) {
            data(stand).set(BrewingStandBlockEntity.DATA_BREW_TIME, ticks);
            stand.setChanged();
        }
    }

    @Override
    public List<PlayerRef> viewers() {
        BrewingStandBlockEntity stand = stand();
        List<PlayerRef> viewers = new ArrayList<>();
        if (stand == null) {
            return viewers;
        }
        for (ServerPlayer player : block.level().getServer().getPlayerList().getPlayers()) {
            for (Slot slot : player.containerMenu.slots) {
                if (slot.container == stand) {
                    viewers.add(ModRefs.player(player));
                    break;
                }
            }
        }
        return viewers;
    }

    private BrewingStandBlockEntity stand() {
        ServerLevel level = block.level();
        BlockPos pos = block.blockPos();
        return level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand ? stand : null;
    }

    private static ContainerData data(BrewingStandBlockEntity stand) {
        return ((BrewingStandDataAccess) stand).adapt$dataAccess();
    }

    private ItemRef item(int slot) {
        BrewingStandBlockEntity stand = stand();
        return ModRefs.item(stand == null ? ItemStack.EMPTY : stand.getItem(slot));
    }

    private void setItem(int slot, ItemRef item) {
        BrewingStandBlockEntity stand = stand();
        if (stand != null) {
            stand.setItem(slot, ModRefs.item(item).copy());
            stand.setChanged();
        }
    }
}
