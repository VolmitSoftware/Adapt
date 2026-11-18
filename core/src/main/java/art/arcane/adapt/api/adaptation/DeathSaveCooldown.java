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

package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;

public enum DeathSaveCooldown {
  LAST_RITES("tragoul_last_rites"),
  HOURGLASS_GUARD("chronos_hourglass_guard"),
  VOID_SKIN("rift_void_skin");

  private final DataKey<Long> deadlineKey;

  DeathSaveCooldown(String name) {
    deadlineKey = DataKey.adapt("death_save_" + name, DataCodecs.LONG);
  }

  public boolean isReady(PlayerRef player, long now) {
    return remaining(player, now) == 0L;
  }

  public long remaining(PlayerRef player, long now) {
    try {
      DataHolder data = player.data();
      if (!data.has(deadlineKey) && data.hasAny(deadlineKey.id())) {
        throw new IllegalArgumentException("The value stored under " + deadlineKey.id() + " is not a LONG");
      }
      long deadline = data.getOrDefault(deadlineKey, 0L);
      return deadline <= now ? 0L : deadline - now;
    } catch (RuntimeException exception) {
      reportFailure(player, exception);
      return Long.MAX_VALUE;
    }
  }

  public boolean activate(PlayerRef player, long now, long durationMillis) {
    if (!isReady(player, now)) {
      return false;
    }
    long duration = Math.max(0L, durationMillis);
    long deadline = now > Long.MAX_VALUE - duration ? Long.MAX_VALUE : now + duration;
    try {
      player.data().set(deadlineKey, deadline);
      return true;
    } catch (RuntimeException exception) {
      reportFailure(player, exception);
      return false;
    }
  }

  private void reportFailure(PlayerRef player, RuntimeException exception) {
    AdaptLog.error("Could not persist death-save cooldown " + deadlineKey.id() + " for " + player.uuid() + ".", exception);
  }
}
