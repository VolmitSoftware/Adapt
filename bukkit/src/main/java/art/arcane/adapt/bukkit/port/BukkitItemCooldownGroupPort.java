package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.adaptation.ItemCooldownGroupPort;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.item.ItemRef;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;

final class BukkitItemCooldownGroupPort implements ItemCooldownGroupPort {
  @Override
  public boolean stampGroup(ItemRef stack, Id group, float markerSeconds) {
    if (stack == null || group == null) {
      return false;
    }
    ItemStack item = BukkitRefs.item(stack);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return false;
    }
    NamespacedKey key = BukkitRefs.key(group);
    if (meta.hasUseCooldown() && key.equals(meta.getUseCooldown().getCooldownGroup())) {
      return false;
    }
    UseCooldownComponent component = meta.getUseCooldown();
    component.setCooldownSeconds(markerSeconds);
    component.setCooldownGroup(key);
    meta.setUseCooldown(component);
    item.setItemMeta(meta);
    return true;
  }
}
