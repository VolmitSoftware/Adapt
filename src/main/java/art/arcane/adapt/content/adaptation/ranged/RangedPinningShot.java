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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RangedPinningShot extends SimpleAdaptation<RangedPinningShot.Config> {
  private final Map<UUID, Long> targetProcTimes = playerState();

  public RangedPinningShot() {
    super("ranged-pinning-shot");
    registerConfiguration(Config.class);
    setIcon(Material.TRIPWIRE_HOOK);
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ARROW)
        .key("challenge_ranged_pinning_300")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_ranged_pinning_300", "ranged.pinning-shot.targets-pinned", 300, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getProcChance(level), 0), 1);
    statLore(v, Form.duration(getDurationTicks(level) * 50D, 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getReapplyCooldownMillis(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (RangedHeartseeker.isSeekingProjectile(e.getDamager())) {
      return;
    }
    art.arcane.adapt.api.adaptation.Adaptation.ProjectileContext combat = resolveProjectileContext(e);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    LivingEntity target = combat.target();
    int level = combat.level();

    long now = System.currentTimeMillis();
    cleanupExpired(now);
    long reapply = getReapplyCooldownMillis(level);
    Long last = targetProcTimes.get(target.getUniqueId());
    if (last != null && last + reapply > now) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() > getProcChance(level)) {
      return;
    }

    targetProcTimes.put(target.getUniqueId(), now);
    int durationTicks = getDurationTicks(level);
    if (durationTicks > 0) {
      AdaptAttributeService.get().applyTimed(target, getName(), "pin", Attributes.MOVEMENT_SPEED, pinSpeedScalar(getAmplifier(level)), AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
    }
    addStat(p, "ranged.pinning-shot.targets-pinned", 1);

    if (getConfig().dampenVelocityOnProc) {
      Vector velocity = target.getVelocity();
      target.setVelocity(new Vector(velocity.getX() * getConfig().horizontalVelocityFactor, velocity.getY(), velocity.getZ() * getConfig().horizontalVelocityFactor));
      fx(target, FxPriority.COMBAT).dustBurst(Color.fromRGB(140, 120, 100), 6, 0.2D, 1.0F);
    }

    Location center = target.getLocation();
    double ringRadius = 0.6D + Math.min(0.4D, durationTicks / 300.0D);
    fx(center, FxPriority.COMBAT)
        .column(Particles.CRIT_MAGIC, 6, 1.6D)
        .dustRing(Color.fromRGB(120, 120, 140), ringRadius, 12, 1.0F)
        .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 1.0D, 0, 0.35D, 0.0D)
        .chord(Sound.BLOCK_BELL_USE, 1.1F, 0.48F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.55F)
        .sound(Sound.BLOCK_ANVIL_LAND, 0.3F, 0.6F);
    xp(p, getConfig().xpOnProc);
  }

  private void cleanupExpired(long now) {
    if (targetProcTimes.size() < getConfig().cleanupThreshold) {
      return;
    }

    targetProcTimes.entrySet().removeIf(entry -> entry.getValue() + getConfig().entryTtlMillis < now);
  }

  private double getProcChance(int level) {
    return Math.min(getConfig().maxProcChance, getConfig().procChanceBase + (getLevelPercent(level) * getConfig().procChanceFactor));
  }

  private int getDurationTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
  }

  private int getAmplifier(int level) {
    return Math.max(0, (int) Math.floor(getConfig().amplifierBase + (getLevelPercent(level) * getConfig().amplifierFactor)));
  }

  static double pinSpeedScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * (Math.max(0, amplifier) + 1.0D));
  }

  private long getReapplyCooldownMillis(int level) {
    return Math.max(1000, (long) Math.round(getConfig().reapplyCooldownMillisBase - (getLevelPercent(level) * getConfig().reapplyCooldownMillisFactor)));
  }


  @ConfigDescription("Projectiles can pin targets with heavy slowness.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dampen Velocity On Proc for the Ranged Pinning Shot adaptation.", impact = "True enables this behavior and false disables it.")
    boolean dampenVelocityOnProc = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double procChanceBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Proc Chance Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double procChanceFactor = 0.42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Proc Chance for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxProcChance = 0.65;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksBase = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration Ticks Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double durationTicksFactor = 90;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplifier Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amplifierBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amplifier Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reapply Cooldown Millis Base for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reapplyCooldownMillisBase = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reapply Cooldown Millis Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reapplyCooldownMillisFactor = 2800;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Horizontal Velocity Factor for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double horizontalVelocityFactor = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cleanup Threshold for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int cleanupThreshold = 128;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Entry Ttl Millis for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long entryTtlMillis = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Proc for the Ranged Pinning Shot adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnProc = 12;

    public Config() {
      costFactor = 0.74;
      maxLevel = 6;
      initialCost = 4;
    }
  }
}
