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
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public class NetherBlazeLeech extends SimpleAdaptation<NetherBlazeLeech.Config> {
  public NetherBlazeLeech() {
    super("nether-blaze-leech");
    registerConfiguration(Config.class);
    setIcon(Material.BLAZE_POWDER);
    setInterval(900);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BLAZE_ROD)
        .key("challenge_nether_blaze_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BLAZE_POWDER)
            .key("challenge_nether_blaze_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_nether_blaze_200", "nether.blaze-leech.health-from-fire", 200, 300);
    registerMilestone("challenge_nether_blaze_2500", "nether.blaze-leech.health-from-fire", 2500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getTriggerChance(level), 0), 1);
    statLore(v, Form.duration(getRegenTicks(level) * 50D, 1), 2);
    statLore(v, Form.f(getFoodRestore(level)), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (!isFireCause(e.getCause()) || !isReady(p, level)) {
        return;
      }

      if (ThreadLocalRandom.current().nextDouble() > getTriggerChance(level)) {
        return;
      }

      applyLeech(p, level, true);
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!canDamageTarget(p, target)) {
        return;
      }

      int level = getActiveLevel(p);
      if (target.getFireTicks() <= 0 || !isReady(p, level)) {
        return;
      }

      if (ThreadLocalRandom.current().nextDouble() > getTriggerChance(level)) {
        return;
      }

      applyLeech(p, level, false);
    });
  }

  private boolean isFireCause(EntityDamageEvent.DamageCause cause) {
    return cause == EntityDamageEvent.DamageCause.FIRE
        || cause == EntityDamageEvent.DamageCause.FIRE_TICK
        || cause == EntityDamageEvent.DamageCause.LAVA
        || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
  }

  private boolean isReady(Player p, int level) {
    long now = System.currentTimeMillis();
    long next = getStorageLong(p, "blazeLeechNext", 0L);
    if (next > now) {
      return false;
    }

    setStorage(p, "blazeLeechNext", now + getCooldownMillis(level));
    return true;
  }

  private void applyLeech(Player p, int level, boolean defensive) {
    int newFood = Math.min(20, p.getFoodLevel() + (int) Math.round(getFoodRestore(level)));
    p.setFoodLevel(newFood);
    float sat = Math.min(20f, p.getSaturation() + (float) getConfig().saturationRestore);
    p.setSaturation(sat);

    int amp = Math.max(0, (int) Math.floor(getRegenAmplifier(level)));
    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, getRegenTicks(level), amp, true, false, true), true);

    float pitch = defensive ? 1.4F : 1.7F;
    FxEmitter emit = fx(p.getLocation(), FxPriority.COMBAT)
        .helix(Particle.FLAME, 0.5D, 1.9D, 8, 0D)
        .helix(Particle.SOUL, 0.5D, 1.9D, 4, Math.PI)
        .particle(Particle.HAPPY_VILLAGER, 3, 0D, 1.0D, 0D, 0.3D, 0.0D)
        .chord(Sound.ENTITY_BLAZE_AMBIENT, 0.45F, pitch, Sound.BLOCK_FIRE_AMBIENT, 0.2F, 1.0F);
    if (!defensive) {
      emit.sound(Sound.ENTITY_BLAZE_DEATH, 0.25F, 1.8F);
    }
    xp(p, defensive ? getConfig().xpOnDefensiveProc : getConfig().xpOnOffensiveProc);
    addStat(p, "nether.blaze-leech.health-from-fire", 1);
  }

  private double getTriggerChance(int level) {
    return Math.min(getConfig().maxTriggerChance, getConfig().triggerChanceBase + (getLevelPercent(level) * getConfig().triggerChanceFactor));
  }

  private int getRegenTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().regenTicksBase + (getLevelPercent(level) * getConfig().regenTicksFactor)));
  }

  private double getRegenAmplifier(int level) {
    return getConfig().regenAmplifierBase + (getLevelPercent(level) * getConfig().regenAmplifierFactor);
  }

  private double getFoodRestore(int level) {
    return getConfig().foodRestoreBase + (getLevelPercent(level) * getConfig().foodRestoreFactor);
  }

  private long getCooldownMillis(int level) {
    return Math.max(100L, Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  @ConfigDescription("Fire interactions can grant hunger and regeneration in short bursts.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trigger Chance Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double triggerChanceBase = 0.16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trigger Chance Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double triggerChanceFactor = 0.34;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Trigger Chance for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxTriggerChance = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Regen Ticks Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double regenTicksBase = 28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Regen Ticks Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double regenTicksFactor = 42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Regen Amplifier Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double regenAmplifierBase = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Regen Amplifier Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double regenAmplifierFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Restore Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodRestoreBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Restore Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodRestoreFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Saturation Restore for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saturationRestore = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 1400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 900;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Defensive Proc for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnDefensiveProc = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Offensive Proc for the Nether Blaze Leech adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnOffensiveProc = 5;

    public Config() {
      baseCost = 3;
      costFactor = 0.62;
      initialCost = 3;
    }
  }
}
