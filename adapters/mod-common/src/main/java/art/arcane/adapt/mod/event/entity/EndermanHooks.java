package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.EndermanAttackPlayerEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** An enderman's stare check, posted for every check like Paper's EndermanAttackPlayerEvent. */
public final class EndermanHooks {
    private EndermanHooks() {
    }

    /** The stare result to use: {@code provoked} unless a handler changed the cancelled flag. */
    public static boolean staring(Entity enderman, Player player, boolean provoked) {
        if (!(enderman.level() instanceof ServerLevel) || !(player instanceof ServerPlayer viewer)
            || !ModEvents.listening(EndermanAttackPlayerEvent.class)) {
            return provoked;
        }
        return !ModEvents.post(new EndermanAttackPlayerEvent(ModRefs.entity(enderman), ModRefs.player(viewer), provoked)).isCancelled();
    }
}
