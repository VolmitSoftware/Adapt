package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptFlightRequests;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Players whose may-fly is only a flight request grant take fall damage like players who may not fly. */
@Mixin(Player.class)
public abstract class PlayerFlightRequestMixin implements AdaptFlightRequests {
    @Unique
    private boolean adapt$flightRequests;

    @Override
    public boolean adapt$flightRequests() {
        return adapt$flightRequests;
    }

    @Override
    public void adapt$setFlightRequests(boolean granted) {
        adapt$flightRequests = granted;
    }

    @ModifyExpressionValue(method = "causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z"))
    private boolean adapt$mayFlyForFallDamage(boolean mayFly) {
        return mayFly && !adapt$flightRequests;
    }
}
