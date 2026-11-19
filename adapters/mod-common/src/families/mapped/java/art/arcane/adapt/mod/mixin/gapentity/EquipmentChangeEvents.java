package art.arcane.adapt.mod.mixin.gapentity;

import art.arcane.adapt.mod.event.entity.BodyHooks;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.Map;

/** Equipment changes vanilla detected this tick, before the previous items are replaced. */
@Mixin(LivingEntity.class)
public abstract class EquipmentChangeEvents {
    @Shadow
    @Final
    private NonNullList<ItemStack> lastHandItemStacks;

    @Shadow
    @Final
    private NonNullList<ItemStack> lastArmorItemStacks;

    @Shadow
    private ItemStack lastBodyItemStack;

    @Inject(method = "handleEquipmentChanges(Ljava/util/Map;)V", at = @At("HEAD"))
    private void adapt$equipmentChanged(Map<EquipmentSlot, ItemStack> changed, CallbackInfo callback) {
        Map<EquipmentSlot, ItemStack> last = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : changed.keySet()) {
            EquipmentSlot.Type type = slot.getType();
            if (type == EquipmentSlot.Type.HAND) {
                last.put(slot, lastHandItemStacks.get(slot.getIndex()));
            } else if (type == EquipmentSlot.Type.HUMANOID_ARMOR) {
                last.put(slot, lastArmorItemStacks.get(slot.getIndex()));
            } else {
                last.put(slot, lastBodyItemStack);
            }
        }
        BodyHooks.equipmentChanged((LivingEntity) (Object) this, changed, last);
    }
}
