package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.bukkit.Bukkit;

/** Fires {@link AdaptAdaptationTeleportEvent} so other plugins can cancel or redirect an adaptation teleport. */
final class BukkitAdaptationTeleportPort implements AdaptationTeleportPort {
  @Override
  public Loc announce(PlayerRef player, Adaptation<?> adaptation, Loc from, Loc to) {
    AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(!Bukkit.isPrimaryThread(),
        AdaptCore.get().getAdaptServer().getPlayer(player), adaptation, BukkitRefs.location(from), BukkitRefs.location(to));
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
      return null;
    }
    return BukkitRefs.loc(event.getToLocation());
  }
}
