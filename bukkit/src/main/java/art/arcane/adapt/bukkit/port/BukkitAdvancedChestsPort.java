package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.entity.BukkitInventoryRef;
import art.arcane.adapt.engine.integration.AdvancedChestsPort;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI;
import us.lynuxcraft.deadsilenceiv.advancedchests.chest.AdvancedChest;

/** AdvancedChests lookups and page opening for Rift Access. */
final class BukkitAdvancedChestsPort implements AdvancedChestsPort {
  @Override
  public Lookup find(Loc block) {
    if (!Bukkit.getPluginManager().isPluginEnabled("AdvancedChests")) {
      return Lookup.ABSENT;
    }
    Location target = BukkitRefs.location(block);
    try {
      return AdvancedChestsAPI.getChestManager().getAdvancedChest(target) == null ? Lookup.ABSENT : Lookup.PRESENT;
    } catch (Throwable error) {
      AdaptLog.error("Failed to resolve AdvancedChests container for Rift Access at " + target + ".");
      AdaptLog.error(error);
      return Lookup.FAILED;
    }
  }

  @Override
  public InventoryRef open(PlayerRef player, Loc block) {
    if (!Bukkit.getPluginManager().isPluginEnabled("AdvancedChests")) {
      return null;
    }
    AdvancedChest<?, ?> chest = AdvancedChestsAPI.getChestManager().getAdvancedChest(BukkitRefs.location(block));
    if (chest == null) {
      return null;
    }
    Player viewer = BukkitRefs.player(player);
    Inventory previousTop = viewer.getOpenInventory().getTopInventory();
    chest.openPage(viewer, 1);
    InventoryView opened = viewer.getOpenInventory();
    if (opened == null || opened.getTopInventory() == previousTop) {
      return null;
    }
    return new BukkitInventoryRef(opened.getTopInventory());
  }
}
