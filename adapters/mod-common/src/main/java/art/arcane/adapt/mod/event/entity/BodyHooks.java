package art.arcane.adapt.mod.event.entity;

import art.arcane.adapt.mod.event.ModEvents;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.event.entity.EntityEquipmentChangedEvent;
import art.arcane.adapt.platform.event.entity.EntityInsideBlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;

/** Blocks an entity stands inside, and equipment changes vanilla detects each tick. */
public final class BodyHooks {
    private BodyHooks() {
    }

    /** True when a handler cancelled; the block's inside effect is then skipped. Air is never posted. */
    public static boolean inside(Entity entity, BlockState state, BlockPos pos) {
        if (state.isAir() || !(entity.level() instanceof ServerLevel level) || !ModEvents.listening(EntityInsideBlockEvent.class)) {
            return false;
        }
        return ModEvents.post(new EntityInsideBlockEvent(ModRefs.entity(entity), ModRefs.block(level, pos.immutable()))).isCancelled();
    }

    /** Before vanilla stores and sends the changed equipment; {@code last} still holds the previous items. */
    public static void equipmentChanged(LivingEntity entity, Map<EquipmentSlot, ItemStack> changed, Map<EquipmentSlot, ItemStack> last) {
        if (!(entity.level() instanceof ServerLevel) || !ModEvents.listening(EntityEquipmentChangedEvent.class)) {
            return;
        }
        Map<art.arcane.adapt.platform.entity.EquipmentSlot, EntityEquipmentChangedEvent.Change> changes =
            new EnumMap<>(art.arcane.adapt.platform.entity.EquipmentSlot.class);
        for (Map.Entry<EquipmentSlot, ItemStack> entry : changed.entrySet()) {
            ItemStack previous = last.get(entry.getKey());
            changes.put(ModConversions.slot(entry.getKey()), new EntityEquipmentChangedEvent.Change(
                ModRefs.item(previous == null ? ItemStack.EMPTY : previous.copy()), ModRefs.item(entry.getValue().copy())));
        }
        ModEvents.post(new EntityEquipmentChangedEvent(ModRefs.living(entity), changes));
    }
}
