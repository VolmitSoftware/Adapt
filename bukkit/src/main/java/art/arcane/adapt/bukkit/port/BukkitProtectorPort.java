package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.protection.ProtectorPort;
import art.arcane.adapt.api.protection.ProtectorRegistry;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class BukkitProtectorPort implements ProtectorPort {
  @Override
  public List<String> defaultProtectors() {
    ProtectorRegistry registry = registry();
    return registry == null ? List.of() : names(registry.getDefaultProtectors());
  }

  @Override
  public List<String> allProtectors() {
    ProtectorRegistry registry = registry();
    return registry == null ? List.of() : names(registry.getAllProtectors());
  }

  @Override
  public boolean allows(Set<String> protectors, ProtectionCheck check, PlayerRef player, Loc location,
                        EntityRef victim, Adaptation<?> adaptation) {
    ProtectorRegistry registry = registry();
    if (registry == null || protectors == null || protectors.isEmpty()) {
      return true;
    }
    Player bukkitPlayer = BukkitRefs.player(player);
    Location bukkitLocation = BukkitRefs.location(location);
    Entity bukkitVictim = BukkitRefs.entity(victim);
    for (Protector protector : registry.getAllProtectors()) {
      if (protector == null || !protectors.contains(protector.getName())) {
        continue;
      }
      if (!allows(protector, check, bukkitPlayer, bukkitLocation, bukkitVictim, adaptation)) {
        return false;
      }
    }
    return true;
  }

  private static boolean allows(Protector protector, ProtectionCheck check, Player player, Location location,
                                Entity victim, Adaptation<?> adaptation) {
    return switch (check) {
      case BLOCK_BREAK -> protector.canBlockBreak(player, location, adaptation);
      case BLOCK_PLACE -> protector.canBlockPlace(player, location, adaptation);
      case PVP -> protector.canPVP(player, location, adaptation);
      case PVE -> victim != null
          ? protector.canPVE(player, victim, adaptation)
          : protector.canPVE(player, location, adaptation);
      case INTERACT -> protector.canInteract(player, location, adaptation);
      case ACCESS_CHEST -> protector.canAccessChest(player, location, adaptation);
      case REGION -> protector.checkRegion(player, location, adaptation);
    };
  }

  private static List<String> names(List<Protector> protectors) {
    List<String> names = new ArrayList<>(protectors.size());
    for (Protector protector : protectors) {
      if (protector != null) {
        names.add(protector.getName());
      }
    }
    return names;
  }

  private static ProtectorRegistry registry() {
    Adapt active = Adapt.instance;
    return active == null ? null : active.getProtectorRegistry();
  }
}
