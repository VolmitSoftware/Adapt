package art.arcane.adapt.mod.platform.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Entity-less ray casts: 1.20.1 hands a null entity to CollisionContext.of, 1.21+ uses the empty context instead. */
@Mixin(ClipContext.class)
public abstract class ClipContextEntityMixin {
    @WrapOperation(
        method = "<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/CollisionContext;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/CollisionContext;"))
    private CollisionContext adapt$emptyWithoutEntity(Entity entity, Operation<CollisionContext> original) {
        return entity == null ? CollisionContext.empty() : original.call(entity);
    }
}
