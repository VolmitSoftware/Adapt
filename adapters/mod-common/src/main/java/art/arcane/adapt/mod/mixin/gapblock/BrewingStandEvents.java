package art.arcane.adapt.mod.mixin.gapblock;

import art.arcane.adapt.mod.event.inventory.ContainerHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A brewing stand finishing a brew; the results are posted after vanilla mixed them, undone on cancel. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandEvents {
    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;doBrew"))
    private static void adapt$brewing(CallbackInfo callback, @Local(argsOnly = true) BrewingStandBlockEntity stand) {
        ContainerHooks.brewing(stand);
    }

    @Inject(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;doBrew",
        shift = At.Shift.AFTER))
    private static void adapt$brewed(CallbackInfo callback, @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) BrewingStandBlockEntity stand) {
        BrewingStandAccess access = (BrewingStandAccess) stand;
        ContainerHooks.brewed(stand, pos, access.adapt$fuel(), access.adapt$brewTime());
    }
}
