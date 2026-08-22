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

package art.arcane.adapt.api.potion;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records which potion effect types Adapt itself applied to a player.
 * <p>
 * Adaptations push effects straight onto the Bukkit entity, so without this ledger there is no way
 * to tell an adaptation buff apart from a potion the player drank. A reset needs to strip exactly
 * the former and leave the latter alone.
 */
public final class AdaptPotionRegistry {
  private static final Map<UUID, Set<PotionEffectType>> APPLIED = new ConcurrentHashMap<>();

  private AdaptPotionRegistry() {
  }

  public static void record(UUID playerId, PotionEffectType type) {
    if (playerId == null || type == null) {
      return;
    }

    APPLIED.computeIfAbsent(playerId, unused -> ConcurrentHashMap.newKeySet()).add(type);
  }

  public static Set<PotionEffectType> applied(UUID playerId) {
    Set<PotionEffectType> types = playerId == null ? null : APPLIED.get(playerId);
    return types == null ? Set.of() : Set.copyOf(types);
  }

  public static void forget(UUID playerId) {
    if (playerId != null) {
      APPLIED.remove(playerId);
    }
  }

  /**
   * Strips every effect Adapt applied to this player and forgets the ledger. Must run on the
   * player's owning thread. Returns the number of effects actually removed.
   */
  public static int strip(Player player) {
    if (player == null) {
      return 0;
    }

    Set<PotionEffectType> types = APPLIED.remove(player.getUniqueId());
    if (types == null || types.isEmpty()) {
      return 0;
    }

    int removed = 0;
    for (PotionEffectType type : types) {
      if (type != null && player.hasPotionEffect(type)) {
        player.removePotionEffect(type);
        removed++;
      }
    }
    return removed;
  }

  /**
   * Prunes the ledger down to effects still active on the player. Called on quit so the map does
   * not grow with every player ever seen, while effects that persist across relogs stay strippable.
   */
  public static void retainActive(Player player) {
    if (player == null) {
      return;
    }

    Set<PotionEffectType> types = APPLIED.get(player.getUniqueId());
    if (types == null) {
      return;
    }

    types.removeIf(type -> type == null || !player.hasPotionEffect(type));
    if (types.isEmpty()) {
      APPLIED.remove(player.getUniqueId(), types);
    }
  }

  public static void reset() {
    APPLIED.clear();
  }
}
