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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TragoulCurseOfFrailty extends SimpleAdaptation<TragoulCurseOfFrailty.Config> {
  private static final Color FRAILTY_GREEN = Color.fromRGB(90, 120, 40);
  private final Map<UUID, Long> attackerCooldowns = new ConcurrentHashMap<>();

  public TragoulCurseOfFrailty() {
    super("tragoul-curse-of-frailty");
    registerConfiguration(Config.class);
    setIcon(Material.FERMENTED_SPIDER_EYE);
    setInterval(5000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FERMENTED_SPIDER_EYE)
        .key("challenge_tragoul_frailty_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WITHER_SKELETON_SKULL)
            .key("challenge_tragoul_frailty_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_frailty_100", "tragoul.curse-of-frailty.curses-applied", 100, 400);
    registerMilestone("challenge_tragoul_frailty_1k", "tragoul.curse-of-frailty.curses-applied", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.curse_of_frailty.lore1"));
    v.addLore(C.GREEN + "+ " + Form.duration(getCurseDurationTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.curse_of_frailty.lore2"));
    if (getLevelPercent(level) >= getConfig().slownessUnlockPercent) {
      v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.curse_of_frailty.lore3"));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    LivingEntity attacker = null;
    if (e.getDamager() instanceof LivingEntity living) {
      attacker = living;
    } else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
      attacker = shooter;
    }

    if (attacker == null || attacker == p) {
      return;
    }

    long now = System.currentTimeMillis();
    Long until = attackerCooldowns.get(attacker.getUniqueId());
    if (until != null && until > now) {
      return;
    }

    LivingEntity target = attacker;
    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !canDamageTarget(p, target)) {
        return;
      }

      attackerCooldowns.put(target.getUniqueId(), now + getConfig().perAttackerCooldownMillis);
      int duration = getCurseDurationTicks(level);
      int weaknessAmplifier = getLevelPercent(level) >= 0.8 ? 1 : 0;
      boolean heavy = getLevelPercent(level) >= getConfig().slownessUnlockPercent;
      target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, weaknessAmplifier, true, true, true), true);
      if (heavy) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, getConfig().slownessAmplifier, true, true, true), true);
      }

      playCurse(target, heavy);
      addStat(p, "tragoul.curse-of-frailty.curses-applied", 1);
      xp(p, getConfig().xpPerCurse);
    });
  }

  private void playCurse(LivingEntity target, boolean heavy) {
    timeline(target)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.dustRing(FRAILTY_GREEN, 1.5 - (1.0 * progress), 8, 0.1F);
          if (tick == 0) {
            fx.particle(Particle.WARPED_SPORE, 20, 0, 1.0, 0, 0.3, 0.02);
            fx.particle(Particle.WITCH, 3, 0, 1.8, 0, 0.2, 0.01);
            fx.chord(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.35F, 1.7F, Sound.ENTITY_ZOMBIE_INFECT, 0.25F, 0.7F);
            if (heavy) {
              fx.burst(Particles.SMOKE, 3, 0.2);
            }
          }
        })
        .start();
  }

  private int getCurseDurationTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().curseDurationTicksBase + (getLevelPercent(level) * getConfig().curseDurationTicksFactor)));
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    attackerCooldowns.entrySet().removeIf(i -> i.getValue() <= now);
  }

  @ConfigDescription("Enemies that strike you are cursed with weakness, and slowness at higher levels.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Curse duration in ticks before level scaling.", impact = "Higher values keep attackers weakened longer.")
    double curseDurationTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional curse duration ticks granted at max level.", impact = "Higher values increase level-scaled duration growth.")
    double curseDurationTicksFactor = 100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Level progress required before slowness is added to the curse.", impact = "Lower values unlock the slowness component earlier.")
    double slownessUnlockPercent = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Amplifier of the slowness component once unlocked.", impact = "Higher values slow cursed attackers more.")
    int slownessAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Per-attacker cooldown in milliseconds between curses.", impact = "Higher values stop one attacker from being re-cursed in quick succession.")
    long perAttackerCooldownMillis = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per curse applied.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerCurse = 5;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
