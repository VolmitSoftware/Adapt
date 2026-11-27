package art.arcane.adapt.engine.world;

import java.util.UUID;

/** Publishes player snapshots to a placeholder integration such as PlaceholderAPI. */
public interface PlaceholderPort {
  PlaceholderPort NONE = new PlaceholderPort() {
    @Override
    public void publishPlayer(AdaptPlayer player) {
    }

    @Override
    public void evictNow(UUID playerId) {
    }

    @Override
    public void evictAfterGrace(UUID playerId) {
    }
  };

  void publishPlayer(AdaptPlayer player);

  void evictNow(UUID playerId);

  void evictAfterGrace(UUID playerId);
}
