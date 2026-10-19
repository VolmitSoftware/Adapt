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

package art.arcane.adapt.api.minion;

import org.bukkit.entity.Entity;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class MinionIdentity {
  private static volatile MinionKind[] kinds = new MinionKind[0];

  private MinionIdentity() {
  }

  public static synchronized void registerKind(MinionKind kind) {
    MinionKind registered = Objects.requireNonNull(kind);
    MinionKind[] current = kinds;
    for (MinionKind existing : current) {
      if (existing.equals(registered)) {
        return;
      }
    }
    MinionKind[] next = Arrays.copyOf(current, current.length + 1);
    next[current.length] = registered;
    kinds = next;
  }

  public static synchronized void unregisterKind(MinionKind kind) {
    MinionKind[] current = kinds;
    for (int index = 0; index < current.length; index++) {
      if (current[index].equals(kind)) {
        MinionKind[] next = new MinionKind[current.length - 1];
        System.arraycopy(current, 0, next, 0, index);
        System.arraycopy(current, index + 1, next, index, current.length - index - 1);
        kinds = next;
        return;
      }
    }
  }

  public static boolean isMinion(Entity entity) {
    return kindOf(entity) != null;
  }

  public static UUID ownerOf(Entity entity) {
    MinionKind kind = kindOf(entity);
    return kind == null ? null : kind.ownerOf(entity);
  }

  public static boolean sameOwner(Entity a, Entity b) {
    UUID ownerId = ownerOf(a);
    return ownerId != null && ownerId.equals(ownerOf(b));
  }

  private static MinionKind kindOf(Entity entity) {
    if (entity == null) {
      return null;
    }
    for (MinionKind kind : kinds) {
      if (kind.matches(entity)) {
        return kind;
      }
    }
    return null;
  }
}
