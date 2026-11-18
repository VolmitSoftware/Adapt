package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.player.PlayerGapHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla shearable mob sheared by a player; dispensers and other non-player shearing are not reported. */
@Mixin({Sheep.class, MushroomCow.class, SnowGolem.class, CopperGolem.class, Bogged.class})
public abstract class ShearEvents {
    @Inject(method = "shear", at = @At("HEAD"), cancellable = true)
    private void adapt$shearing(ServerLevel level, SoundSource source, ItemStack tool, CallbackInfo callback) {
        if (PlayerGapHooks.shearing((Entity) (Object) this, level, tool)) {
            callback.cancel();
        }
    }
}
