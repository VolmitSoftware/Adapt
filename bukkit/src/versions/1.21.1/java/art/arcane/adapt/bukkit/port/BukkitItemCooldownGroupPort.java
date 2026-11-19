package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.adaptation.ItemCooldownGroupPort;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

/** Items carry no use-cooldown component before Minecraft 1.21.2, so no stack can join a cooldown group. */
final class BukkitItemCooldownGroupPort implements ItemCooldownGroupPort {
  @Override
  public boolean stampGroup(ItemRef stack, Id group, float markerSeconds) {
    return false;
  }
}
