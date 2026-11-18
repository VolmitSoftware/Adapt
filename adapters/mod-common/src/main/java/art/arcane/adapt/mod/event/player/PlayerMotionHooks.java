package art.arcane.adapt.mod.event.player;

import art.arcane.adapt.mod.event.CombatHooks;
import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.player.PlayerVelocityEvent;
import art.arcane.adapt.platform.event.player.PrePlayerAttackEntityEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Velocity sent to a player's client, and the moment before a player's melee attack. */
public final class PlayerMotionHooks {
    private PlayerMotionHooks() {
    }

    /** True when a handler cancelled the attack. */
    public static boolean preAttack(ServerPlayer player, Entity target) {
        if (!ModEvents.listening(PrePlayerAttackEntityEvent.class)) {
            return false;
        }
        boolean willAttack = target.isAttackable() && !target.skipAttackInteraction(player);
        if (!ModEvents.post(new PrePlayerAttackEntityEvent(ModRefs.player(player), ModRefs.entity(target), willAttack)).isCancelled()) {
            return false;
        }
        CombatHooks.attacked();
        return true;
    }

    /**
     * The velocity about to be sent to the player's client. Returns null when a handler cancelled,
     * else the velocity to send (the player's motion is set to it when changed).
     */
    public static Vec3 velocity(ServerPlayer player, Vec3 velocity) {
        if (!ModEvents.listening(PlayerVelocityEvent.class)) {
            return velocity;
        }
        art.arcane.adapt.platform.value.Vec3 original = ModRefs.vec(velocity);
        PlayerVelocityEvent event = ModEvents.post(new PlayerVelocityEvent(ModRefs.player(player), original));
        if (event.isCancelled()) {
            return null;
        }
        if (event.velocity().equals(original)) {
            return velocity;
        }
        Vec3 changed = ModRefs.vec(event.velocity());
        player.setDeltaMovement(changed);
        return changed;
    }
}
