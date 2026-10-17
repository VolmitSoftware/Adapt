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

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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
    return next;
  }

  public static double pool(Entity golem) {
    Double stored = golem.getPersistentDataContainer().get(POOL_KEY, PersistentDataType.DOUBLE);
    return stored != null && isPositiveFinite(stored) ? stored : 0D;
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

  private static void write(Entity golem, double value) {
    PersistentDataContainer data = golem.getPersistentDataContainer();
    if (value <= 0D) {
      data.remove(POOL_KEY);
      return;
    }
    data.set(POOL_KEY, PersistentDataType.DOUBLE, value);
  }

  private static boolean isPositiveFinite(double value) {
    return Double.isFinite(value) && value > 0D;
  }
}
