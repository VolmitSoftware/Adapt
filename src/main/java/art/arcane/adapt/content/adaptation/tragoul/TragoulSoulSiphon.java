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
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.UUID;

public class TragoulSoulSiphon extends SimpleAdaptation<TragoulSoulSiphon.Config> {
  private final Map<UUID, HealBudget> budgets = playerState();

  public TragoulSoulSiphon() {
    super("tragoul-soul-siphon");
    registerConfiguration(Config.class);
    setIcon(Material.SOUL_LANTERN);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SOUL_LANTERN)
        .key("challenge_tragoul_siphon_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SOUL_CAMPFIRE)
            .key("challenge_tragoul_siphon_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_siphon_500", "tragoul.soul-siphon.health-siphoned", 500, 400);
    registerMilestone("challenge_tragoul_siphon_10k", "tragoul.soul-siphon.health-siphoned", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getHealPercent(level), 0) + C.GRAY + " " + Localizer.dLocalize("tragoul.soul_siphon.lore1"));
    v.addLore(C.YELLOW + "* " + Form.f(getHealCapPerSecond(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.soul_siphon.lore2"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p)) {
      return;
    }

    EntityDamageEvent.DamageCause cause = e.getCause();
    if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
      return;
    }

    if (!(e.getEntity() instanceof LivingEntity victim)) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0 || !canDamageTarget(p, e.getEntity())) {
        return;
      }

      long now = System.currentTimeMillis();
      HealBudget budget = budgets.computeIfAbsent(p.getUniqueId(), k -> new HealBudget());
      if (now - budget.windowStart >= 1000L) {
        budget.windowStart = now;
        budget.healed = 0;
        budget.capNotified = false;
      }

      double remaining = getHealCapPerSecond(level) - budget.healed;
      if (remaining <= 0) {
        if (!budget.capNotified) {
          budget.capNotified = true;
          fx(p.getLocation().add(0, 1.2, 0), FxPriority.TRANSITION)
              .burst(Particles.SMOKE, 2, 0.2)
              .sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.15F, 1.4F);
        }
        return;
      }

      double heal = Math.min(remaining, e.getFinalDamage() * getHealPercent(level));
      if (heal <= 0) {
        return;
      }

      IAttribute attribute = Version.get().getAttribute(p, Attributes.GENERIC_MAX_HEALTH);
      double maxHealth = attribute == null ? 20D : attribute.getValue();
      double newHealth = Math.min(maxHealth, p.getHealth() + heal);
      if (newHealth <= p.getHealth()) {
        return;
      }

      budget.healed += heal;
      p.setHealth(newHealth);
      Location pchest = p.getLocation().add(0, 1.2, 0);
      fx(victim.getLocation().add(0, 1.0, 0), FxPriority.COMBAT)
          .particle(Particle.SOUL, 3, 0, 0, 0, 0.15, 0.02)
          .line(Particle.SOUL, pchest.getX(), pchest.getY(), pchest.getZ(), 4)
          .sound(Sound.PARTICLE_SOUL_ESCAPE, 0.3F, 1.4F);
      addStat(p, "tragoul.soul-siphon.health-siphoned", heal);
      xp(p, getConfig().xpPerHeal);
    });
  }

  private double getHealPercent(int level) {
    return Math.max(0, getConfig().healPercentBase + (getLevelPercent(level) * getConfig().healPercentFactor));
  }

  private double getHealCapPerSecond(int level) {
    return Math.max(0.5, getConfig().healCapPerSecondBase + (getLevelPercent(level) * getConfig().healCapPerSecondFactor));
  }

  @Override
  public void onTick() {
  }

  private static class HealBudget {
    private long windowStart;
    private double healed;
    private boolean capNotified;
  }

  @ConfigDescription("Melee hits heal you for a portion of the damage dealt, capped per second.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of melee damage returned as healing before level scaling.", impact = "Higher values increase baseline lifesteal.")
    double healPercentBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional lifesteal fraction granted at max level.", impact = "Higher values increase level-scaled lifesteal growth.")
    double healPercentFactor = 0.20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum health restored per second before level scaling.", impact = "Lower values harden the anti-abuse healing cap.")
    double healCapPerSecondBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional per-second healing cap granted at max level.", impact = "Higher values let high levels sustain more healing per second.")
    double healCapPerSecondFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per successful siphon heal.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerHeal = 3;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
