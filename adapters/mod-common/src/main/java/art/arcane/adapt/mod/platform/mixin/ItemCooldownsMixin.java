package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptCooldowns;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsMixin implements AdaptCooldowns {
    @Shadow
    private int tickCount;
    @Unique
    private final Map<Identifier, Integer> adapt$ends = new HashMap<>();

    @Inject(method = "addCooldown(Lnet/minecraft/resources/Identifier;I)V", at = @At("TAIL"))
    private void adapt$trackCooldown(Identifier group, int time, CallbackInfo callback) {
        adapt$ends.put(group, tickCount + time);
    }

    @Inject(method = "removeCooldown", at = @At("TAIL"))
    private void adapt$forgetCooldown(Identifier group, CallbackInfo callback) {
        adapt$ends.remove(group);
    }

    @Override
    public int adapt$remaining(Identifier group) {
        Integer end = adapt$ends.get(group);
        if (end == null) {
            return 0;
        }
        int remaining = end - tickCount;
        if (remaining <= 0) {
            adapt$ends.remove(group);
            return 0;
        }
        return remaining;
    }
}
