package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.api.adaptation.EntityGuardPort;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.nativelib.player.PlayerClientAccess;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

final class BukkitEntityGuardPort implements EntityGuardPort {
  @Override
  public boolean isServerPlayer(PlayerRef player) {
    Player bukkitPlayer = BukkitRefs.player(player);
    return bukkitPlayer != null && PlayerClientAccess.matchesServerPlayer(bukkitPlayer);
  }

  @Override
  public boolean isShielded(EntityRef entity) {
    Entity target = BukkitRefs.entity(entity);
    if (target == null) {
      return false;
    }
    if (target instanceof ArmorStand stand && stand.isMarker()) {
      return true;
    }
    return target.hasMetadata("NPC") || MinionIdentity.isMinion(BukkitRefs.entity(target));
  }
}
