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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.ProjectileRef;

public final class GolemKillCredit {
  private GolemKillCredit() {
  }

  /** True when the victim's last damage came from a golem, directly or through its projectile. */
  public static boolean isGolemKill(LivingRef victim) {
    LastDamage lastDamage = victim == null ? null : victim.lastDamage();
    EntityRef source = lastDamage == null ? null : sourceOf(lastDamage.damager());
    if (source == null || GolemBody.of(source).isEmpty()) {
      return false;
    }
    if (GolemancyRuntime.handle(source).isPresent()) {
      return true;
    }
    return !(AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(source)) && GolemState.has(source);
  }

  private static EntityRef sourceOf(EntityRef damager) {
    return damager instanceof ProjectileRef projectile && projectile.shooter() != null ? projectile.shooter() : damager;
  }
}
