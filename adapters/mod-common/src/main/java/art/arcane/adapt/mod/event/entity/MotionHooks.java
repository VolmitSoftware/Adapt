package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.CauseStack;
import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import art.arcane.adapt.platform.event.entity.EntityToggleGlideEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Elytra gliding, spear lunges and mace smash attempts. */
public final class MotionHooks {
    public static final int CANCELLED = Integer.MIN_VALUE;

    private static final CauseStack<Entity> PIERCING_USERS = new CauseStack<>();
    private static boolean relunging;
    private static boolean resyncingGlide;
    private static LivingEntity smashAttacker;
    private static LivingEntity smashTarget;
    private static long smashTick = Long.MIN_VALUE;
    private static boolean smashResult;

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
    public static void resyncGlide(LivingEntity entity) {
        resyncingGlide = true;
        try {
            entity.stopFallFlying();
        } finally {
            resyncingGlide = false;
        }
    }

    public static void piercingStarted(Entity user) {
        PIERCING_USERS.push(user, false);
    }

    public static void piercingEnded() {
        PIERCING_USERS.pop();
    }

    /**
     * A post-piercing (lunge) impulse about to push its user. Returns the enchantment level to use,
     * or {@link #CANCELLED}.
     */
    public static int lunging(Entity entity, int level) {
        if (relunging || PIERCING_USERS.current() != entity || !(entity instanceof LivingEntity living)
            || !(entity.level() instanceof ServerLevel) || !ModEvents.listening(EntityLungeEvent.class)) {
            return level;
        }
        EntityLungeEvent event = ModEvents.post(new EntityLungeEvent(ModRefs.living(living), level));
        return event.isCancelled() ? CANCELLED : event.power();
    }

    public static void relunge(Runnable impulse) {
        relunging = true;
        try {
            impulse.run();
        } finally {
            relunging = false;
        }
    }

    /**
     * Whether a mace hit by this attacker on this target is a smash. Posted once per hit; the
     * damage bonus asks first and the hurt-enemy calls of the same tick reuse its answer.
     */
    public static boolean smash(LivingEntity attacker, Entity victim, boolean original) {
        if (!(victim instanceof LivingEntity target) || !(attacker.level() instanceof ServerLevel level)
            || !ModEvents.listening(EntityAttemptSmashAttackEvent.class)) {
            return original;
        }
        long tick = level.getGameTime();
        if (attacker == smashAttacker && target == smashTarget && tick == smashTick) {
            return smashResult;
        }
        EntityAttemptSmashAttackEvent event = ModEvents.post(new EntityAttemptSmashAttackEvent(ModRefs.living(attacker),
            ModRefs.living(target), ModRefs.item(attacker.getWeaponItem()), original, UseResult.DEFAULT));
        smashAttacker = attacker;
        smashTarget = target;
        smashTick = tick;
        smashResult = event.smashes();
        return smashResult;
    }
}
