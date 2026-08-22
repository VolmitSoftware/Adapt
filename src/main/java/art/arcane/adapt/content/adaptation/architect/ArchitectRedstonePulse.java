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

package art.arcane.adapt.content.adaptation.architect;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lease registry for bound blocks that are currently emitting. Only the first
 * pulse of an emitter mutates block states; overlapping pulses extend the lease
 * so the block data captured before the first pulse is what gets restored.
 */
final class ArchitectRedstonePulse {
  static final int PULSE_TICKS = 4;
  private static final Object LEASE_LOCK = new Object();
  private static final Map<Emitter, Lease> ACTIVE_EMITTERS = new HashMap<>();
  private static long nextRuntimeId;
  private static long nextGeneration;

  private final long runtimeId;
  private boolean accepting = true;

  ArchitectRedstonePulse() {
    synchronized (LEASE_LOCK) {
      runtimeId = ++nextRuntimeId;
    }
  }

  Activation begin(Emitter emitter, List<Snapshot> snapshots) {
    if (emitter == null || snapshots == null || snapshots.isEmpty()) {
      return null;
    }

    synchronized (LEASE_LOCK) {
      if (!accepting) {
        return null;
      }

      Lease previous = ACTIVE_EMITTERS.get(emitter);
      List<Snapshot> restored = previous == null ? List.copyOf(snapshots) : previous.snapshots();
      Lease lease = new Lease(runtimeId, ++nextGeneration, restored);
      ACTIVE_EMITTERS.put(emitter, lease);
      return new Activation(emitter, lease, previous == null);
    }
  }

  boolean complete(Activation activation) {
    if (activation == null) {
      return false;
    }
    synchronized (LEASE_LOCK) {
      return ACTIVE_EMITTERS.remove(activation.emitter(), activation.lease());
    }
  }

  Restoration cancel(Emitter emitter) {
    synchronized (LEASE_LOCK) {
      Lease removed = ACTIVE_EMITTERS.remove(emitter);
      return removed == null ? null : new Restoration(emitter, removed.snapshots());
    }
  }

  boolean owns(Emitter emitter) {
    synchronized (LEASE_LOCK) {
      return ACTIVE_EMITTERS.containsKey(emitter);
    }
  }

  Set<Emitter> emitters() {
    synchronized (LEASE_LOCK) {
      return Set.copyOf(ACTIVE_EMITTERS.keySet());
    }
  }

  Set<Restoration> close() {
    synchronized (LEASE_LOCK) {
      accepting = false;
      Set<Restoration> restorations = new HashSet<>();
      Iterator<Map.Entry<Emitter, Lease>> iterator = ACTIVE_EMITTERS.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<Emitter, Lease> entry = iterator.next();
        if (entry.getValue().runtimeId() != runtimeId) {
          continue;
        }
        restorations.add(new Restoration(entry.getKey(), entry.getValue().snapshots()));
        iterator.remove();
      }
      return Set.copyOf(restorations);
    }
  }

  static Emitter emitter(UUID worldId, int x, int y, int z) {
    return worldId == null ? null : new Emitter(worldId, x, y, z);
  }

  static boolean isBindableFace(BlockFace face) {
    return face == BlockFace.NORTH
        || face == BlockFace.EAST
        || face == BlockFace.SOUTH
        || face == BlockFace.WEST
        || face == BlockFace.UP
        || face == BlockFace.DOWN;
  }

  record Activation(Emitter emitter, Lease lease, boolean firstPulse) {
    List<Snapshot> snapshots() {
      return lease.snapshots();
    }
  }

  record Restoration(Emitter emitter, List<Snapshot> snapshots) {
  }

  record Lease(long runtimeId, long generation, List<Snapshot> snapshots) {
  }

  /**
   * A single block touched by a pulse: where it is, what it was, and what the
   * pulse turned it into.
   */
  record Snapshot(int x, int y, int z, BlockData original, BlockData powered) {
  }

  record Emitter(UUID worldId, int x, int y, int z) {
    boolean isInChunk(UUID otherWorldId, int chunkX, int chunkZ) {
      return worldId.equals(otherWorldId) && (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }
  }
}
