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

import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;

public final class GolemXpRules {
  private GolemXpRules() {
  }

  public static double killValue(double base, double victimMaxHealth, double maxHealthMultiplier) {
    return finiteOrZero(base + Math.max(0D, victimMaxHealth) * maxHealthMultiplier);
  }

  public static double witnessXp(double killValue, double witnessMultiplier, boolean spawnerMob, double spawnerMultiplier) {
    double spawnerFactor = spawnerMob ? spawnerMultiplier : 1D;
    return finiteOrZero(killValue * witnessMultiplier * spawnerFactor, killValue, witnessMultiplier, spawnerFactor);
  }

  public static double repairXp(double paidHealth, double xpPerHealth) {
    return finiteOrZero(paidHealth * xpPerHealth, paidHealth, xpPerHealth);
  }

  public static Golem witnessGolem(Entity damager) {
    if (damager instanceof IronGolem || damager instanceof Snowman) {
      return (Golem) damager;
    }
    if (damager instanceof Snowball snowball && snowball.getShooter() instanceof Snowman snowman) {
      return snowman;
    }
    return null;
  }

  private static double finiteOrZero(double value, double... factors) {
    for (double factor : factors) {
      if (!Double.isFinite(factor) || factor < 0D) {
        return 0D;
      }
    }
    return Double.isFinite(value) && value > 0D ? value : 0D;
  }
}
