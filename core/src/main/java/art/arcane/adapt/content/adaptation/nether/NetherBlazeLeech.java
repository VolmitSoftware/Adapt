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
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.concurrent.ThreadLocalRandom;

public class NetherBlazeLeech extends SimpleAdaptation<NetherBlazeLeech.Config> {
  private static final Id HOT_FLOOR = Id.minecraft("hot_floor");
  private static final Id REGENERATION = Id.minecraft("regeneration");

  public NetherBlazeLeech() {
    super("nether-blaze-leech");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("blaze_powder"));
    setInterval(900);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("blaze_rod"))
        .key("challenge_nether_blaze_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("blaze_powder"))
            .key("challenge_nether_blaze_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_blaze_200", "nether.blaze-leech.health-from-fire", 200, 300);
    registerMilestone("challenge_nether_blaze_2500", "nether.blaze-leech.health-from-fire", 2500, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getTriggerChance(level), 0), 1);
    statLore(v, Form.duration(getRegenTicks(level) * 50D, 1), 2);
    statLore(v, Form.f(getFoodRestore(level)), 3);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (!isFireDamage(e) || !isReady(p)) {
        return;
      }

      if (ThreadLocalRandom.current().nextDouble() > getTriggerChance(level)) {
        return;
      }

      markCooldown(p, level);
      applyLeech(p, level, true);
    });
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p) || !(e.entity() instanceof LivingRef target)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!canDamageTarget(p, target)) {
        return;
      }

      int level = getActiveLevel(p);
      if (target.fireTicks() <= 0 || !isReady(p)) {
        return;
      }

      if (ThreadLocalRandom.current().nextDouble() > getTriggerChance(level)) {
        return;
      }

      markCooldown(p, level);
      applyLeech(p, level, false);
    });
  }

  static boolean isFireDamage(EntityDamageEvent event) {
    DamageCause cause = event.cause();
    return cause == DamageCause.FIRE
        || cause == DamageCause.FIRE_TICK
        || cause == DamageCause.LAVA
        || HOT_FLOOR.equals(event.damageType());
  }

  private boolean isReady(PlayerRef p) {
    long now = System.currentTimeMillis();
    long next = getStorageLong(p, "blazeLeechNext", 0L);
    return next <= now;
  }

  private void markCooldown(PlayerRef p, int level) {
    setStorage(p, "blazeLeechNext", System.currentTimeMillis() + getCooldownMillis(level));
  }

  private void applyLeech(PlayerRef p, int level, boolean defensive) {
    int newFood = Math.min(20, p.foodLevel() + (int) Math.round(getFoodRestore(level)));
    p.setFoodLevel(newFood);
    float sat = Math.min(20f, p.saturation() + (float) getConfig().saturationRestore);
    p.setSaturation(sat);

    int amp = Math.max(0, (int) Math.floor(getRegenAmplifier(level)));
    p.addEffect(new EffectInstance(REGENERATION, getRegenTicks(level), amp, true, false, true));

    float pitch = defensive ? 1.4F : 1.7F;
    FxEmitter emit = fx(p.location(), FxPriority.COMBAT)
        .helix(Particles.FLAME, 0.5D, 1.9D, 8, 0D)
        .helix(Particles.SOUL, 0.5D, 1.9D, 4, Math.PI)
        .particle(Particles.HAPPY_VILLAGER, 3, 0D, 1.0D, 0D, 0.3D, 0.0D)
        .chord(Sounds.ENTITY_BLAZE_AMBIENT, 0.45F, pitch, Sounds.BLOCK_FIRE_AMBIENT, 0.2F, 1.0F);
    if (!defensive) {
      emit.sound(Sounds.ENTITY_BLAZE_DEATH, 0.25F, 1.8F);
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
