package art.arcane.adapt.mod.event.player;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;

/** Food exhaustion, with the reason its vanilla call site gives. */
public final class ExhaustionHooks {
    private static EntityExhaustionEvent.Reason reason;

    private ExhaustionHooks() {
    }

    /** Opens a reason for the exhaustion caused inside the returned scope; pass the result to {@link #close}. */
    public static EntityExhaustionEvent.Reason open(EntityExhaustionEvent.Reason next) {
        EntityExhaustionEvent.Reason outer = reason;
        reason = next;
        return outer;
    }

    public static void close(EntityExhaustionEvent.Reason outer) {
        reason = outer;
    }

    /** The movement reason vanilla's movement statistics pick for this player right now. */
    public static EntityExhaustionEvent.Reason movement(ServerPlayer player) {
        if (player.isSwimming()) {
            return EntityExhaustionEvent.Reason.SWIM;
        }
        if (player.isEyeInFluid(FluidTags.WATER)) {
            return EntityExhaustionEvent.Reason.WALK_UNDERWATER;
        }
        if (player.isInWater()) {
            return EntityExhaustionEvent.Reason.WALK_ON_WATER;
        }
        if (player.isSprinting()) {
            return EntityExhaustionEvent.Reason.SPRINT;
        }
        return player.isCrouching() ? EntityExhaustionEvent.Reason.CROUCH : EntityExhaustionEvent.Reason.WALK;
    }

    /**
     * Exhaustion about to be added to a player. Returns the amount to add, or NaN when a handler
     * cancelled.
     */
    public static float exhausting(Player player, float amount) {
        return exhausting(player, reason == null ? EntityExhaustionEvent.Reason.UNKNOWN : reason, amount);
    }

    public static float exhausting(Player player, EntityExhaustionEvent.Reason why, float amount) {
        if (!(player instanceof ServerPlayer serverPlayer) || !ModEvents.listening(EntityExhaustionEvent.class)) {
            return amount;
        }
        EntityExhaustionEvent event = ModEvents.post(new EntityExhaustionEvent(ModRefs.player(serverPlayer), why, amount));
        return event.isCancelled() ? Float.NaN : event.exhaustion();
    }
}
