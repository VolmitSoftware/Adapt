package art.arcane.adapt.mod.platform.mixin;

import art.arcane.adapt.mod.platform.data.AdaptCooldowns;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/** Cooldowns on this version are keyed by item; the group is the item's registry id. */
@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsMixin implements AdaptCooldowns {
    @Shadow
    private int tickCount;
    @Unique
    private final Map<ResourceLocation, Integer> adapt$ends = new HashMap<>();

    @Inject(method = "addCooldown(Lnet/minecraft/world/item/Item;I)V", at = @At("TAIL"))
    private void adapt$trackCooldown(Item item, int time, CallbackInfo callback) {
        adapt$ends.put(BuiltInRegistries.ITEM.getKey(item), tickCount + time);
    }

    @Inject(method = "removeCooldown(Lnet/minecraft/world/item/Item;)V", at = @At("TAIL"))
    private void adapt$forgetCooldown(Item item, CallbackInfo callback) {
        adapt$ends.remove(BuiltInRegistries.ITEM.getKey(item));
    }

    @Override
    public int adapt$remaining(ResourceLocation group) {
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
