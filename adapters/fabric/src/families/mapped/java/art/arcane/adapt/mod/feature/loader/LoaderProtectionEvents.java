package art.arcane.adapt.mod.feature.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Asks Fabric API's {@code PlayerBlockBreakEvents.BEFORE} and {@code UseBlockCallback} listeners without acting.
 * Neither module is a compile dependency, so both are reached reflectively and an absent module allows. Fabric API
 * has no block place or item pickup event: placing is asked as a right-click on the target block, pickups allow.
 */
public final class LoaderProtectionEvents {
    private static final Hook BEFORE_BREAK = Hook.resolve("net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents",
            "BEFORE", "net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents$Before", "beforeBlockBreak",
            MethodType.methodType(boolean.class, Level.class, Player.class, BlockPos.class, BlockState.class, BlockEntity.class));
    private static final Hook USE_BLOCK = Hook.resolve("net.fabricmc.fabric.api.event.player.UseBlockCallback",
            "EVENT", "net.fabricmc.fabric.api.event.player.UseBlockCallback", "interact",
            MethodType.methodType(InteractionResult.class, Player.class, Level.class, InteractionHand.class, BlockHitResult.class));

    private LoaderProtectionEvents() {
    }

    public static boolean canBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (BEFORE_BREAK == null) {
            return true;
        }
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        try {
            return (boolean) BEFORE_BREAK.call().invoke(BEFORE_BREAK.listeners(), (Level) level, (Player) player, pos, state, blockEntity);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Fabric break probe failed", failure);
        }
    }

    public static boolean canUse(ServerLevel level, ServerPlayer player, BlockHitResult hit) {
        if (USE_BLOCK == null) {
            return true;
        }
        try {
            InteractionResult result = (InteractionResult) USE_BLOCK.call().invoke(USE_BLOCK.listeners(), (Player) player,
                    (Level) level, InteractionHand.MAIN_HAND, hit);
            return result != InteractionResult.FAIL;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Fabric use probe failed", failure);
        }
    }

    public static boolean canPlace(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState against) {
        return canUse(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    public static boolean canPickup(ServerPlayer player, ItemEntity item) {
        return true;
    }

    private record Hook(Object event, MethodHandle invoker, MethodHandle call) {
        static Hook resolve(String holder, String field, String callback, String method, MethodType type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> eventType = Class.forName("net.fabricmc.fabric.api.event.Event");
                Object event = Class.forName(holder).getField(field).get(null);
                MethodHandle invoker = lookup.findVirtual(eventType, "invoker", MethodType.methodType(Object.class));
                return new Hook(event, invoker, lookup.findVirtual(Class.forName(callback), method, type));
            } catch (ReflectiveOperationException | LinkageError absent) {
                return null;
            }
        }

        Object listeners() throws Throwable {
            return (Object) invoker.invoke(event);
        }
    }
}
