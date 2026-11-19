package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Bogged;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Every vanilla shearable mob sheared by a player; dispensers and other non-player shearing are not reported.
 * Forge shears these mobs through IForgeShearable.onSheared instead of shear(SoundSource).
 */
@Mixin({Sheep.class, MushroomCow.class, SnowGolem.class, Bogged.class})
public abstract class ShearEvents {
    @Unique
    private ItemStack adapt$shearTool = ItemStack.EMPTY;

    @Inject(method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
        at = @At("HEAD"))
    private void adapt$interacting(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        adapt$shearTool = player.getItemInHand(hand);
    }

    @Inject(method = "shear(Lnet/minecraft/sounds/SoundSource;)V", at = @At("HEAD"), cancellable = true)
    private void adapt$shearing(SoundSource source, CallbackInfo callback) {
        ItemStack tool = adapt$shearTool;
        adapt$shearTool = ItemStack.EMPTY;
        Entity entity = (Entity) (Object) this;
        if (entity.level() instanceof ServerLevel level && PlayerGapHooks.shearing(entity, level, tool)) {
            callback.cancel();
        }
    }

    @Inject(method = "onSheared(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;I)Ljava/util/List;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void adapt$shearingForge(Player player, ItemStack tool, Level world, BlockPos pos, int fortune,
                                     CallbackInfoReturnable<List<ItemStack>> callback) {
        Entity entity = (Entity) (Object) this;
        if (player != null && world instanceof ServerLevel level && PlayerGapHooks.shearing(entity, level, tool)) {
            callback.setReturnValue(List.of());
        }
    }
}
