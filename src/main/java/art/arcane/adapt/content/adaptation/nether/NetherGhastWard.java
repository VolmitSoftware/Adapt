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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

public class NetherGhastWard extends SimpleAdaptation<NetherGhastWard.Config> {
  public NetherGhastWard() {
    super("nether-ghast-ward");
    registerConfiguration(Config.class);
    setIcon(Material.GHAST_TEAR);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GHAST_TEAR)
        .key("challenge_nether_ghast_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_nether_ghast_500", "nether.ghast-ward.damage-reduced", 500, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getGhastProjectileReduction(level), 0), 1);
    statLore(v, Form.pc(getExplosionReduction(level), 0), 2);
    statLore(v, Form.pc(getWitherSkeletonReduction(level), 0), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!isNether(p)) {
        return;
      }

      int level = getActiveLevel(p);
      if (e.getDamager() instanceof Fireball fireball && fireball.getShooter() instanceof Ghast) {
        double before = e.getDamage();
        e.setDamage(Math.max(0, e.getDamage() * (1D - getGhastProjectileReduction(level))));
        int fireBefore = p.getFireTicks();
        p.setFireTicks(Math.min(p.getFireTicks(), getMaxFireTicks(level)));
        xp(p, e.getDamage() * getConfig().xpPerMitigatedDamage);
        int reduced = (int) Math.round(before - e.getDamage());
        if (reduced > 0) {
          addStat(p, "nether.ghast-ward.damage-reduced", reduced);
        }
        Location center = p.getLocation().add(0D, 1.0D, 0D);
        Vector rel = p.getLocation().toVector().subtract(fireball.getLocation().toVector());
        double yaw = Math.atan2(rel.getZ(), rel.getX());
        int arcPoints = 8 + (int) Math.round(getGhastProjectileReduction(level) * 8D);
        FxEmitter emit = fx(center, FxPriority.COMBAT)
            .arc(Particle.SOUL, 1.2D, arcPoints, yaw, Math.PI * 0.6D, 0.0D)
            .burst(Particle.CRIT, 4, 0.3D)
            .chord(Sound.ITEM_SHIELD_BLOCK, 0.6F, 1.1F, Sound.ENTITY_WITHER_HURT, 0.3F, 1.6F);
        if (p.getFireTicks() < fireBefore) {
          emit.particle(Particles.SMOKE, 3, 0D, 0D, 0D, 0.2D, 0.0D);
        }
        return;
      }

      if (e.getDamager() instanceof AbstractArrow arrow && arrow.getShooter() instanceof WitherSkeleton) {
        double before = e.getDamage();
        e.setDamage(Math.max(0, e.getDamage() * (1D - getWitherSkeletonReduction(level))));
        xp(p, e.getDamage() * getConfig().xpPerMitigatedDamage);
        int reduced = (int) Math.round(before - e.getDamage());
        if (reduced > 0) {
          addStat(p, "nether.ghast-ward.damage-reduced", reduced);
        }
        fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.COMBAT)
            .burst(Particle.CRIT, 6, 0.3D)
            .particle(Particle.SOUL, 2, 0D, 0.2D, 0D, 0.2D, 0.0D)
            .sound(Sound.ITEM_SHIELD_BLOCK, 0.35F, 1.5F);
      }
    });
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(EntityDamageEvent e) {
    if (e instanceof EntityDamageByEntityEvent || !(e.getEntity() instanceof Player p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!isNether(p)) {
        return;
      }

      if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION && e.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
        return;
      }

      double before = e.getDamage();
      e.setDamage(Math.max(0, e.getDamage() * (1D - getExplosionReduction(getLevel(p)))));
      xp(p, e.getDamage() * getConfig().xpPerMitigatedDamage);
      int reduced = (int) Math.round(before - e.getDamage());
      if (reduced > 0) {
        addStat(p, "nether.ghast-ward.damage-reduced", reduced);
      }
      double domeRadius = 0.9D + (getExplosionReduction(getLevel(p)) * 0.8D);
      fx(p.getLocation().add(0D, 1.0D, 0D), FxPriority.COMBAT)
          .dome(Particle.SCULK_SOUL, domeRadius, 14)
          .chord(Sound.BLOCK_BEACON_DEACTIVATE, 0.4F, 1.4F, Sound.ENTITY_GENERIC_EXPLODE, 0.25F, 0.6F);
    });
  }

  private boolean isNether(Player p) {
    return p.getWorld().getEnvironment().name().contains("NETHER");
  }

  private double getGhastProjectileReduction(int level) {
    return Math.min(getConfig().maxGhastProjectileReduction, getConfig().ghastProjectileReductionBase + (getLevelPercent(level) * getConfig().ghastProjectileReductionFactor));
  }

  private double getExplosionReduction(int level) {
    return Math.min(getConfig().maxExplosionReduction, getConfig().explosionReductionBase + (getLevelPercent(level) * getConfig().explosionReductionFactor));
  }

  private double getWitherSkeletonReduction(int level) {
    return Math.min(getConfig().maxWitherSkeletonReduction, getConfig().witherSkeletonReductionBase + (getLevelPercent(level) * getConfig().witherSkeletonReductionFactor));
  }

  private int getMaxFireTicks(int level) {
    return Math.max(0, (int) Math.round(getConfig().maxFireTicksBase - (getLevelPercent(level) * getConfig().maxFireTicksFactor)));
  }


  @ConfigDescription("Harden against ghast blasts and wither-skeleton pressure in the Nether.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ghast Projectile Reduction Base for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double ghastProjectileReductionBase = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ghast Projectile Reduction Factor for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double ghastProjectileReductionFactor = 0.54;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Ghast Projectile Reduction for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxGhastProjectileReduction = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Explosion Reduction Base for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double explosionReductionBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Explosion Reduction Factor for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double explosionReductionFactor = 0.42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Explosion Reduction for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxExplosionReduction = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Skeleton Reduction Base for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double witherSkeletonReductionBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Skeleton Reduction Factor for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double witherSkeletonReductionFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Wither Skeleton Reduction for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxWitherSkeletonReduction = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Fire Ticks Base for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxFireTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Fire Ticks Factor for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxFireTicksFactor = 70;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mitigated Damage for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMitigatedDamage = 4.2;

    public Config() {
      costFactor = 0.73;
      maxLevel = 6;
      initialCost = 4;
    }
  }
}
