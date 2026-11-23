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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class BlockingShieldWall extends SimpleAdaptation<BlockingShieldWall.Config> {
  private static final Id SHIELD = Id.minecraft("shield");

  public BlockingShieldWall() {
    super("blocking-shield-wall");
    registerConfiguration(Config.class);
    setIcon(SHIELD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_shieldwall_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_ingot"))
            .key("challenge_blocking_shieldwall_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_blocking_shieldwall_500", "blocking.shield-wall.damage-shielded", 500, 500);
    registerMilestone("challenge_blocking_shieldwall_5k", "blocking.shield-wall.damage-shielded", 5000, 2000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.ALLIES);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getArcDegrees(level), 0), 1);
    statLore(v, Form.pc(getDamageReduction(level), 0), 2);
    statLore(v, Form.f(getRange(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef ally) || !(e.damager() instanceof ProjectileRef projectile)) {
      return;
    }

    Vec3 inDir = projectile.velocity();
    if (inDir.lengthSquared() < 1.0E-6) {
      inDir = ally.location().position().subtract(projectile.location().position());
    }
    if (inDir.lengthSquared() < 1.0E-6) {
      return;
    }
    inDir = inDir.normalize();

    double scanRadius = getConfig().rangeBase + getConfig().rangeFactor;
    Loc allyLocation = ally.location();
    PlayerRef bestBlocker = null;
    int bestLevel = 0;
    double bestReduction = 0;
    double minFacing = getConfig().minFacingAlignment;

    for (EntityRef nearby : allyLocation.world().nearbyEntities(allyLocation.position(), scanRadius, scanRadius, scanRadius)) {
      if (!(nearby instanceof PlayerRef blocker) || blocker.uuid().equals(ally.uuid()) || !blocker.isBlocking() || !hasShield(blocker)) {
        continue;
      }

      int level = getActiveLevel(blocker);
      if (level <= 0 || !preference(blocker, BlockingPreferences.ALLIES).accepts(blocker, ally)) {
        continue;
      }

      Vec3 blockerToAlly = allyLocation.position().subtract(blocker.location().position());
      double distance = blockerToAlly.length();
      if (distance < 0.01 || distance > getRange(level)) {
        continue;
      }
      blockerToAlly = blockerToAlly.multiply(1.0D / distance);

      if (inDir.dot(blockerToAlly) < getArcCos(level)) {
        continue;
      }
      if (blocker.eyeLocation().direction().dot(inDir) > -minFacing) {
        continue;
      }

      double reduction = getDamageReduction(level);
      if (reduction > bestReduction) {
        bestReduction = reduction;
        bestBlocker = blocker;
        bestLevel = level;
      }
    }

    if (bestBlocker == null) {
      return;
    }

    double before = e.damage();
    double shielded = before * bestReduction;
    if (shielded <= 0) {
      return;
    }
    e.setDamage(Math.max(0, before - shielded));

    impactCue(ally);
    PlayerRef blocker = bestBlocker;
    int blockerLevel = bestLevel;
    Tasks.runEntity(blocker, () -> rewardBlocker(blocker, blockerLevel, shielded));
  }

  private void rewardBlocker(PlayerRef blocker, int level, double shielded) {
    if (!blocker.isOnline()) {
      return;
    }
    xp(blocker, shielded * getConfig().xpPerDamageShielded);
    addStat(blocker, "blocking.shield-wall.damage-shielded", shielded);
    Vec3 look = blocker.eyeLocation().direction();
    Loc shield = blocker.eyeLocation().add(look.x() * 0.55D, look.y() * 0.55D, look.z() * 0.55D);
    fx(shield, FxPriority.COMBAT)
        .ring(Particles.END_ROD, 0.45D, 6, 0)
        .sound(Sounds.ITEM_SHIELD_BLOCK, 0.6F, 1.3F);
  }

  private void impactCue(PlayerRef ally) {
    fx(ally.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 4, 0.2D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.5F, 1.5F);
  }

  private boolean hasShield(PlayerRef p) {
    ItemRef main = p.inventory().mainHand();
    ItemRef off = p.inventory().offHand();
    return (isItem(main) && SHIELD.equals(main.type())) || (isItem(off) && SHIELD.equals(off.type()));
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private double getArcDegrees(int level) {
    return getConfig().arcDegreesBase + (getLevelPercent(level) * getConfig().arcDegreesFactor);
  }

  private double getArcCos(int level) {
    return Math.cos(Math.toRadians(getArcDegrees(level) * 0.5D));
  }

  private double getDamageReduction(int level) {
    return Math.min(getConfig().maxDamageReduction, getConfig().damageReductionBase + (getLevelPercent(level) * getConfig().damageReductionFactor));
  }


  @ConfigDescription("While blocking, allies sheltered behind your shield take reduced projectile damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Base for the Blocking Shield Wall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Factor for the Blocking Shield Wall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Arc Degrees Base for the Blocking Shield Wall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double arcDegreesBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Arc Degrees Factor for the Blocking Shield Wall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double arcDegreesFactor = 90;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Reduction Base for the Blocking Shield Wall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageReductionBase = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Reduction Factor for the Blocking Shield Wall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageReductionFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Damage Reduction for the Blocking Shield Wall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDamageReduction = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Facing Alignment for the Blocking Shield Wall adaptation.", impact = "Higher values require the blocker to face the incoming projectile more directly.")
    double minFacingAlignment = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage Shielded for the Blocking Shield Wall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamageShielded = 3.0;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
