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

package art.arcane.adapt.content.skill.golemancy;

import java.util.concurrent.ConcurrentHashMap;

public final class GolemXpCooldowns<K> {
  private final ConcurrentHashMap<K, Long> lastClaims = new ConcurrentHashMap<>();

  public boolean tryClaim(K key, long nowMillis, long cooldownMillis) {
    long cooldown = Math.max(0L, cooldownMillis);
    while (true) {
      Long last = lastClaims.get(key);
      if (last == null) {
        if (lastClaims.putIfAbsent(key, nowMillis) == null) {
          return true;
        }
        continue;
      }
      if (nowMillis - last < cooldown) {
        return false;
      }
      if (lastClaims.replace(key, last, nowMillis)) {
        return true;
      }
    }
  }

  public void sweep(long nowMillis, long cooldownMillis) {
    long cooldown = Math.max(0L, cooldownMillis);
    lastClaims.values().removeIf(last -> nowMillis - last >= cooldown);
  }
}
