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

package art.arcane.adapt.engine.protection;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionPolicyService {
  private static final RegionPolicy FAILED_SOURCE_POLICY = new RegionPolicy(false,
      RegionPolicy.DEFAULT.xpMultiplier(), RegionPolicy.DEFAULT.powerBonus(), RegionPolicy.DEFAULT.unlockedAdaptations());
  private static final AtomicReference<RegionPolicySource> SOURCE = new AtomicReference<>();
  private static final AtomicBoolean QUARANTINED = new AtomicBoolean();

  private RegionPolicyService() {
  }

  public static void install(RegionPolicySource source) {
    QUARANTINED.set(false);
    SOURCE.set(source);
  }

  public static void clear() {
    SOURCE.set(null);
    QUARANTINED.set(false);
  }

  public static boolean isActive() {
    return SOURCE.get() != null && !QUARANTINED.get();
  }

  public static RegionPolicy resolve(PlayerRef player, Loc location) {
    RegionPolicySource source = SOURCE.get();
    if (source == null) {
      return RegionPolicy.DEFAULT;
    }
    if (QUARANTINED.get()) {
      return FAILED_SOURCE_POLICY;
    }
    if (player == null || location == null) {
      return RegionPolicy.DEFAULT;
    }

    try {
      RegionPolicy policy = source.resolve(player, location);
      return policy == null ? RegionPolicy.DEFAULT : policy;
    } catch (Throwable error) {
      quarantine(source, error);
      return FAILED_SOURCE_POLICY;
    }
  }

  public static double adjustXp(double xp, RegionPolicy policy) {
    if (policy == null || !policy.xpAllowed()) {
      return 0D;
    }

    return xp * policy.xpMultiplier();
  }

  private static void quarantine(RegionPolicySource source, Throwable error) {
    if (!QUARANTINED.compareAndSet(false, true)) {
      return;
    }

    AdaptLog.warn("Region policy source " + source.getName() + " failed; gameplay XP is paused and other region flags use defaults until plugin reload: "
        + error.getClass().getSimpleName()
        + (error.getMessage() == null ? "" : " - " + error.getMessage()));
    AdaptLog.error(error);
  }
}
