package art.arcane.adapt.mod.event;

import art.arcane.adapt.platform.event.entity.DamageCause;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;

/**
 * Causes Paper fixes at the call site instead of reading them from the damage source: poison ticks, snow golem
 * melting and water animals drying out. The call-site mixins wrap their hurt call in {@link #call} or {@link #run}.
 */
public final class KnownDamageCause {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private record Frame(Entity entity, DamageCause cause) {
    }

    private KnownDamageCause() {
    }

    public static <T> T call(Entity entity, DamageCause cause, Supplier<T> damage) {
        Frame outer = CURRENT.get();
        CURRENT.set(new Frame(entity, cause));
        try {
            return damage.get();
        } finally {
            if (outer == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(outer);
            }
        }
    }

    public static void run(Entity entity, DamageCause cause, Runnable damage) {
        call(entity, cause, () -> {
            damage.run();
            return null;
        });
    }

    /** The cause for a water animal drowning out of water on this Paper version. */
    public static DamageCause dryOut() {
        return DamageCauseRules.WATER_ANIMAL_DRYOUT ? DamageCause.DRYOUT : null;
    }

    static DamageCause of(Entity entity) {
        Frame frame = CURRENT.get();
        return frame != null && frame.entity() == entity ? frame.cause() : null;
    }
}
