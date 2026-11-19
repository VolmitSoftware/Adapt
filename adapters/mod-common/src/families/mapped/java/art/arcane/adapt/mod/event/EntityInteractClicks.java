package art.arcane.adapt.mod.event;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Before 26.x a client sends one entity right click as an interaction at a position and then, unless that was consumed
 * client-side, a plain interaction with the same entity and hand. 26.x sends one positioned packet, so the
 * interaction event is posted once, for the positioned packet, and the plain packet that completes the same click
 * takes over its outcome: a cancelled click stops vanilla's interaction too.
 */
public final class EntityInteractClicks {
    private static final Map<ServerPlayer, Click> LAST = new WeakHashMap<>();

    private EntityInteractClicks() {
    }

    /** True when the packet's vanilla handling is to be skipped. */
    public static boolean interacting(ServerPlayer player, Entity target, InteractionHand hand, Vec3 location) {
        int entityId = target == null ? -1 : target.getId();
        int tick = player.server.getTickCount();
        if (location != null) {
            boolean cancelled = InteractHooks.interactingEntity(player, target, hand, location);
            LAST.put(player, new Click(entityId, hand, tick, cancelled));
            return cancelled;
        }
        Click positioned = LAST.remove(player);
        if (positioned != null && positioned.completedBy(entityId, hand, tick)) {
            if (positioned.cancelled()) {
                player.containerMenu.sendAllDataToRemote();
            }
            return positioned.cancelled();
        }
        return InteractHooks.interactingEntity(player, target, hand, null);
    }

    /** A positioned interaction waiting for the plain packet of the same click. */
    record Click(int entityId, InteractionHand hand, int tick, boolean cancelled) {
        /** The plain packet follows within the same client tick, which the server handles in the same or the next tick. */
        boolean completedBy(int plainEntityId, InteractionHand plainHand, int plainTick) {
            return entityId == plainEntityId && hand == plainHand && plainTick - tick <= 1;
        }
    }
}
