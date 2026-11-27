package art.arcane.adapt.mod.platform;

import art.arcane.adapt.engine.ComponentPort;
import art.arcane.adapt.engine.protection.ProtectionProbes;
import art.arcane.adapt.mod.event.PlayerHooks;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ModComponentPort implements ComponentPort {
    @Override
    public boolean isPickupLocked(ItemEntityRef item) {
        return !item.canPlayerPickup();
    }

    @Override
    public boolean attemptItemPickup(PlayerRef player, ItemEntityRef item, ItemRef stack) {
        if (player == null || item == null || stack == null) {
            return false;
        }
        return ProtectionProbes.attemptItemPickup(player, item, ProtectionProbes.remainingAfterPickup(player.inventory(), stack));
    }

    @Override
    public void damageItem(PlayerRef player, EquipmentSlot slot, int amount) {
        if (player == null || slot == null || amount <= 0) {
            return;
        }
        ServerPlayer handle = ModRefs.player(player);
        net.minecraft.world.entity.EquipmentSlot vanilla = ModConversions.slot(slot);
        ItemStack stack = handle.getItemBySlot(vanilla);
        if (!stack.isEmpty()) {
            stack.hurtAndBreak(amount, handle, vanilla);
        }
    }

    @Override
    public int awardExperience(PlayerRef player, int points) {
        if (player == null || points <= 0) {
            return 0;
        }
        ServerPlayer handle = ModRefs.player(player);
        int awarded = Math.max(0, PlayerHooks.experienceGained(handle, points));
        if (awarded > 0) {
            handle.giveExperiencePoints(awarded);
        }
        return awarded;
    }

    @Override
    public void setTotalExperience(PlayerRef player, int total) {
        if (player != null) {
            ModRefs.player(player).totalExperience = Math.max(0, total);
        }
    }
}
