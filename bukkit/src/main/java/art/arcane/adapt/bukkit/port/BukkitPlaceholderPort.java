package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlaceholderPort;
import art.arcane.adapt.papi.AdaptPlaceholders;

import java.util.UUID;

public final class BukkitPlaceholderPort implements PlaceholderPort {
  @Override
  public void publishPlayer(AdaptPlayer player) {
    AdaptPlaceholders.get().publishPlayer(player);
  }

  @Override
  public void evictNow(UUID playerId) {
    AdaptPlaceholders.get().evictNow(playerId);
  }

  @Override
  public void evictAfterGrace(UUID playerId) {
    AdaptPlaceholders.get().evictAfterGrace(playerId);
  }
}
