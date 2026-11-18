package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.BlockDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/** Lava burn damage, attributed to the lava block the entity touched (Paper's lava contact). */
@Mixin(LavaFluid.class)
public abstract class LavaDamageEvents {
    @WrapOperation(method = "entityInside(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/InsideBlockEffectApplier;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/InsideBlockEffectApplier;runAfter(Lnet/minecraft/world/entity/InsideBlockEffectType;Ljava/util/function/Consumer;)V"))
    private void adapt$burning(InsideBlockEffectApplier applier, InsideBlockEffectType type, Consumer<Entity> effect, Operation<Void> original,
                               @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        BlockPos contact = pos.immutable();
        original.call(applier, type, (Consumer<Entity>) (Entity entity) -> BlockDamage.run(entity, level, contact, () -> effect.accept(entity)));
    }
}
