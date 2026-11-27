package org.geysermc.floodgate.api;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stand-in for the Floodgate API, with the two members Adapt looks up by reflection. */
public final class FloodgateApi {
  public static final Set<UUID> BEDROCK = ConcurrentHashMap.newKeySet();
  private static final FloodgateApi INSTANCE = new FloodgateApi();

  private FloodgateApi() {
  }

  public static FloodgateApi getInstance() {
    return INSTANCE;
  }

  public boolean isFloodgatePlayer(UUID playerId) {
    return BEDROCK.contains(playerId);
  }
}
