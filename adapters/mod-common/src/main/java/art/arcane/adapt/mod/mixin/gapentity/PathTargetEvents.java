package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.AiHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The entity a path is searched towards, for the pathfind event that follows. */
@Mixin({PathNavigation.class, GroundPathNavigation.class, WallClimberNavigation.class, FlyingPathNavigation.class})
public abstract class PathTargetEvents {
    @Inject(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"))
    private void adapt$pathingTo(Entity target, int reachRange, CallbackInfoReturnable<Path> callback) {
        AiHooks.pathingTo(target);
    }

    @Inject(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void adapt$pathed(Entity target, int reachRange, CallbackInfoReturnable<Path> callback) {
        AiHooks.pathed();
    }
}
