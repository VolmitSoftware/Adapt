package art.arcane.adapt.mod.platform;

import art.arcane.adapt.engine.adaptation.ItemCooldownGroupPort;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseCooldown;

public final class ModItemCooldownGroupPort implements ItemCooldownGroupPort {
    @Override
    public boolean stampGroup(ItemRef stack, Id group, float markerSeconds) {
        if (stack == null || group == null) {
            return false;
        }
        ItemStack item = ModRefs.item(stack);
        if (item.isEmpty()) {
            return false;
        }
        Identifier key = ModConversions.identifier(group);
        UseCooldown current = item.get(DataComponents.USE_COOLDOWN);
        if (current != null && current.cooldownGroup().filter(key::equals).isPresent()) {
            return false;
        }
        item.set(DataComponents.USE_COOLDOWN, new UseCooldown(markerSeconds, Optional.of(key)));
        return true;
    }
}
