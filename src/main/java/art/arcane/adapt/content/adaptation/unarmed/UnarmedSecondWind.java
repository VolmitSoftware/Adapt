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

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class UnarmedSecondWind extends SimpleAdaptation<UnarmedSecondWind.Config> {
  private final Cooldowns secondWindCooldown = cooldowns();

  public UnarmedSecondWind() {
    super("unarmed-second-wind");
    registerConfiguration(Config.class);
    setIcon(Material.COOKED_BEEF);
    setInterval(4960);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_second_wind_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_second_wind_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_second_wind_100", "unarmed.second-wind.second-winds", 100, 400);
    registerMilestone("challenge_unarmed_second_wind_1k", "unarmed.second-wind.second-winds", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getFoodRestore(level), 1);
    statLore(v, Form.duration(getRegenDurationTicks(level) * 50D, 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (!(e.getEntity() instanceof LivingEntity victim) || victim instanceof Player) {
      return;
    }

    if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg) || !(dmg.getDamager() instanceof Player p)) {
      return;
    }

    if (isProtectedFriendly(p, victim)) {
      return;
    }

    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!secondWindCooldown.isReady(p.getUniqueId(), getConfig().cooldownMillis)) {
      return;
    }

    secondWindCooldown.mark(p.getUniqueId());
    int foodRestore = getFoodRestore(level);
    int regenTicks = getRegenDurationTicks(level);
    J.runEntity(p, () -> {
      if (!p.isOnline() || p.isDead()) {
        return;
      }
      p.setFoodLevel(Math.min(20, p.getFoodLevel() + foodRestore));
      p.setSaturation((float) Math.min(p.getFoodLevel(), p.getSaturation() + getConfig().saturationRestore));
      p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenTicks, getConfig().regenAmplifier, false, true, true), true);
    });

    playBloom(p);
    xp(p, getConfig().xpPerSecondWind, "second-wind");
    addStat(p, "unarmed.second-wind.second-winds", 1);
  }

  private void playBloom(Player p) {
    fx(p.getLocation().add(0, 1.6D, 0), FxPriority.TRANSITION)
        .chord(Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.7F, Sound.ENTITY_GENERIC_EAT, 0.4F, 1.2F);
    timeline(p)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.particle(Particle.HEART, 3, 0, 1.4D + (progress * 0.4D), 0, 0.3D, 0.0D);
          fx.particle(Particle.WAX_ON, 2, 0, 1.2D + (progress * 0.4D), 0, 0.25D, 0.0D);
        })
        .start();
  }

  private int getFoodRestore(int level) {
    return Math.max(1, (int) Math.round(getConfig().foodRestoreBase + (getLevelPercent(level) * getConfig().foodRestoreFactor)));
  }

  private int getRegenDurationTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().regenDurationTicksBase + (getLevelPercent(level) * getConfig().regenDurationTicksFactor)));
  }


  @ConfigDescription("Bare-hand kills restore hunger and grant a short regeneration burst.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base hunger points restored per bare-hand kill.", impact = "Higher values restore more food per kill.")
    double foodRestoreBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional hunger points restored at max level.", impact = "Higher values restore more food per kill as levels increase.")
    double foodRestoreFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Saturation restored per bare-hand kill.", impact = "Higher values keep the hunger bar full for longer.")
    double saturationRestore = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base regeneration duration in ticks per bare-hand kill.", impact = "Higher values keep the regen burst active longer.")
    double regenDurationTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional regeneration duration ticks granted at max level.", impact = "Higher values keep the regen burst active longer as levels increase.")
    double regenDurationTicksFactor = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Regeneration amplifier applied by the burst.", impact = "Higher values heal faster during the burst.")
    int regenAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between second wind triggers.", impact = "Higher values prevent rapid kill-chains from spamming the heal.")
    long cooldownMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per second wind trigger.", impact = "Higher values speed up unarmed skill progression from kills.")
    double xpPerSecondWind = 18;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      initialCost = 4;
    }
  }
}
