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

package art.arcane.adapt.api.telemetry;

/**
 * Tick-resolution wall clock for the one-second telemetry buckets. Refreshed from the tick
 * loop and from every sampled ability execution so it stays current without a tick loop.
 */
public final class AdaptTelemetryClock {
  private static volatile long millis = System.currentTimeMillis();

  private AdaptTelemetryClock() {
  }

  public static void refresh() {
    millis = System.currentTimeMillis();
  }

  public static long millis() {
    return millis;
  }
}
