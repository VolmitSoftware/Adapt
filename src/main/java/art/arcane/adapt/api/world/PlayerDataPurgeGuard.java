/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.world;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Marks players whose Adapt data was purged so no stale in-memory copy can write it back.
 * <p>
 * A purge deletes the persisted copy asynchronously, but the plugin keeps player data in memory for
 * up to a minute after quit and re-adopts it on rejoin. Without this guard those copies re-persist
 * the deleted state and the purge silently unwinds itself. Every load and every save consults the
 * guard, so a purged player resolves to default data until a fresh instance is loaded for them.
 */
public final class PlayerDataPurgeGuard {
  private static final Set<UUID> PURGED = ConcurrentHashMap.newKeySet();
  private static final Map<UUID, Long> GENERATIONS = new ConcurrentHashMap<>();
  private static final AtomicLong GENERATION_SEQUENCE = new AtomicLong();

  private PlayerDataPurgeGuard() {
  }

  public static long mark(UUID playerId) {
    if (playerId == null) {
      return 0L;
    }

    long generation = GENERATION_SEQUENCE.incrementAndGet();
    GENERATIONS.put(playerId, generation);
    PURGED.add(playerId);
    return generation;
  }

  public static boolean isPurged(UUID playerId) {
    return playerId != null && PURGED.contains(playerId);
  }

  /**
   * Drops the mark once a fresh copy has been loaded for the player. Returns true when a mark was
   * actually cleared so callers can log the resurrection they just prevented.
   */
  public static boolean clear(UUID playerId) {
    return playerId != null && PURGED.remove(playerId);
  }

  public static long generation(UUID playerId) {
    return playerId == null ? 0L : GENERATIONS.getOrDefault(playerId, 0L);
  }

  public static boolean allowsSave(UUID playerId, long expectedGeneration) {
    return playerId != null && !PURGED.contains(playerId) && generation(playerId) == expectedGeneration;
  }

  public static int size() {
    return PURGED.size();
  }

  public static void reset() {
    PURGED.clear();
    GENERATIONS.clear();
    GENERATION_SEQUENCE.set(0L);
  }
}
