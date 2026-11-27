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

package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Optional;
import java.util.Set;

public final class HostileDamagePool {
  public static final DataKey<Double> POOL_KEY = DataKey.of(Id.of("adapt", "golem_hostile_pool"), DataCodecs.DOUBLE);
  private static final Set<Id> POOL_BODIES = Set.of(Id.minecraft("iron_golem"), Id.minecraft("snow_golem"), Id.minecraft("copper_golem"));

  private HostileDamagePool() {
  }

  public static boolean isPoolBody(Id type) {
    return type != null && POOL_BODIES.contains(type);
  }

  public static boolean isHostileDamager(EntityRef damager) {
    if (damager == null) {
      return false;
    }
    if (damager.has(EntityTrait.ENEMY)) {
      return true;
    }
    EntityRef shooter = damager instanceof ProjectileRef projectile ? projectile.shooter() : null;
    return shooter != null && shooter.has(EntityTrait.ENEMY);
  }

  public static double add(EntityRef golem, double damage, double maxHealth) {
    double current = pool(golem);
    if (!isPositiveFinite(damage) || !isPositiveFinite(maxHealth)) {
      return current;
    }
    double next = Math.min(maxHealth, current + damage);
    write(golem, next);
    return pool(golem);
  }

  public static double pool(EntityRef golem) {
    Optional<GolemHandle> handle = GolemancyRuntime.handle(golem);
    if (handle.isPresent()) {
      return handle.get().hostileDamagePool();
    }
    if (GolemState.has(golem)) {
      return GolemState.read(golem).map(GolemState::hostileDamagePool).orElse(0D);
    }
    return unbound(golem);
  }

  public static double consume(EntityRef golem, double amount) {
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

  static double takeUnbound(EntityRef golem) {
    double stored = unbound(golem);
    golem.data().remove(POOL_KEY);
    return stored;
  }

  static void putUnbound(EntityRef golem, double value) {
    DataHolder data = golem.data();
    if (!isPositiveFinite(value)) {
      data.remove(POOL_KEY);
      return;
    }
    data.set(POOL_KEY, value);
  }

  private static double unbound(EntityRef golem) {
    Double stored = golem.data().get(POOL_KEY);
    return stored != null && isPositiveFinite(stored) ? stored : 0D;
  }

  private static void write(EntityRef golem, double value) {
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
