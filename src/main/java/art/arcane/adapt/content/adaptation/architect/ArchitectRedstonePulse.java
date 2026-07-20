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

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ArchitectRedstonePulse {
  static final int PULSE_TICKS = 4;
  private static final int AIR_KIND_SHIFT = 30;
  private static final int COORDINATE_MASK = 0x3FFFFFFF;
  private static final Object LEASE_LOCK = new Object();
  private static final Map<Receiver, Lease> ACTIVE_RECEIVERS = new HashMap<>();
  private static long nextRuntimeId;
  private static long nextGeneration;

  private final long runtimeId;
  private boolean accepting = true;

  ArchitectRedstonePulse() {
    synchronized (LEASE_LOCK) {
      runtimeId = ++nextRuntimeId;
    }
  }

  Activation begin(Receiver receiver, Material originalMaterial) {
    if (receiver == null) {
      return null;
    }

    synchronized (LEASE_LOCK) {
      if (!accepting) {
        return null;
      }

      Lease previous = ACTIVE_RECEIVERS.get(receiver);
      if (previous == null && !isAir(originalMaterial)) {
        return null;
      }
      Material restoredMaterial = previous == null ? originalMaterial : previous.originalMaterial();
      Lease lease = new Lease(runtimeId, ++nextGeneration, restoredMaterial);
      ACTIVE_RECEIVERS.put(receiver, lease);
      return new Activation(receiver, lease, previous == null);
    }
  }

  boolean complete(Activation activation) {
    if (activation == null) {
      return false;
    }
    synchronized (LEASE_LOCK) {
      return ACTIVE_RECEIVERS.remove(activation.receiver(), activation.lease());
    }
  }

  Restoration cancel(Receiver receiver) {
    synchronized (LEASE_LOCK) {
      Lease removed = ACTIVE_RECEIVERS.remove(receiver);
      return removed == null ? null : new Restoration(receiver, removed.originalMaterial());
    }
  }

  boolean owns(Receiver receiver) {
    synchronized (LEASE_LOCK) {
      return ACTIVE_RECEIVERS.containsKey(receiver);
    }
  }

  Material originalMaterial(Receiver receiver) {
    synchronized (LEASE_LOCK) {
      Lease lease = ACTIVE_RECEIVERS.get(receiver);
      return lease == null ? null : lease.originalMaterial();
    }
  }

  Set<Receiver> receivers() {
    synchronized (LEASE_LOCK) {
      return Set.copyOf(ACTIVE_RECEIVERS.keySet());
    }
  }

  Set<Restoration> close() {
    synchronized (LEASE_LOCK) {
      accepting = false;
      Set<Restoration> restorations = new HashSet<>();
      Iterator<Map.Entry<Receiver, Lease>> iterator = ACTIVE_RECEIVERS.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<Receiver, Lease> entry = iterator.next();
        if (entry.getValue().runtimeId() != runtimeId) {
          continue;
        }
        restorations.add(new Restoration(entry.getKey(), entry.getValue().originalMaterial()));
        iterator.remove();
      }
      return Set.copyOf(restorations);
    }
  }

  static Receiver receiver(UUID worldId, int targetX, int targetY, int targetZ, BlockFace face) {
    if (!isReceiverFace(face)) {
      return null;
    }

    return new Receiver(
        worldId,
        targetX + face.getModX(),
        targetY + face.getModY(),
        targetZ + face.getModZ()
    );
  }

  static boolean isReceiverFace(BlockFace face) {
    return face == BlockFace.NORTH
        || face == BlockFace.EAST
        || face == BlockFace.SOUTH
        || face == BlockFace.WEST
        || face == BlockFace.UP
        || face == BlockFace.DOWN;
  }

  static int encodeBlock(int localX, int y, int localZ, int minHeight, Material originalMaterial) {
    int coordinates = ((y - minHeight) << 8) | ((localZ & 15) << 4) | (localX & 15);
    return (airKind(originalMaterial) << AIR_KIND_SHIFT) | coordinates;
  }

  static int decodeX(int encoded) {
    return (encoded & COORDINATE_MASK) & 15;
  }

  static int decodeZ(int encoded) {
    return ((encoded & COORDINATE_MASK) >>> 4) & 15;
  }

  static int decodeY(int encoded, int minHeight) {
    return minHeight + ((encoded & COORDINATE_MASK) >>> 8);
  }

  static Material decodeOriginalMaterial(int encoded) {
    return switch (encoded >>> AIR_KIND_SHIFT) {
      case 0 -> Material.AIR;
      case 1 -> Material.CAVE_AIR;
      case 2 -> Material.VOID_AIR;
      default -> null;
    };
  }

  private static int airKind(Material material) {
    return switch (material) {
      case AIR -> 0;
      case CAVE_AIR -> 1;
      case VOID_AIR -> 2;
      default -> throw new IllegalArgumentException("Receiver material is not air: " + material);
    };
  }

  private static boolean isAir(Material material) {
    return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
  }

  record Activation(Receiver receiver, Lease lease, boolean firstPulse) {
    Material originalMaterial() {
      return lease.originalMaterial();
    }
  }

  record Restoration(Receiver receiver, Material originalMaterial) {
  }

  private record Lease(long runtimeId, long generation, Material originalMaterial) {
  }

  record Receiver(UUID worldId, int x, int y, int z) {
    boolean isInChunk(UUID otherWorldId, int chunkX, int chunkZ) {
      return worldId.equals(otherWorldId) && (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }
  }
}
