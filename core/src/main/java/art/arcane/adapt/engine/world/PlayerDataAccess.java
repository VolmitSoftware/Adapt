package art.arcane.adapt.engine.world;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.Nullable;

/**
 * The engine player records the built-in content still reads directly: XP pools, discovery notes, seen chunks and other
 * bookkeeping no public member covers yet. Null for a player without loaded data.
 */
public final class PlayerDataAccess {
  private PlayerDataAccess() {
  }

  public static @Nullable AdaptPlayer player(@Nullable PlayerRef player) {
    AdaptCore core = AdaptCore.current();
    if (player == null || core == null || core.getAdaptServer() == null) {
      return null;
    }
    AdaptPlayer found = core.getAdaptServer().getPlayer(player);
    return found != null && found.isRuntimeReady() ? found : null;
  }

  public static @Nullable PlayerData data(@Nullable PlayerRef player) {
    AdaptPlayer found = player(player);
    return found == null ? null : found.getData();
  }
}
