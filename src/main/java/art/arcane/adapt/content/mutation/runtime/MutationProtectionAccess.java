package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.protection.ProtectorRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

final class MutationProtectionAccess {
  boolean canAffect(Player actor, Entity target) {
    if (actor == null || target == null || !target.isValid()) {
      return false;
    }
    return canAffectAt(actor, target.getLocation(), target instanceof Player);
  }

  boolean canAffectAt(Player actor, Location targetLocation, boolean playerTarget) {
    if (actor == null || !valid(targetLocation)) {
      return false;
    }
    for (Protector protector : protectors()) {
      boolean allowed = playerTarget
          ? protector.canPVP(actor, targetLocation, null)
          : protector.canPVE(actor, targetLocation, null);
      if (!allowed) {
        return false;
      }
    }
    return true;
  }

  boolean canBreak(Player actor, Location location) {
    if (actor == null || !valid(location)) {
      return false;
    }
    for (Protector protector : protectors()) {
      if (!protector.canBlockBreak(actor, location, null)) {
        return false;
      }
    }
    return true;
  }

  boolean canPlace(Player actor, Location location) {
    if (actor == null || !valid(location)) {
      return false;
    }
    for (Protector protector : protectors()) {
      if (!protector.canBlockPlace(actor, location, null)) {
        return false;
      }
    }
    return true;
  }

  boolean canInteract(Player actor, Location location) {
    if (actor == null || !valid(location)) {
      return false;
    }
    for (Protector protector : protectors()) {
      if (!protector.canInteract(actor, location, null)) {
        return false;
      }
    }
    return true;
  }

  boolean canOccupy(Player actor, Location location) {
    if (actor == null || !valid(location)) {
      return false;
    }
    for (Protector protector : protectors()) {
      if (!protector.checkRegion(actor, location, null)) {
        return false;
      }
    }
    return true;
  }

  private List<Protector> protectors() {
    ProtectorRegistry registry = Adapt.instance == null ? null : Adapt.instance.getProtectorRegistry();
    return registry == null ? List.of() : registry.getDefaultProtectors();
  }

  private boolean valid(Location location) {
    return location != null && location.getWorld() != null && location.isWorldLoaded();
  }
}
