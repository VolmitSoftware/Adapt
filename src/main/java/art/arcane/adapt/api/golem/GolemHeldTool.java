package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;

final class GolemHeldTool {
  private final GolemHandle handle;
  private String encoded;
  private ItemStack decoded;
  private boolean shown;

  GolemHeldTool(GolemHandle handle) {
    this.handle = Objects.requireNonNull(handle);
  }

  static void hide(Mob golem) {
    EntityEquipment equipment = golem.getEquipment();
    if (equipment != null) {
      equipment.setItemInMainHand(null);
    }
  }

  Optional<ItemStack> item() {
    String stored = handle.state().held();
    if (stored == null) {
      return Optional.empty();
    }
    if (!stored.equals(encoded)) {
      try {
        decoded = handle.platform().decodeItem(stored);
      } catch (RuntimeException corrupt) {
        Adapt.warn("Skipping the unreadable held tool of golem " + handle.id() + "; the stored entry is kept.", corrupt);
        decoded = null;
      }
      encoded = stored;
    }
    return decoded == null ? Optional.empty() : Optional.of(decoded.clone());
  }

  boolean hold(ItemStack next) {
    ItemStack before = item().orElse(null);
    String stored = next == null ? null : handle.platform().encodeItem(next);
    if (!handle.setHeldEncoded(stored)) {
      return false;
    }
    encoded = stored;
    decoded = next == null ? null : next.clone();
    show(before, next);
    return true;
  }

  void sync() {
    if (shown) {
      return;
    }
    shown = true;
    item().ifPresent(tool -> show(tool, tool));
  }

  boolean wear(DoubleSupplier random) {
    ItemStack tool = item().orElse(null);
    ItemMeta meta = tool == null ? null : tool.getItemMeta();
    if (!(meta instanceof Damageable damageable) || damageable.isUnbreakable()) {
      return false;
    }
    int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : tool.getType().getMaxDurability();
    if (maxDamage <= 0) {
      return false;
    }
    GolemToolRules.Wear wear = GolemToolRules.wear(damageable.getDamage(), maxDamage, GolemToolEnchants.unbreaking(tool), random.getAsDouble());
    if (!wear.worn()) {
      return false;
    }
    if (wear.broken()) {
      handle.entity().broadcastSlotBreak(EquipmentSlot.HAND);
      hold(null);
      return true;
    }
    damageable.setDamage(wear.damage());
    tool.setItemMeta(damageable);
    hold(tool);
    return false;
  }

  private void show(ItemStack before, ItemStack next) {
    Mob golem = handle.entity();
    EntityEquipment equipment = golem.getEquipment();
    if (equipment == null) {
      return;
    }
    ItemStack current = equipment.getItemInMainHand();
    if (before == null && current != null && current.getType() != Material.AIR) {
      Location at = golem.getLocation();
      golem.getWorld().dropItemNaturally(at, current);
    }
    equipment.setItemInMainHand(next == null ? null : next.clone());
    equipment.setItemInMainHandDropChance(0F);
  }
}
