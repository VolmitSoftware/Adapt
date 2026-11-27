package art.arcane.adapt.content.protector;

import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A protection plugin Adapt integrates with on Bukkit, answering each check in Bukkit terms. Every check it does not
 * override allows. Registered through {@code AdaptApi.protection()} like any other protector.
 */
public abstract class BukkitProtector implements Protector {
  @Override
  public final boolean allows(ProtectionCheck check, PlayerRef player, Loc location, @Nullable EntityRef victim,
                              Id adaptation) {
    return allows(check, BukkitRefs.player(player), BukkitRefs.location(location), BukkitRefs.entity(victim));
  }

  /** The same check for Bukkit callers that act for no adaptation. */
  public final boolean allows(ProtectionCheck check, Player player, Location location, @Nullable Entity victim) {
    if (player == null || location == null) {
      return true;
    }
    return switch (check) {
      case BLOCK_BREAK -> canBlockBreak(player, location);
      case BLOCK_PLACE -> canBlockPlace(player, location);
      case PVP -> canPVP(player, location);
      case PVE -> victim != null ? canPVE(player, victim) : canPVE(player, location);
      case INTERACT -> canInteract(player, location);
      case ACCESS_CHEST -> canAccessChest(player, location);
      case REGION -> checkRegion(player, location);
    };
  }

  /** Releases what the protector holds in the protection plugin; called when Adapt disables. */
  public void unregister() {
  }

  protected boolean canBlockBreak(Player player, Location blockLocation) {
    return true;
  }

  protected boolean canBlockPlace(Player player, Location blockLocation) {
    return true;
  }

  protected boolean canPVP(Player player, Location victimLocation) {
    return true;
  }

  protected boolean canPVE(Player player, Location victimLocation) {
    return true;
  }

  protected boolean canPVE(Player player, Entity victim) {
    return canPVE(player, victim.getLocation());
  }

  protected boolean canInteract(Player player, Location targetLocation) {
    return true;
  }

  protected boolean canAccessChest(Player player, Location chestLocation) {
    return true;
  }

  protected boolean checkRegion(Player player, Location location) {
    return true;
  }
}
