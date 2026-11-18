package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.CauseStack;
import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.event.entity.EntityPushedByEntityAttackEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Knockback and pushes. Each vanilla caller names its cause and the entity behind it before the
 * knockback runs; knockback with no known caller is posted as UNKNOWN.
 */
public final class KnockbackHooks {
    private static final CauseStack<Frame> FRAMES = new CauseStack<>();

    private KnockbackHooks() {
    }

    private record Frame(EntityKnockbackEvent.Cause cause, Entity pushedBy) {
    }

    public static void open(EntityKnockbackEvent.Cause cause, Entity pushedBy) {
        FRAMES.push(new Frame(cause, pushedBy), false);
    }

    public static void close() {
        FRAMES.pop();
    }

    /** Knockback from damage: ENTITY_ATTACK by the direct entity when there is one, else DAMAGE. */
    public static void openDamage(DamageSource source) {
        Entity direct = source == null ? null : source.getDirectEntity();
        open(direct == null ? EntityKnockbackEvent.Cause.DAMAGE : EntityKnockbackEvent.Cause.ENTITY_ATTACK, direct);
    }

    /** True when any knockback or push listener exists. */
    public static boolean active() {
        return ModEvents.listening(EntityKnockbackEvent.class) || ModEvents.listening(EntityPushedByEntityAttackEvent.class);
    }

    private static boolean listening(Entity pushedBy) {
        return pushedBy == null ? ModEvents.listening(EntityKnockbackEvent.class) : ModEvents.listening(EntityPushedByEntityAttackEvent.class);
    }

    /**
     * The velocity a knockback sets, given the velocity before it. Returns null when a handler
     * cancelled, the new velocity otherwise.
     */
    public static Vec3 knockback(LivingEntity entity, Vec3 before, Vec3 after) {
        if (!(entity.level() instanceof ServerLevel)) {
            return after;
        }
        Frame frame = FRAMES.current();
        EntityKnockbackEvent.Cause cause = frame == null ? EntityKnockbackEvent.Cause.UNKNOWN : frame.cause();
        Entity pushedBy = frame == null ? null : frame.pushedBy();
        Vec3 delta = post(entity, cause, pushedBy, after.subtract(before));
        return delta == null ? null : before.add(delta);
    }

    /** A push of the given impulse. Returns null when a handler cancelled, else the impulse to apply. */
    public static Vec3 push(Entity entity, Entity pushedBy, EntityKnockbackEvent.Cause cause, Vec3 impulse) {
        if (!(entity.level() instanceof ServerLevel)) {
            return impulse;
        }
        return post(entity, cause, pushedBy, impulse);
    }

    /** Explosion knockback for living entities; Vec3.ZERO when a handler cancelled. */
    public static Vec3 explosion(Entity entity, Entity source, Vec3 knockback) {
        if (!(entity instanceof LivingEntity) || !(entity.level() instanceof ServerLevel)) {
            return knockback;
        }
        Vec3 result = post(entity, EntityKnockbackEvent.Cause.EXPLOSION, source, knockback);
        return result == null ? Vec3.ZERO : result;
    }

    private static Vec3 post(Entity entity, EntityKnockbackEvent.Cause cause, Entity pushedBy, Vec3 knockback) {
        if (!listening(pushedBy)) {
            return knockback;
        }
        art.arcane.adapt.platform.value.Vec3 original = ModRefs.vec(knockback);
        EntityKnockbackEvent event = pushedBy == null
            ? new EntityKnockbackEvent(ModRefs.entity(entity), cause, original)
            : new EntityPushedByEntityAttackEvent(ModRefs.entity(entity), cause, original, ModRefs.entity(pushedBy));
        ModEvents.post(event);
        if (event.isCancelled()) {
            return null;
        }
        return event.knockback().equals(original) ? knockback : ModRefs.vec(event.knockback());
    }
}
