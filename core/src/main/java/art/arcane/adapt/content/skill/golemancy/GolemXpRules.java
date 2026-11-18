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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Id;

public final class GolemXpRules {
  private static final Id SNOWBALL = Id.minecraft("snowball");
  private static final Id SNOW_GOLEM = Id.minecraft("snow_golem");
  private static final Id IRON_GOLEM = Id.minecraft("iron_golem");

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

  public static double effectiveDamage(double damage, double remainingHealth) {
    if (Double.isNaN(damage) || !Double.isFinite(remainingHealth) || damage <= 0D || remainingHealth <= 0D) {
      return 0D;
    }
    return Math.min(damage, remainingHealth);
  }

  public static double assistXp(double dealtDamage, double assistMultiplier, double bodyMultiplier, boolean spawnerMob, double spawnerMultiplier) {
    double spawnerFactor = spawnerMob ? spawnerMultiplier : 1D;
    return finiteOrZero(dealtDamage * assistMultiplier * bodyMultiplier * spawnerFactor, dealtDamage, assistMultiplier, bodyMultiplier, spawnerFactor);
  }

  public static double ownedKillXp(double killValue, boolean raidMob, double raidMultiplier, boolean spawnerMob, double spawnerMultiplier) {
    double raidFactor = raidMob ? raidMultiplier : 1D;
    double spawnerFactor = spawnerMob ? spawnerMultiplier : 1D;
    return finiteOrZero(killValue * raidFactor * spawnerFactor, killValue, raidFactor, spawnerFactor);
  }

  public static double servantShareXp(double share, double ownedKillXp) {
    return finiteOrZero(share * ownedKillXp, share, ownedKillXp);
  }

  public static String servantRewardKey(Id victim) {
    return "golemancy:servant:" + victim.path();
  }

  public static double tankXp(double takenDamage, double tankMultiplier, boolean spawnerMob, double spawnerMultiplier) {
    double spawnerFactor = spawnerMob ? spawnerMultiplier : 1D;
    return finiteOrZero(takenDamage * tankMultiplier * spawnerFactor, takenDamage, tankMultiplier, spawnerFactor);
  }

  public static double ownerDistanceFactor(double distanceSquared, double proximityRadius, double distantMultiplier) {
    if (Double.isFinite(proximityRadius) && proximityRadius >= 0D && distanceSquared <= proximityRadius * proximityRadius) {
      return 1D;
    }
    return Double.isFinite(distantMultiplier) ? Math.clamp(distantMultiplier, 0D, 1D) : 0D;
  }

  public static EntityRef golemSource(EntityRef damager) {
    MobRef snowman = snowballShooter(damager);
    return snowman == null ? damager : snowman;
  }

  public static EntityRef attacker(EntityRef damager) {
    if (damager instanceof ProjectileRef projectile && projectile.shooter() != null) {
      return projectile.shooter();
    }
    return damager;
  }

  public static boolean inActiveRaid(EntityRef victim) {
    return victim != null && AdaptCore.platform().host().animals().inActiveRaid(victim);
  }

  public static MobRef witnessGolem(EntityRef damager) {
    if (damager instanceof MobRef mob && (IRON_GOLEM.equals(mob.type()) || SNOW_GOLEM.equals(mob.type()))) {
      return mob;
    }
    return snowballShooter(damager);
  }

  private static MobRef snowballShooter(EntityRef damager) {
    if (damager instanceof ProjectileRef snowball && SNOWBALL.equals(snowball.type())
        && snowball.shooter() instanceof MobRef snowman && SNOW_GOLEM.equals(snowman.type())) {
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
