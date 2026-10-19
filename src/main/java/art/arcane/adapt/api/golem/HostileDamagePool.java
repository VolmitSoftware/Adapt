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

package art.arcane.adapt.api.golem;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public final class HostileDamagePool {
  public static final NamespacedKey POOL_KEY = NamespacedKey.fromString("adapt:golem_hostile_pool");

  private HostileDamagePool() {
  }

  public static boolean isPoolBody(EntityType type) {
    if (type == null) {
      return false;
    }
    return switch (type) {
      case IRON_GOLEM, SNOW_GOLEM, COPPER_GOLEM -> true;
      default -> false;
    };
  }

  public static boolean isHostileDamager(Entity damager) {
    if (damager instanceof Enemy) {
      return true;
    }
    return damager instanceof Projectile projectile && projectile.getShooter() instanceof Enemy;
  }

  public static double add(Entity golem, double damage, double maxHealth) {
    double current = pool(golem);
    if (!isPositiveFinite(damage) || !isPositiveFinite(maxHealth)) {
      return current;
    }
    double next = Math.min(maxHealth, current + damage);
    write(golem, next);
    return pool(golem);
  }

  public static double pool(Entity golem) {
    Optional<GolemHandle> handle = GolemancyRuntime.handle(golem);
    if (handle.isPresent()) {
      return handle.get().hostileDamagePool();
    }
    if (GolemState.has(golem)) {
      return GolemState.read(golem).map(GolemState::hostileDamagePool).orElse(0D);
    }
    return unbound(golem);
  }

  public static double consume(Entity golem, double amount) {
    if (!isPositiveFinite(amount)) {
      return 0D;
    }
    double current = pool(golem);
    double paid = Math.min(current, amount);
    if (paid <= 0D) {
      return 0D;
    }
    write(golem, current - paid);
    return paid;
  }

  static double takeUnbound(Entity golem) {
    double stored = unbound(golem);
    golem.getPersistentDataContainer().remove(POOL_KEY);
    return stored;
  }

  static void putUnbound(Entity golem, double value) {
    PersistentDataContainer data = golem.getPersistentDataContainer();
    if (!isPositiveFinite(value)) {
      data.remove(POOL_KEY);
      return;
    }
    data.set(POOL_KEY, PersistentDataType.DOUBLE, value);
  }

  private static double unbound(Entity golem) {
    Double stored = golem.getPersistentDataContainer().get(POOL_KEY, PersistentDataType.DOUBLE);
    return stored != null && isPositiveFinite(stored) ? stored : 0D;
  }

  private static void write(Entity golem, double value) {
    Optional<GolemHandle> handle = GolemancyRuntime.handle(golem);
    if (handle.isPresent()) {
      handle.get().setHostileDamagePool(value);
      return;
    }
    if (GolemState.has(golem)) {
      GolemState.read(golem).ifPresent(state -> {
        state.setHostileDamagePool(value);
        state.write(golem);
      });
      return;
    }
    putUnbound(golem, value);
  }

  private static boolean isPositiveFinite(double value) {
    return Double.isFinite(value) && value > 0D;
  }
}
