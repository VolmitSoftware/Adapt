package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.volmlib.util.bukkit.BukkitExperience;
import com.francobm.magicosmetics.api.CosmeticType;
import com.francobm.magicosmetics.api.MagicAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Paper 1.20.1 cannot compute or set a player's total experience points, so those use the core arithmetic. */
final class BukkitComponentPort implements ComponentPort {
  @Override
  public boolean cosmeticCovers(PlayerRef player, EquipmentSlot slot) {
    Player bukkitPlayer = BukkitRefs.player(player);
    if (bukkitPlayer == null || slot == null || !Bukkit.getPluginManager().isPluginEnabled("MagicCosmetics")) {
      return false;
    }
    return switch (slot) {
      case HEAD -> MagicAPI.hasEquipCosmetic(bukkitPlayer, CosmeticType.HAT);
      case CHEST -> MagicAPI.hasEquipCosmetic(bukkitPlayer, CosmeticType.BAG);
      default -> false;
    };
  }

  @Override
  public void damageItem(PlayerRef player, EquipmentSlot slot, int amount) {
    Player bukkitPlayer = BukkitRefs.player(player);
    if (bukkitPlayer == null || slot == null || amount <= 0) {
      return;
    }
    bukkitPlayer.damageItemStack(BukkitConversions.slot(slot), amount);
  }

  @Override
  public boolean isPickupLocked(ItemEntityRef item) {
    Entity entity = BukkitRefs.entity(item);
    if (!(entity instanceof Item bukkitItem)) {
      return false;
    }
    if (bukkitItem.hasMetadata("NPC") || bukkitItem.hasMetadata("shopitem") || bukkitItem.hasMetadata("hologram")) {
      return true;
    }
    try {
      return !bukkitItem.canPlayerPickup();
    } catch (NoSuchMethodError ignored) {
      return false;
    }
  }

  @Override
  public boolean attemptItemPickup(PlayerRef player, ItemEntityRef item, ItemRef stack) {
    Player bukkitPlayer = BukkitRefs.player(player);
    Entity entity = BukkitRefs.entity(item);
    if (bukkitPlayer == null || !(entity instanceof Item bukkitItem) || stack == null) {
      return false;
    }
    ItemStack nativeStack = BukkitRefs.item(stack);
    int remaining = ProtectionEventProbe.remainingAfterPickup(bukkitPlayer.getInventory(), nativeStack);
    return ProtectionEventProbe.attemptItemPickup(bukkitPlayer, bukkitItem, remaining);
  }

  @Override
  public int awardExperience(PlayerRef player, int points) {
    Player bukkitPlayer = BukkitRefs.player(player);
    if (bukkitPlayer == null || points <= 0) {
      return 0;
    }
    return BukkitExperience.award(bukkitPlayer, points);
  }

  @Override
  public void setTotalExperience(PlayerRef player, int total) {
    Player bukkitPlayer = BukkitRefs.player(player);
    if (bukkitPlayer != null) {
      bukkitPlayer.setTotalExperience(total);
    }
  }
}
