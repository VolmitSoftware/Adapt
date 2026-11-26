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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

public class NetherGhastWard extends SimpleAdaptation<NetherGhastWard.Config> {
  private static final Id WITHER_SKELETON = Id.minecraft("wither_skeleton");

  public NetherGhastWard() {
    super("nether-ghast-ward");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ghast_tear"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ghast_tear"))
        .key("challenge_nether_ghast_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_nether_ghast_500", "nether.ghast-ward.damage-reduced", 500, 400);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getGhastProjectileReduction(level), 0), 1);
    statLore(v, Form.pc(getExplosionReduction(level), 0), 2);
    statLore(v, Form.pc(getWitherSkeletonReduction(level), 0), 3);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!isNether(p)) {
        return;
      }

      int level = getActiveLevel(p);
      EntityRef damager = e.damager();
      if (NetherBlastWard.isGhastFireball(damager)) {
        double removed = NetherBlastWard.absorbFireball(this, p, e, getGhastProjectileReduction(level), getMaxFireTicks(level));
        xp(p, removed * getConfig().xpPerMitigatedDamage);
        int reduced = (int) Math.round(removed);
        if (reduced > 0) {
          addStat(p, "nether.ghast-ward.damage-reduced", reduced);
        }
        return;
      }

      if (damager.has(EntityTrait.ARROW) && NetherBlastWard.shooterIs(damager, WITHER_SKELETON)) {
        double before = e.damage();
        e.setDamage(Math.max(0, e.damage() * (1D - getWitherSkeletonReduction(level))));
        xp(p, (before - e.damage()) * getConfig().xpPerMitigatedDamage);
        int reduced = (int) Math.round(before - e.damage());
        if (reduced > 0) {
          addStat(p, "nether.ghast-ward.damage-reduced", reduced);
        }
        fx(p.location().add(0D, 1.0D, 0D), FxPriority.COMBAT)
            .burst(Particles.CRIT, 6, 0.3D)
            .particle(Particles.SOUL, 2, 0D, 0.2D, 0D, 0.2D, 0.0D)
            .sound(Sounds.ITEM_SHIELD_BLOCK, 0.35F, 1.5F);
      }
    });
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    if (e instanceof EntityDamageByEntityEvent byEntity && NetherBlastWard.isGhastFireball(byEntity.damager())) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!isNether(p)) {
        return;
      }

      if (!NetherBlastWard.isExplosion(e.cause())) {
        return;
      }

      double removed = NetherBlastWard.absorbBlast(this, p, e, getExplosionReduction(getLevel(p)));
      xp(p, removed * getConfig().xpPerMitigatedDamage);
      int reduced = (int) Math.round(removed);
      if (reduced > 0) {
        addStat(p, "nether.ghast-ward.damage-reduced", reduced);
      }
    });
  }

  private boolean isNether(PlayerRef p) {
    return p.world().environment() == Environment.NETHER;
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


  static void fireballFx(Adaptation<?> owner, PlayerRef p, EntityRef fireball, double reduction, boolean doused) {
    Vec3 rel = p.location().position().subtract(fireball.location().position());
    double yaw = Math.atan2(rel.z(), rel.x());
    int arcPoints = 8 + (int) Math.round(reduction * 8D);
    FxEmitter emit = Fx.now(owner, p.location().add(0D, 1.0D, 0D), FxPriority.COMBAT)
        .arc(Particles.SOUL, 1.2D, arcPoints, yaw, Math.PI * 0.6D, 0.0D)
        .burst(Particles.CRIT, 4, 0.3D)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 0.6F, 1.1F, Sounds.ENTITY_WITHER_HURT, 0.3F, 1.6F);
    if (doused) {
      emit.particle(Particles.SMOKE, 3, 0D, 0D, 0D, 0.2D, 0.0D);
    }
  }

  static void blastFx(Adaptation<?> owner, PlayerRef p, double reduction) {
    double domeRadius = 0.9D + (reduction * 0.8D);
    Fx.now(owner, p.location().add(0D, 1.0D, 0D), FxPriority.COMBAT)
        .dome(Particles.SCULK_SOUL, domeRadius, 14)
        .chord(Sounds.BLOCK_BEACON_DEACTIVATE, 0.4F, 1.4F, Sounds.ENTITY_GENERIC_EXPLODE, 0.25F, 0.6F);
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
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 6;
      initialCost = 2;
    }
  }
}
