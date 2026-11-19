package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.AiHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every path search, once vanilla decided a new path is needed. Cancelled: no path. */
@Mixin(PathNavigation.class)
public abstract class PathfindEvents {
    @Shadow
    @Final
    protected Mob mob;

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getProfiler()Lnet/minecraft/util/profiling/ProfilerFiller;", ordinal = 0),
        cancellable = true)
    private void adapt$pathfinding(java.util.Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange, float maxPathLength,
                                   CallbackInfoReturnable<Path> callback) {
        if (AiHooks.pathfinding(mob, targets)) {
            callback.setReturnValue(null);
        }
    }
}
