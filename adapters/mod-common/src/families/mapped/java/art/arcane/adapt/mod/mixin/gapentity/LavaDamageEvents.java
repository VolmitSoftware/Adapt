package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.BlockDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** Lava burn damage, attributed to the lava block the entity touched (Paper's lava contact). */
@Mixin(Entity.class)
public abstract class LavaDamageEvents {
    @WrapOperation(method = "lavaHurt()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean adapt$burning(Entity entity, DamageSource source, float amount, Operation<Boolean> original) {
        Level level = entity.level();
        Optional<BlockPos> contact = BlockPos.betweenClosedStream(entity.getBoundingBox().deflate(0.001))
            .filter((BlockPos pos) -> level.getFluidState(pos).is(FluidTags.LAVA))
            .findFirst()
            .map(BlockPos::immutable);
        if (contact.isEmpty()) {
            return original.call(entity, source, amount);
        }
        return BlockDamage.call(entity, level, contact.get(), () -> original.call(entity, source, amount));
    }
}
