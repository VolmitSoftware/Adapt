package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.BodyHooks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Equipment changes vanilla detected this tick, before the previous items are replaced. */
@Mixin(LivingEntity.class)
public abstract class EquipmentChangeEvents {
    @Shadow
    @Final
    private Map<EquipmentSlot, ItemStack> lastEquipmentItems;

    @Inject(method = "handleEquipmentChanges", at = @At("HEAD"))
    private void adapt$equipmentChanged(Map<EquipmentSlot, ItemStack> changed, CallbackInfo callback) {
        BodyHooks.equipmentChanged((LivingEntity) (Object) this, changed, lastEquipmentItems);
    }
}
