package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The block dealing damage, as Paper attributes it: the block call-site mixins (magma, campfire, cactus, berry bush,
 * stalagmite, conduit, lava) wrap their hurt call in {@link #call} or {@link #run}. {@link DamageCauseRules} names the
 * damage that is block damage even without a known block, and the cause Paper reports.
 */
public final class BlockDamage {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private record Frame(Entity entity, Level level, BlockPos pos) {
    }

    private BlockDamage() {
    }

    public static <T> T call(Entity entity, Level level, BlockPos pos, Supplier<T> damage) {
        Frame outer = CURRENT.get();
        CURRENT.set(new Frame(entity, level, pos.immutable()));
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

    public static void run(Entity entity, Level level, BlockPos pos, Runnable damage) {
        call(entity, level, pos, () -> {
            damage.run();
            return null;
        });
    }

    /** True when the damage {@code entity} is taking from {@code source}, which has no entity source, counts as block damage. */
    static boolean byBlock(Entity entity, DamageSource source) {
        return DamageCauseRules.alwaysByBlock(vanillaType(entity, source)) || frame(entity) != null;
    }

    /** The cause Paper reports for the damage {@code entity} is taking from {@code source}. */
    static DamageCause cause(Entity entity, DamageSource source, boolean sweeping) {
        String type = vanillaType(entity, source);
        DamageCause known = sweeping && sweepable(type) ? DamageCause.ENTITY_SWEEP_ATTACK : KnownDamageCause.of(entity);
        return DamageCauseRules.cause(type, DamageDealer.of(source.getDirectEntity()), byBlock(entity, source), known);
    }

    private static boolean sweepable(String type) {
        return switch (type) {
            case "mob_attack", "mob_attack_no_aggro", "player_attack", "spear", "sting", "mace_smash" -> true;
            default -> false;
        };
    }

    private static String vanillaType(Entity entity, DamageSource source) {
        if (PearlLanding.isPearlDamage(entity, source)) {
            return "ender_pearl";
        }
        Optional<ResourceKey<DamageType>> key = source.typeHolder().unwrapKey();
        if (key.isEmpty() || !"minecraft".equals(key.get().location().getNamespace())) {
            return "";
        }
        return key.get().location().getPath();
    }

    /** The block dealing the damage {@code entity} is taking, or null when unknown. */
    static BlockRef damager(Entity entity) {
        Frame frame = frame(entity);
        return frame != null && frame.level() instanceof ServerLevel level ? ModRefs.block(level, frame.pos()) : null;
    }

    private static Frame frame(Entity entity) {
        Frame frame = CURRENT.get();
        return frame != null && frame.entity() == entity ? frame : null;
    }
}
