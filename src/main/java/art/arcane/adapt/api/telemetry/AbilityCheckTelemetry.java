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

import java.util.ArrayDeque;

public final class AbilityCheckTelemetry {
    private static final long WINDOW_MS = 60_000L;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<Long> checkOps = new ArrayDeque<>();
    private static final ArrayDeque<Long> successfulOps = new ArrayDeque<>();

    private AbilityCheckTelemetry() {
    }

    public static void recordCheckAttempt() {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            checkOps.addLast(now);
            trim(checkOps, now);
            trim(successfulOps, now);
        }
    }

    public static void recordSuccessfulCheck() {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            successfulOps.addLast(now);
            trim(checkOps, now);
            trim(successfulOps, now);
        }
    }

    public static long checksPerMinute(long now) {
        synchronized (LOCK) {
            trim(checkOps, now);
            return checkOps.size();
        }
    }

    public static long successfulChecksPerMinute(long now) {
        synchronized (LOCK) {
            trim(successfulOps, now);
            return successfulOps.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            checkOps.clear();
            successfulOps.clear();
        }
    }

    private static void trim(ArrayDeque<Long> samples, long now) {
        while (!samples.isEmpty() && (now - samples.peekFirst()) > WINDOW_MS) {
            samples.removeFirst();
        }
    }
}
