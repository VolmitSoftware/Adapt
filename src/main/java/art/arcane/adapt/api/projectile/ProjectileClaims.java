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

package art.arcane.adapt.api.projectile;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptations that launch or repurpose projectiles stamp an ownership key on the projectile's
 * persistent data container before it spawns. Any feature that clones, redirects, or replaces
 * projectiles must refuse ones carrying a foreign ownership key, or it will hijack another
 * adaptation's projectile mid-flight. Registered cooperative modifier keys do not claim ownership.
 */
public final class ProjectileClaims {
  private static final Set<NamespacedKey> COOPERATIVE_KEYS = ConcurrentHashMap.newKeySet();

  private ProjectileClaims() {
  }

  public static void registerCooperativeKeys(NamespacedKey... keys) {
    for (NamespacedKey key : keys) {
      COOPERATIVE_KEYS.add(Objects.requireNonNull(key));
    }
  }

  public static PersistentDataContainer snapshotCooperativeData(PersistentDataContainer data) {
    PersistentDataContainer snapshot = data.getAdapterContext().newPersistentDataContainer();
    data.copyTo(snapshot, true);
    for (NamespacedKey key : snapshot.getKeys()) {
      if (!COOPERATIVE_KEYS.contains(key)) {
        snapshot.remove(key);
      }
    }
    return snapshot;
  }

  public static boolean isUnclaimed(Projectile projectile, NamespacedKey... ownedKeys) {
    return projectile != null && isUnclaimedContainer(projectile.getPersistentDataContainer(), ownedKeys);
  }

  public static boolean isUnclaimedContainer(PersistentDataContainer data, NamespacedKey... ownedKeys) {
    if (data == null) {
      return false;
    }

    Set<NamespacedKey> keys = data.getKeys();
    if (keys == null || keys.isEmpty()) {
      return true;
    }

    for (NamespacedKey key : keys) {
      if (!COOPERATIVE_KEYS.contains(key) && !isOwnedKey(key, ownedKeys)) {
        return false;
      }
    }

    return true;
  }

  private static boolean isOwnedKey(NamespacedKey key, NamespacedKey[] ownedKeys) {
    for (NamespacedKey owned : ownedKeys) {
      if (owned != null && owned.equals(key)) {
        return true;
      }
    }

    return false;
  }
}
