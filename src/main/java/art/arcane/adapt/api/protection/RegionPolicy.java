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

package art.arcane.adapt.api.protection;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public record RegionPolicy(boolean xpAllowed, double xpMultiplier, int powerBonus, Set<String> unlockedAdaptations) {
  public static final String UNLOCK_ALL = "*";
  public static final double MIN_XP_MULTIPLIER = 0D;
  public static final double MAX_XP_MULTIPLIER = 1000D;
  public static final int MIN_POWER_BONUS = -4096;
  public static final int MAX_POWER_BONUS = 4096;
  public static final RegionPolicy DEFAULT = new RegionPolicy(true, 1D, 0, Set.of());

  public RegionPolicy {
    xpMultiplier = Double.isFinite(xpMultiplier)
        ? Math.max(MIN_XP_MULTIPLIER, Math.min(MAX_XP_MULTIPLIER, xpMultiplier))
        : 1D;
    powerBonus = Math.max(MIN_POWER_BONUS, Math.min(MAX_POWER_BONUS, powerBonus));
    unlockedAdaptations = normalize(unlockedAdaptations);
  }

  public boolean unlocksEverything() {
    return unlockedAdaptations.contains(UNLOCK_ALL);
  }

  public boolean grantsAnyAdaptation() {
    return !unlockedAdaptations.isEmpty();
  }

  private static Set<String> normalize(Set<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return Set.of();
    }

    Set<String> normalized = new LinkedHashSet<>(ids.size());
    for (String id : ids) {
      if (id == null) {
        continue;
      }

      String trimmed = id.trim().toLowerCase(Locale.ROOT);
      if (!trimmed.isEmpty()) {
        normalized.add(trimmed);
      }
    }

    return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
  }
}
