package art.arcane.adapt.mod.platform.world;

import art.arcane.adapt.platform.world.LidRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chest, barrel and shulker box lids opened without a player, as Bukkit's {@code Lidded#open} does: chests and
 * shulker boxes get the open-count block event, barrels their {@code open} state, each with the vanilla sound.
 */
final class ModLidRef implements LidRef {
    private static final int EVENT_SET_OPEN_COUNT = 1;
    private static final Set<Key> FORCED = ConcurrentHashMap.newKeySet();
    private final ServerLevel level;
    private final BlockPos pos;

    private ModLidRef(ServerLevel level, BlockPos pos) {
        this.level = Objects.requireNonNull(level, "level");
        this.pos = pos.immutable();
    }

    static ModLidRef at(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean lidded = state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
        return lidded && level.getBlockEntity(pos) instanceof Container ? new ModLidRef(level, pos) : null;
    }

    @Override
    public boolean viewed() {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof Container container)) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            for (Slot slot : player.containerMenu.slots) {
                if (slot.container == container || (slot.container instanceof CompoundContainer compound && compound.contains(container))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isOpen() {
        if (FORCED.contains(key())) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock) {
            return state.getValue(BarrelBlock.OPEN);
        }
        return viewed();
    }

    @Override
    public void open() {
        if (!FORCED.add(key()) || viewed()) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, true), 3);
            sound(SoundEvents.BARREL_OPEN);
        } else if (state.getBlock() instanceof ChestBlock chest) {
            level.blockEvent(pos, state.getBlock(), EVENT_SET_OPEN_COUNT, 1);
            sound(chest.getOpenChestSound());
        } else if (state.getBlock() instanceof ShulkerBoxBlock) {
            level.blockEvent(pos, state.getBlock(), EVENT_SET_OPEN_COUNT, 1);
            sound(SoundEvents.SHULKER_BOX_OPEN);
        }
    }

    @Override
    public void close() {
        if (!FORCED.remove(key()) || viewed()) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BarrelBlock) {
            level.setBlock(pos, state.setValue(BarrelBlock.OPEN, false), 3);
            sound(SoundEvents.BARREL_CLOSE);
        } else if (state.getBlock() instanceof ChestBlock chest) {
            level.blockEvent(pos, state.getBlock(), EVENT_SET_OPEN_COUNT, 0);
            sound(chest.getCloseChestSound());
        } else if (state.getBlock() instanceof ShulkerBoxBlock) {
            level.blockEvent(pos, state.getBlock(), EVENT_SET_OPEN_COUNT, 0);
            sound(SoundEvents.SHULKER_BOX_CLOSE);
        }
    }

    private void sound(SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    private Key key() {
        return new Key(level.dimension().identifier().toString(), pos.asLong());
    }

    private record Key(String dimension, long pos) {
    }
}
