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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RollingXpCap {
  public static final long WINDOW_MILLIS = 3_600_000L;

  private final Map<UUID, Ledger> ledgers = new HashMap<>();

  public synchronized double claim(UUID player, double requested, double cap, long nowMillis) {
    if (!isPositiveFinite(requested) || !isPositiveFinite(cap)) {
      return 0D;
    }
    Ledger ledger = ledgers.computeIfAbsent(player, ignored -> new Ledger());
    ledger.prune(nowMillis);
    double granted = Math.min(requested, cap - ledger.total);
    if (granted <= 0D) {
      return 0D;
    }
    ledger.grants.addLast(new Grant(nowMillis, granted));
    ledger.total += granted;
    return granted;
  }

  public synchronized void sweep(long nowMillis) {
    ledgers.values().removeIf(ledger -> {
      ledger.prune(nowMillis);
      return ledger.grants.isEmpty();
    });
  }

  private static boolean isPositiveFinite(double value) {
    return Double.isFinite(value) && value > 0D;
  }

  private record Grant(long atMillis, double amount) {
  }

  private static final class Ledger {
    private final ArrayDeque<Grant> grants = new ArrayDeque<>();
    private double total;

    private void prune(long nowMillis) {
      while (!grants.isEmpty() && nowMillis - grants.peekFirst().atMillis() >= WINDOW_MILLIS) {
        total -= grants.removeFirst().amount();
      }
      if (grants.isEmpty()) {
        total = 0D;
      }
    }
  }
}
