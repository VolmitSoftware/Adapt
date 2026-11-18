package art.arcane.adapt.mod.feature.protection;

import art.arcane.adapt.api.protection.ProtectionProbePort;
import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.mixin.ItemEntityAccess;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.event.entity.EntityPickupItemEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Vanilla rules plus the loader's own cancellable events, so claim mods see what Adapt does for a player. Breaks
 * use the protection host, which already asks the loader's break event. Pickups that pass are also posted on
 * Adapt's bus as a pickup, as on Bukkit, with the item showing only the amount that would be taken. Break, use and
 * place probes are then posted on the bus marked as probes, cancelled when the loader denied, as Bukkit delivers its
 * probe events to Adapt's handlers other than skills and adaptations; a handler that cancels denies the probe.
 */
public final class ModProtectionProbePort implements ProtectionProbePort {
    @Override
    public boolean probeUse(PlayerRef actor, BlockRef block) {
        ServerPlayer player = ModRefs.player(actor);
        ServerLevel level = ModRefs.world(block.world());
        if (player == null || level == null) {
            return false;
        }
        BlockPos pos = ModRefs.pos(block.pos());
        boolean allowed = ModProtectionHost.mayUse(player, level, pos) && LoaderProbes.canUse(level, player, hit(pos));
        UseResult use = allowed ? UseResult.DEFAULT : UseResult.DENY;
        PlayerInteractEvent event = postProbe(new PlayerInteractEvent(actor, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
            actor.inventory().mainHand(), block, BlockFace.UP, null, use, use));
        return event.useBlock() != UseResult.DENY;
    }

    @Override
    public boolean probeBreak(PlayerRef actor, BlockRef block) {
        BlockBreakEvent event = new BlockBreakEvent(actor, block, true, 0);
        event.setCancelled(!ProtectionProbePort.super.probeBreak(actor, block));
        return !postProbe(event).isCancelled();
    }

    @Override
    public boolean probePlace(PlayerRef actor, BlockRef block) {
        ServerPlayer player = ModRefs.player(actor);
        ServerLevel level = ModRefs.world(block.world());
        if (player == null || level == null || player.level() != level) {
            return false;
        }
        BlockPos pos = ModRefs.pos(block.pos());
        boolean allowed = ModProtectionHost.mayPlace(player, level, pos) && LoaderProbes.canPlace(level, player, pos, level.getBlockState(pos));
        BlockPlaceEvent event = new BlockPlaceEvent(actor, block, block.state(), block, actor.inventory().mainHand(), Hand.MAIN_HAND);
        event.setCancelled(!allowed);
        postProbe(event);
        return !event.isCancelled() && event.canBuild();
    }

    private static <E extends AdaptEvent> E postProbe(E event) {
        if (ModEvents.listening(event.getClass())) {
            event.markProtectionProbe();
            ModEvents.post(event);
        }
        return event;
    }

    @Override
    public boolean probeTreeGrow(PlayerRef actor, BlockRef origin, Id feature, List<BlockChange> blocks) {
        ServerPlayer player = ModRefs.player(actor);
        ServerLevel level = ModRefs.world(origin.world());
        if (player == null || level == null || player.level() != level) {
            return false;
        }
        BlockState sapling = level.getBlockState(ModRefs.pos(origin.pos()));
        UUID world = origin.world().uid();
        for (BlockChange change : blocks) {
            if (!change.block().world().uid().equals(world)) {
                return false;
            }
            BlockPos pos = ModRefs.pos(change.block().pos());
            if (!ModProtectionHost.mayPlace(player, level, pos) || !LoaderProbes.canPlace(level, player, pos, sapling)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean probeDropPickup(PlayerRef player, ItemEntityRef item, int remaining, Loc source) {
        return playerPickup(player, item, remaining);
    }

    @Override
    public boolean probeItemPickup(PlayerRef player, ItemEntityRef item, int remaining) {
        return playerPickup(player, item, remaining);
    }

    @Override
    public boolean probeEntityPickup(LivingRef entity, ItemEntityRef item, int remaining) {
        LivingEntity taker = ModRefs.living(entity);
        if (taker == null || !taker.isAlive() || !(ModRefs.entity(item) instanceof ItemEntity itemEntity)) {
            return false;
        }
        int taken = PickupCount.taken(itemEntity.getItem().getCount(), remaining);
        if (taken <= 0) {
            return false;
        }
        return showingTaken(itemEntity, taken, () -> postPickup(entity, item, remaining));
    }

    private static boolean playerPickup(PlayerRef actor, ItemEntityRef item, int remaining) {
        ServerPlayer player = ModRefs.player(actor);
        if (player == null || !(ModRefs.entity(item) instanceof ItemEntity itemEntity) || !mayPickup(player, itemEntity)) {
            return false;
        }
        int taken = PickupCount.taken(itemEntity.getItem().getCount(), remaining);
        if (taken <= 0) {
            return false;
        }
        return showingTaken(itemEntity, taken,
                () -> LoaderProbes.canPickup(player, itemEntity) && postPickup(actor, item, remaining));
    }

    private static boolean mayPickup(ServerPlayer player, ItemEntity item) {
        UUID owner = ((ItemEntityAccess) item).adapt$target();
        return player.isAlive() && !player.isSpectator() && (owner == null || owner.equals(player.getUUID()));
    }

    private static boolean postPickup(LivingRef taker, ItemEntityRef item, int remaining) {
        if (!ModEvents.listening(EntityPickupItemEvent.class)) {
            return true;
        }
        return !ModEvents.post(new EntityPickupItemEvent(taker, item, remaining)).isCancelled();
    }

    private static boolean showingTaken(ItemEntity item, int taken, BooleanSupplier check) {
        ItemStack original = item.getItem();
        ItemStack visible = original.copyWithCount(taken);
        item.setItem(visible);
        try {
            return check.getAsBoolean();
        } finally {
            if (!item.isRemoved() && item.getItem() == visible) {
                item.setItem(original);
            }
        }
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }
}
