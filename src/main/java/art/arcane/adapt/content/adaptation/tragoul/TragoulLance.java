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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class TragoulLance extends SimpleAdaptation<TragoulLance.Config> {
  private static final Color LANCE_MAROON = Color.fromRGB(128, 0, 0);
  private final Cooldowns cooldowns = cooldowns();

  public TragoulLance() {
    super("tragoul-lance");
    registerConfiguration(TragoulLance.Config.class);
    setIcon(Material.TRIDENT);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_tragoul_lance_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DIAMOND_SWORD)
        .key("challenge_tragoul_lance_kills_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_tragoul_lance_200", "tragoul.lance.lances-spawned", 200, 400);
    registerMilestone("challenge_tragoul_lance_kills_100", "tragoul.lance.lance-kills", 100, 1000);
  }


  @EventHandler(priority = EventPriority.LOWEST)
  public void onEntityDeath(EntityDeathEvent event) {
    if (event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent e) {
      if (e.getDamager() instanceof Player p) {
        withAdaptedPlayer(p, () -> {
          int level = getActiveLevel(p);
          if (level <= 0 || !canDamageTarget(p, event.getEntity())) {
            return;
          }

          if (!cooldowns.isReady(p.getUniqueId(), 5000L)) {
            return;
          }

          cooldowns.mark(p.getUniqueId());
          double baseSeekerRange = 5 + 4 * level;
          double damageDealt = e.getDamage();
          double seekerDamage = getConfig().seekerDamageMultiplier * damageDealt;

          triggerSeeker(p, event.getEntity(), seekerDamage, level, baseSeekerRange, level);
          addStat(p, "tragoul.lance.lance-kills", 1);
        });
      }
    }
  }

  private void triggerSeeker(Player p, Entity origin, double damage, int remainingSeekers, double range, int totalSeekers) {
    if (remainingSeekers <= 0) {
      return;
    }

    LivingEntity nearest = null;
    double best = range * range;

    for (Entity e : origin.getNearbyEntities(range, range, range)) {
      if (e instanceof LivingEntity le && le != p) {
        double d2 = origin.getLocation().distanceSquared(le.getLocation());
        if (d2 < best) {
          nearest = le;
          best = d2;
        }
      }
    }

    if (nearest != null) {
      addStat(p, "tragoul.lance.lances-spawned", 1);
      playLanceLaunch(origin.getLocation());
      playLanceTravel(origin.getLocation(), nearest.getLocation());
      double seekerDamage = getConfig().seekerDamageMultiplier * damage;
      double selfDamage = getConfig().selfDamageMultiplier * seekerDamage;

      p.damage(selfDamage, p);
      fx(p, FxPriority.TRAIL)
          .particle(Particle.DAMAGE_INDICATOR, 2, 0, 1.0, 0, 0.1, 0.01)
          .sound(Sound.ENTITY_PLAYER_HURT, 0.2F, 1.0F);

      LivingEntity finalNearest = nearest;
      int generation = totalSeekers - remainingSeekers;
      J.runEntity(finalNearest, () -> {
        double remainingHealth = finalNearest.getHealth() - damage;
        finalNearest.damage(damage, p);
        float pitch = (float) Math.min(2.0, 1.0 + (generation * 0.1));
        fx(finalNearest, FxPriority.COMBAT)
            .burst(Particle.CRIT, 8, 0.4)
            .dustBurst(LANCE_MAROON, 6, 0.4, 1.0F)
            .chord(Sound.ITEM_TRIDENT_HIT, 0.7F, pitch, Sound.ENTITY_ARROW_HIT, 0.4F, 0.7F);
        if (remainingHealth <= 0) {
          triggerSeeker(p, finalNearest, damage * 0.5, remainingSeekers - 1, range, totalSeekers);
        }
      }, getConfig().seekerDelay);
    }
  }

  private void playLanceLaunch(Location origin) {
    fx(origin.clone().add(0, 1.0, 0), FxPriority.COMBAT)
        .burst(Particle.SCULK_SOUL, 6, 0.3)
        .chord(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.6F, 1.3F, Sound.ENTITY_WITHER_SHOOT, 0.3F, 1.8F);
  }

  private void playLanceTravel(Location origin, Location target) {
    Location start = origin.clone().add(0, 1.0, 0);
    double dx = target.getX() - start.getX();
    double dy = (target.getY() + 1.0) - start.getY();
    double dz = target.getZ() - start.getZ();
    timeline(start)
        .duration(Math.max(4, getConfig().seekerDelay))
        .priority(FxPriority.TRAIL)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.particle(Particle.SOUL_FIRE_FLAME, 2, dx * progress, dy * progress, dz * progress, 0.03, 0);
          double tail = Math.max(0.0, progress - 0.06);
          fx.particle(Particle.SOUL, 1, dx * tail, dy * tail, dz * tail, 0.02, 0);
        })
        .start();
  }


  @Override
  public void onTick() {
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.lance.lore1"));
    v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.lance.lore2"));
    v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.lance.lore3") + level);
  }

  @ConfigDescription("Killing an enemy spawns a lance that seeks and damages a nearby enemy.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Seeker Delay for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int seekerDelay = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Seeker Damage Multiplier for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double seekerDamageMultiplier = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Self Damage Multiplier for the Tragoul Lance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double selfDamageMultiplier = 0.5;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }
}
