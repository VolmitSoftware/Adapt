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

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class GolemKillCredit {
  private GolemKillCredit() {
  }

  public static boolean isGolemKill(LivingEntity victim) {
    if (victim == null || !(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent lastDamage)) {
      return false;
    }
    Entity source = sourceOf(lastDamage.getDamager());
    if (source == null || GolemBody.of(source).isEmpty()) {
      return false;
    }
    if (GolemancyRuntime.handle(source).isPresent()) {
      return true;
    }
    return !(J.isFoliaThreading() && !J.isOwnedByCurrentRegion(source)) && GolemState.has(source);
  }

  private static Entity sourceOf(Entity damager) {
    return damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter ? shooter : damager;
  }
}
