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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.math.VelocitySpeed;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class TamingMountedTactics extends SimpleAdaptation<TamingMountedTactics.Config> {
  private final Map<UUID, Location> lastMountedLocation = playerState();
  private final Map<UUID, Boolean> wasSprinting = playerState();
  private final Cooldowns emberCd = cooldowns();
  private final Cooldowns hitCd = cooldowns();

  public TamingMountedTactics() {
    super("tame-mounted-tactics");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.mounted_tactics");
    setIcon(Material.SADDLE);
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SADDLE)
        .key("challenge_taming_mounted_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_HORSE_ARMOR)
        .key("challenge_taming_mounted_50k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_taming_mounted_200", "taming.mounted-tactics.mounted-kills", 200, 400);
    registerMilestone("challenge_taming_mounted_50k", "taming.mounted-tactics.distance", 50000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getMountedDamageBonus(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.mounted_tactics.lore1"));
    v.addLore(C.GREEN + "+ " + Form.pc(getMountedDamageReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.mounted_tactics.lore2"));
  }

  @Override
  public void onTick() {
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player p = adaptPlayer.getPlayer();
      int level = getActiveLevel(p);
      if (level <= 0) {
        continue;
      }

      UUID id = p.getUniqueId();
      Entity vehicle = p.getVehicle();
      if (vehicle != null) {
        Location last = lastMountedLocation.get(id);
        Location current = p.getLocation();
        if (last != null && last.getWorld() == current.getWorld()) {
          double dist = last.distance(current);
          if (dist > 0.1 && dist < 100) {
            addStat(p, "taming.mounted-tactics.distance", dist);
          }
        }
        lastMountedLocation.put(id, current);

        boolean sprint = p.isSprinting();
        Boolean wasSprint = wasSprinting.get(id);
        if (sprint && (wasSprint == null || !wasSprint)) {
          fx(vehicle.getLocation(), FxPriority.TRAIL)
              .particle(Particle.POOF, 3, 0, 0.1D, 0, 0.15D, 0.02D)
              .dustBurst(Color.fromRGB(0x9A7B4F), 4, 0.25D, 1.0F)
              .sound(Sound.ENTITY_HORSE_GALLOP, 0.4F, 1.1F);
        }
        if (wasSprint == null || wasSprint != sprint) {
          wasSprinting.put(id, sprint);
        }
      } else {
        lastMountedLocation.remove(id);
        wasSprinting.remove(id);
      }
      if (vehicle instanceof AbstractHorse horse) {
        if (hasForwardInput(p)) {
          applyMountForwardSpeed(horse, p, getHorseTargetSpeed(level));
        }
        if (p.isSprinting()) {
          Vector push = p.getLocation().getDirection().clone().setY(0).normalize().multiply(getHorsePush(level));
          horse.setVelocity(horse.getVelocity().multiply(0.8).add(push));
        }
      } else if (vehicle instanceof Strider strider) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false, true), true);
        if (hasForwardInput(p)) {
          applyMountForwardSpeed(strider, p, getStriderTargetSpeed(level));
        }
        if (strider.getLocation().getBlock().getType() == Material.LAVA || strider.getLocation().clone().subtract(0, 1, 0).getBlock().getType() == Material.LAVA) {
          strider.setShivering(false);
          if (emberCd.isReady(p.getUniqueId(), 1000)) {
            emberCd.mark(p.getUniqueId());
            fx(strider, FxPriority.AMBIENT)
                .particle(Particle.FLAME, 2, 0, 0.3D, 0, 0.1D, 0.01D)
                .particle(Particle.SCRAPE, 1, 0, 0.4D, 0, 0.1D, 0);
          }
        }
      } else if (vehicle instanceof Pig pig) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 25, getPigResistanceAmplifier(level), false, false, true), true);
        if (p.isSprinting()) {
          Vector forward = p.getLocation().getDirection().clone().setY(0).normalize().multiply(getPigPush(level));
          pig.setVelocity(pig.getVelocity().multiply(0.7).add(forward));
        }
      }
    }
  }

  private void applyMountForwardSpeed(Entity mount, Player rider, double targetSpeed) {
    Vector direction = rider.getLocation().getDirection().setY(0);
    if (direction.lengthSquared() <= VelocitySpeed.EPSILON) {
      return;
    }

    direction.normalize();
    Vector velocity = mount.getVelocity();
    Vector horizontal = VelocitySpeed.horizontalOnly(velocity);
    Vector targetHorizontal = direction.multiply(Math.max(0, targetSpeed));
    Vector nextHorizontal = VelocitySpeed.moveTowards(horizontal, targetHorizontal, Math.max(0, getConfig().mountAccelPerTick));
    nextHorizontal = VelocitySpeed.clampHorizontal(nextHorizontal, getConfig().mountMaxHorizontalSpeed);
    mount.setVelocity(new Vector(nextHorizontal.getX(), velocity.getY(), nextHorizontal.getZ()));
  }

  private boolean hasForwardInput(Player p) {
    VelocitySpeed.InputSnapshot input = VelocitySpeed.readInput(p, getConfig().fallbackInputVelocityThresholdSquared());
    return input.forward() && !input.backward();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (e.getEntity().getKiller() instanceof Player p
        && p.getVehicle() != null
        && hasActiveAdaptation(p)) {
      addStat(p, "taming.mounted-tactics.mounted-kills", 1);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player attacker && attacker.getVehicle() != null) {
      int level = getActiveLevel(attacker);
      if (level > 0) {
        if (!canDamageTarget(attacker, e.getEntity())) {
          return;
        }

        e.setDamage(e.getDamage() * (1D + getMountedDamageBonus(level)));
        xp(attacker, e.getDamage() * getConfig().xpPerMountedDamage);
        if (hitCd.isReady(attacker.getUniqueId(), 200)) {
          hitCd.mark(attacker.getUniqueId());
          int count = Math.min(9, 4 + level);
          fx(e.getEntity().getLocation().add(0, 0.8D, 0), FxPriority.COMBAT)
              .burst(Particles.CRIT_MAGIC, count, 0.35D)
              .particle(Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
              .chord(Sound.ENTITY_HORSE_BREATHE, 0.4F, 0.9F, Sound.ITEM_TRIDENT_RETURN, 0.35F, 1.3F);
        }
      }
    }

    if (e.getEntity() instanceof Player defender && defender.getVehicle() != null) {
      int level = getActiveLevel(defender);
      if (level > 0) {
        e.setDamage(e.getDamage() * (1D - getMountedDamageReduction(level)));
        Vector face = defender.getLocation().getDirection().setY(0);
        Location front = defender.getLocation().add(0, 1, 0);
        if (face.lengthSquared() > 1.0E-6D) {
          front.add(face.normalize().multiply(0.6D));
        }
        fx(front, FxPriority.COMBAT)
            .dustBurst(Color.fromRGB(0x8FA3B0), 4, 0.35D, 1.0F)
            .sound(Sound.ITEM_SHIELD_BLOCK, 0.3F, 1.4F);
      }
    }
  }

  private double getMountedDamageBonus(int level) {
    return Math.min(getConfig().maxMountedDamageBonus, getConfig().mountedDamageBonusBase + (getLevelPercent(level) * getConfig().mountedDamageBonusFactor));
  }

  private double getMountedDamageReduction(int level) {
    return Math.min(getConfig().maxMountedDamageReduction, getConfig().mountedDamageReductionBase + (getLevelPercent(level) * getConfig().mountedDamageReductionFactor));
  }

  private int getHorseSpeedAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().horseSpeedAmplifierBase + (getLevelPercent(level) * getConfig().horseSpeedAmplifierFactor)));
  }

  private int getStriderSpeedAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().striderSpeedAmplifierBase + (getLevelPercent(level) * getConfig().striderSpeedAmplifierFactor)));
  }

  private int getPigResistanceAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().pigResistanceAmplifierBase + (getLevelPercent(level) * getConfig().pigResistanceAmplifierFactor)));
  }

  private double getHorseTargetSpeed(int level) {
    int amplifier = getHorseSpeedAmplifier(level);
    double base = Math.max(0, getConfig().horseBaseHorizontalSpeed);
    return Math.min(getConfig().mountMaxHorizontalSpeed, base * VelocitySpeed.speedAmplifierScalar(amplifier));
  }

  private double getStriderTargetSpeed(int level) {
    int amplifier = getStriderSpeedAmplifier(level);
    double base = Math.max(0, getConfig().striderBaseHorizontalSpeed);
    return Math.min(getConfig().mountMaxHorizontalSpeed, base * VelocitySpeed.speedAmplifierScalar(amplifier));
  }

  private double getHorsePush(int level) {
    return getConfig().horsePushBase + (getLevelPercent(level) * getConfig().horsePushFactor);
  }

  private double getPigPush(int level) {
    return getConfig().pigPushBase + (getLevelPercent(level) * getConfig().pigPushFactor);
  }

  @ConfigDescription("Gain mount-specific combat and control bonuses while riding.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Bonus Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedDamageBonusBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Bonus Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedDamageBonusFactor = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Mounted Damage Bonus for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxMountedDamageBonus = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Reduction Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedDamageReductionBase = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mounted Damage Reduction Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mountedDamageReductionFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Mounted Damage Reduction for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxMountedDamageReduction = 0.28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horse Speed Amplifier Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horseSpeedAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horse Speed Amplifier Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horseSpeedAmplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strider Speed Amplifier Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double striderSpeedAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Strider Speed Amplifier Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double striderSpeedAmplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pig Resistance Amplifier Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pigResistanceAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pig Resistance Amplifier Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pigResistanceAmplifierFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base horizontal speed target used for horse mounted speed scaling.", impact = "Higher values increase steady mounted horse acceleration when moving forward.")
    double horseBaseHorizontalSpeed = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base horizontal speed target used for strider mounted speed scaling.", impact = "Higher values increase steady mounted strider acceleration when moving forward.")
    double striderBaseHorizontalSpeed = 0.24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum horizontal speed this adaptation can force on mounts.", impact = "Acts as a hard cap to prevent runaway mounted momentum.")
    double mountMaxHorizontalSpeed = 0.78;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How fast mounts accelerate toward the target speed per tick.", impact = "Higher values accelerate faster; lower values feel smoother.")
    double mountAccelPerTick = 0.065;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fallback movement threshold used when direct input API is unavailable.", impact = "Only used on runtimes without Player input access.")
    double fallbackInputVelocityThreshold = 0.0008;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horse Push Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horsePushBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horse Push Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horsePushFactor = 0.16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pig Push Base for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pigPushBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pig Push Factor for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pigPushFactor = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mounted Damage for the Taming Mounted Tactics adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMountedDamage = 1.5;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }

    double fallbackInputVelocityThresholdSquared() {
      double threshold = Math.max(0, fallbackInputVelocityThreshold);
      return threshold * threshold;
    }
  }
}
