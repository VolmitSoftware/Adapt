package art.arcane.adapt.engine.world;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.util.common.io.SQLManager;

import java.util.UUID;

final class TestPersistenceHost implements PlayerDataPersistenceQueue.Host {
  @Override
  public SQLManager sqlManager() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getSqlManager();
  }

  @Override
  public void fenceLost(UUID playerId, UUID ownerToken, long epoch) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    if (server != null) {
      server.onPersistenceFenceLost(playerId, ownerToken, epoch);
    }
  }
}
