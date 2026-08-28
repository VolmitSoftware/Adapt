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
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class BlockingShieldWall extends SimpleAdaptation<BlockingShieldWall.Config> {
  public BlockingShieldWall() {
    super("blocking-shield-wall");
    registerConfiguration(Config.class);
    setIcon(Material.SHIELD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_shieldwall_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_INGOT)
            .key("challenge_blocking_shieldwall_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_blocking_shieldwall_500", "blocking.shield-wall.damage-shielded", 500, 500);
    registerMilestone("challenge_blocking_shieldwall_5k", "blocking.shield-wall.damage-shielded", 5000, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getArcDegrees(level), 0), 1);
    statLore(v, Form.pc(getDamageReduction(level), 0), 2);
    statLore(v, Form.f(getRange(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player ally) || !(e.getDamager() instanceof Projectile projectile)) {
      return;
    }

    Vector inDir = projectile.getVelocity().clone();
    if (inDir.lengthSquared() < 1.0E-6) {
      inDir = ally.getLocation().toVector().subtract(projectile.getLocation().toVector());
    }
    if (inDir.lengthSquared() < 1.0E-6) {
      return;
    }
    inDir.normalize();

    double scanRadius = getConfig().rangeBase + getConfig().rangeFactor;
    Location allyLocation = ally.getLocation();
    Player bestBlocker = null;
    int bestLevel = 0;
    double bestReduction = 0;
    double minFacing = getConfig().minFacingAlignment;

    for (Player blocker : PaperCompat.nearbyEntitiesByType(Player.class, allyLocation, scanRadius, scanRadius, scanRadius)) {
      if (blocker == ally || !blocker.isBlocking() || !hasShield(blocker)) {
        continue;
      }

      int level = getActiveLevel(blocker);
      if (level <= 0) {
        continue;
      }

      Vector blockerToAlly = allyLocation.toVector().subtract(blocker.getLocation().toVector());
      double distance = blockerToAlly.length();
      if (distance < 0.01 || distance > getRange(level)) {
        continue;
      }
      blockerToAlly.multiply(1.0D / distance);

      if (inDir.dot(blockerToAlly) < getArcCos(level)) {
        continue;
      }
      if (blocker.getEyeLocation().getDirection().dot(inDir) > -minFacing) {
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

    double before = e.getDamage();
    double shielded = before * bestReduction;
    if (shielded <= 0) {
      return;
    }
    e.setDamage(Math.max(0, before - shielded));

    impactCue(ally);
    Player blocker = bestBlocker;
    int blockerLevel = bestLevel;
    J.runEntity(blocker, () -> rewardBlocker(blocker, blockerLevel, shielded));
  }

  private void rewardBlocker(Player blocker, int level, double shielded) {
    if (!blocker.isOnline()) {
      return;
    }
    xp(blocker, shielded * getConfig().xpPerDamageShielded);
    addStat(blocker, "blocking.shield-wall.damage-shielded", shielded);
    Vector look = blocker.getEyeLocation().getDirection();
    Location shield = blocker.getEyeLocation().add(look.getX() * 0.55D, look.getY() * 0.55D, look.getZ() * 0.55D);
    fx(shield, FxPriority.COMBAT)
        .ring(Particles.END_ROD, 0.45D, 6, 0)
        .sound(Sound.ITEM_SHIELD_BLOCK, 0.6F, 1.3F);
  }

  private void impactCue(Player ally) {
    fx(ally.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 4, 0.2D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.5F, 1.5F);
  }

  private boolean hasShield(Player p) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
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
      costFactor = 0.65;
      initialCost = 4;
    }
  }
}
