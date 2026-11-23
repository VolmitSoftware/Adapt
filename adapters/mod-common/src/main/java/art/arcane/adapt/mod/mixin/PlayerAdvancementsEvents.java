package art.arcane.adapt.mod.mixin;

import art.arcane.adapt.mod.event.AdvancementHooks;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Advancement completion: vanilla grants the rewards exactly once, when the advancement turns done. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsEvents {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void adapt$completed(AdvancementHolder advancement, String criterion, CallbackInfoReturnable<Boolean> callback) {
        AdvancementHooks.completed(player, advancement);
    }
}
