package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.feature.protection.LoaderProbes;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.value.Loc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.function.Supplier;

/**
 * Shared plumbing for the modded event hooks: bus lookup, the cheap listener checks every hook runs
 * before it allocates, and the switch Adapt-initiated block breaks use to stay off the bus.
 */
public final class ModEvents {
    private static int silentBreaks;

    private ModEvents() {
    }

    /** The bus, or null before the platform is assembled. */
    public static AdaptEventBus bus() {
        return ModPlatform.bus();
    }

    /** True when a listener would receive an event of exactly this class. */
    public static boolean listening(Class<? extends AdaptEvent> type) {
        AdaptEventBus bus = ModPlatform.bus();
        return bus != null && bus.hasListeners(type);
    }

    public static <E extends AdaptEvent> E post(E event) {
        AdaptEventBus bus = ModPlatform.bus();
        if (bus != null) {
            bus.post(event);
        }
        return event;
    }

    /**
     * Runs a block break Adapt starts itself ({@code ProtectionHost.attemptBreak}, veinminer-style
     * adaptations) without posting block break or block damage events, so handlers do not recurse.
     */
    public static <T> T silentBreaks(Supplier<T> action) {
        silentBreaks++;
        try {
            return action.get();
        } finally {
            silentBreaks--;
        }
    }

    static boolean breaksSilenced() {
        return silentBreaks > 0 || LoaderProbes.probing();
    }

    static boolean server(Entity entity) {
        return entity != null && entity.level() instanceof ServerLevel;
    }

    static ItemEntityRef itemEntity(ItemEntity entity) {
        return (ItemEntityRef) ModRefs.entity(entity);
    }

    static ProjectileRef projectile(Projectile projectile) {
        return (ProjectileRef) ModRefs.entity(projectile);
    }

    static Loc loc(Entity entity) {
        return ModRefs.loc((ServerLevel) entity.level(), entity.position(), entity.getYRot(), entity.getXRot());
    }

    static Loc loc(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        return ModRefs.loc(HookCompat.level(player), new net.minecraft.world.phys.Vec3(x, y, z), yaw, pitch);
    }
}
