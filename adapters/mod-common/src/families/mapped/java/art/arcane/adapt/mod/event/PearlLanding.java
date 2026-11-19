package art.arcane.adapt.mod.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;

/**
 * Before 1.21.2 a landing ender pearl hurts its thrower with plain fall damage; 26.x deals it as the ender_pearl damage
 * type, which core and Paper treat as pearl damage (projectile cause, not fall). While a pearl lands, the fall damage
 * its thrower takes reports as ender_pearl.
 */
public final class PearlLanding {
    private static Entity thrower;

    private PearlLanding() {
    }

    public static void started(Entity owner) {
        thrower = owner;
    }

    public static void ended() {
        thrower = null;
    }

    static boolean isPearlDamage(Entity victim, DamageSource source) {
        return victim != null && victim == thrower && source.is(DamageTypes.FALL);
    }
}
