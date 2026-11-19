package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.HarvestHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players picking sweet berries or glow berries by hand. */
@Mixin({SweetBerryBushBlock.class, CaveVinesBlock.class, CaveVinesPlantBlock.class})
public abstract class BerryHarvestEvents {
    @Inject(method = "use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at = @At("HEAD"))
    private void adapt$harvesting(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
                                  CallbackInfoReturnable<InteractionResult> callback) {
        HarvestHooks.opening(player, level, pos);
        boolean drops;
        if (((Object) this) instanceof SweetBerryBushBlock) {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            drops = age > 1 && (age == 3 || !player.getItemInHand(hand).is(Items.BONE_MEAL));
        } else {
            drops = CaveVines.hasGlowBerries(state);
        }
        if (drops) {
            HarvestHooks.lootStarting();
        }
    }

    @Inject(method = "use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
        at = @At("RETURN"), cancellable = true)
    private void adapt$harvested(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
                                 CallbackInfoReturnable<InteractionResult> callback) {
        HarvestHooks.lootDone();
        if (HarvestHooks.closing(level, pos, state)) {
            callback.setReturnValue(InteractionResult.PASS);
        }
    }
}
