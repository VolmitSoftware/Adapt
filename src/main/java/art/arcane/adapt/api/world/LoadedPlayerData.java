package art.arcane.adapt.api.world;

import java.util.Objects;
import java.util.UUID;

record LoadedPlayerData(PlayerData data, UUID ownerToken, long epoch, long sequence) {
  LoadedPlayerData {
    Objects.requireNonNull(data);
    if (ownerToken == null) {
      if (epoch != -1L || sequence != 0L) {
        throw new IllegalArgumentException("Unowned player data cannot carry fence state");
      }
    } else if (epoch < 1L || sequence < 0L) {
      throw new IllegalArgumentException("Owned player data requires a positive epoch and nonnegative sequence");
    }
  }

  static LoadedPlayerData inspected(PlayerData data) {
    return new LoadedPlayerData(data, null, -1L, 0L);
  }

  static LoadedPlayerData owned(PlayerData data, UUID ownerToken, long epoch, long sequence) {
    return new LoadedPlayerData(data, Objects.requireNonNull(ownerToken), epoch, sequence);
  }

  boolean isOwned() {
    return ownerToken != null;
  }
}
