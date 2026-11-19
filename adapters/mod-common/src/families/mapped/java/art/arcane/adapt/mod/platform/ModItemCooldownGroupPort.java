package art.arcane.adapt.mod.platform;

import art.arcane.adapt.api.adaptation.ItemCooldownGroupPort;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

/** This version has no use cooldown component, so cooldowns stay per item type and no stack is ever stamped. */
public final class ModItemCooldownGroupPort implements ItemCooldownGroupPort {
    @Override
    public boolean stampGroup(ItemRef stack, Id group, float markerSeconds) {
        return false;
    }
}
