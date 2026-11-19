package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.EntityToggleGlideEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Elytra gliding. */
public final class MotionHooks {
    private static boolean resyncingGlide;

    private MotionHooks() {
    }

    /** True when a handler cancelled the change of the gliding flag. */
    public static boolean toggleGlide(LivingEntity entity, boolean gliding) {
        if (resyncingGlide || !(entity.level() instanceof ServerLevel) || entity.isFallFlying() == gliding
            || !ModEvents.listening(EntityToggleGlideEvent.class)) {
            return false;
        }
        return ModEvents.post(new EntityToggleGlideEvent(ModRefs.living(entity), gliding)).isCancelled();
    }

    /** Sends the client its real gliding state after a cancelled start, without posting a stop. */
    public static void resyncGlide(Player player) {
        resyncingGlide = true;
        try {
            player.stopFallFlying();
        } finally {
            resyncingGlide = false;
        }
    }
}
