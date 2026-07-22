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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SeaborneInkVeil extends SimpleAdaptation<SeaborneInkVeil.Config> {
  private static final int MAX_AFFECTED = 24;

  private final Cooldowns cloudCooldowns = cooldowns();

  public SeaborneInkVeil() {
    super("seaborne-ink-veil");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.ink_veil");
    setIcon(Material.INK_SAC);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.INK_SAC)
        .key("challenge_seaborne_ink_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GLOW_INK_SAC)
            .key("challenge_seaborne_ink_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_seaborne_ink_100", "seaborne.ink-veil.clouds-burst", 100, 300);
    registerMilestone("challenge_seaborne_ink_1k", "seaborne.ink-veil.clouds-burst", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getCloudSize(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    withPlayerThread(p, e, () -> {
      if (!p.isInWater()) {
        return;
      }

      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      if (!cloudCooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
        return;
      }

      burstInk(p, level);
      cloudCooldowns.mark(p.getUniqueId());
    });
  }

  private void burstInk(Player p, int level) {
    double radius = getCloudSize(level);
    Location center = p.getLocation().clone().add(0D, 1.0D, 0D);
    int blindTicks = getBlindTicks(level);

    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, getInvisTicks(level), 0, false, false, true));

    int affected = 0;
    for (Entity entity : p.getWorld().getNearbyEntities(center, radius, radius, radius)) {
      if (!(entity instanceof Monster monster)) {
        continue;
      }

      J.runEntity(monster, () -> {
        monster.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0, false, true, true));
        if ((monster instanceof Drowned || monster instanceof Guardian || monster instanceof ElderGuardian)
            && monster.getTarget() == p) {
          monster.setTarget(null);
        }
      });

      if (++affected >= MAX_AFFECTED) {
        break;
      }
    }

    addStat(p, "seaborne.ink-veil.clouds-burst", 1);
    xp(p, getConfig().burstXp);
    fx(center, FxPriority.COMBAT)
        .particle(Particle.SMOKE, (int) Math.min(48D, radius * 6D), 0D, 0D, 0D, radius * 0.5D, 0.01D)
        .particle(Particle.SOUL, (int) Math.min(24D, radius * 3D), 0D, 0D, 0D, radius * 0.4D, 0.01D)
        .dustBurst(Color.BLACK, (int) Math.min(24D, radius * 3D), radius * 0.5D, 1.4F)
        .chord(Sound.ENTITY_DOLPHIN_SPLASH, 0.6F, 0.6F, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5F, 0.7F);
  }

  private double getCloudSize(int level) {
    return cloudSize(getConfig().cloudSizeBase, getConfig().cloudSizeFactor, getLevelPercent(level));
  }

  private long getCooldownMillis(int level) {
    return cooldownMillis(getConfig().cooldownMillisBase, getConfig().cooldownMillisReduction, getLevelPercent(level));
  }

  private int getInvisTicks(int level) {
    return (int) Math.round(getConfig().invisTicksBase + (getLevelPercent(level) * getConfig().invisTicksFactor));
  }

  private int getBlindTicks(int level) {
    return (int) Math.round(getConfig().blindTicksBase + (getLevelPercent(level) * getConfig().blindTicksFactor));
  }

  static double cloudSize(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  static long cooldownMillis(long base, long reduction, double levelPercent) {
    return Math.max(3000L, (long) (base - (levelPercent * reduction)));
  }

  @ConfigDescription("Taking damage underwater bursts an ink cloud that blinds hostiles and briefly hides you from drowned and guardians.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base ink cloud radius in blocks.", impact = "Higher values blind hostiles across a wider area at low levels.")
    double cloudSizeBase = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional ink cloud radius gained across levels.", impact = "Higher values greatly widen the cloud at higher levels.")
    double cloudSizeFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown in milliseconds between ink bursts.", impact = "Lower values allow more frequent bursts; the effective cooldown shrinks as you level.")
    long cooldownMillisBase = 12000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown milliseconds removed at max level.", impact = "Higher values make higher levels burst far more often (floored at 3s).")
    long cooldownMillisReduction = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base invisibility duration in ticks granted on a burst.", impact = "Higher values keep you hidden longer at low levels.")
    double invisTicksBase = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional invisibility ticks gained across levels.", impact = "Higher values extend hidden time at higher levels.")
    double invisTicksFactor = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base blindness duration in ticks applied to nearby hostiles.", impact = "Higher values blind hostiles for longer at low levels.")
    double blindTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional blindness ticks gained across levels.", impact = "Higher values blind hostiles for longer at higher levels.")
    double blindTicksFactor = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus XP granted when an ink cloud bursts.", impact = "Higher values reward defensive bursts with more skill XP.")
    double burstXp = 10;

    public Config() {
      baseCost = 4;
      costFactor = 0.55;
      maxLevel = 5;
      initialCost = 4;
    }
  }
}
