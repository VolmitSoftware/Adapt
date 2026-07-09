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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ChronosBorrowedTime extends SimpleAdaptation<ChronosBorrowedTime.Config> {
  private final Map<UUID, Deque<DeferredDamage>> deferred;
  private final Set<UUID> applyingDeferred;

  public ChronosBorrowedTime() {
    super("chronos-borrowed-time");
    registerConfiguration(Config.class);
    setIcon(Material.SOUL_SAND);
    setInterval(1000);
    deferred = new ConcurrentHashMap<>();
    applyingDeferred = ConcurrentHashMap.newKeySet();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SOUL_SAND)
        .key("challenge_chronos_borrowed_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_borrowed_2500", "chronos.borrowed-time.damage-deferred", 2500, 900);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Math.round(getDeferFraction(level) * 100D) + "% " + Localizer.dLocalize("chronos.borrowed_time.lore1"));
    v.addLore(C.YELLOW + "+ " + getConfig().paybackPulses + "s " + Localizer.dLocalize("chronos.borrowed_time.lore2"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.borrowed_time.lore3"));
  }

  private double getDeferFraction(int level) {
    return Math.min(getConfig().maxDeferFraction,
        getConfig().baseDeferFraction + (Math.max(1, level) * getConfig().deferFractionPerLevel));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    deferred.remove(id);
    applyingDeferred.remove(id);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    UUID id = e.getEntity().getUniqueId();
    deferred.remove(id);
    applyingDeferred.remove(id);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    UUID id = p.getUniqueId();
    if (applyingDeferred.contains(id)) {
      return;
    }

    double finalDamage = e.getFinalDamage();
    if (finalDamage < getConfig().minimumDeferDamage) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    double fraction = getDeferFraction(level);
    if (fraction <= 0D) {
      return;
    }

    double deferredAmount = finalDamage * fraction;
    e.setDamage(Math.max(0D, e.getDamage() * (1D - fraction)));

    int pulses = Math.max(1, getConfig().paybackPulses);
    deferred.computeIfAbsent(id, k -> new ConcurrentLinkedDeque<>())
        .add(new DeferredDamage(deferredAmount / pulses, pulses));

    addStat(p, "chronos.borrowed-time.damage-deferred", deferredAmount);

    Location chest = p.getLocation().add(0, 1.0, 0);
    fx(chest, FxPriority.TRANSITION)
        .particle(Particle.SOUL, 4, 0, 0.1D, 0, 0.2D, 0.01D)
        .dustRing(Color.fromRGB(210, 150, 60), 0.6D, 8, 0.9F)
        .sound(Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.4F, 0.8F);
  }

  @Override
  public void onTick() {
    if (deferred.isEmpty()) {
      return;
    }

    for (Map.Entry<UUID, Deque<DeferredDamage>> entry : deferred.entrySet()) {
      UUID id = entry.getKey();
      Player p = Bukkit.getPlayer(id);
      if (p == null || !p.isOnline() || p.isDead()) {
        deferred.remove(id);
        continue;
      }

      Deque<DeferredDamage> queue = entry.getValue();
      double amount = 0D;
      Iterator<DeferredDamage> iterator = queue.iterator();
      while (iterator.hasNext()) {
        DeferredDamage chunk = iterator.next();
        amount += chunk.perPulse();
        if (chunk.consumePulse() <= 0) {
          iterator.remove();
        }
      }

      if (queue.isEmpty()) {
        deferred.remove(id);
      }
      boolean cleared = queue.isEmpty();

      if (amount <= 0D) {
        continue;
      }

      double damage = amount;
      Runnable apply = () -> {
        if (!p.isOnline() || p.isDead()) {
          return;
        }

        double health = p.getHealth();
        if (health <= 0D) {
          return;
        }

        double remaining = health - damage;
        if (remaining <= 0D) {
          applyingDeferred.add(id);
          try {
            p.damage(damage);
          } finally {
            applyingDeferred.remove(id);
          }
        } else {
          p.setHealth(remaining);
        }

        int motes = (int) Math.max(2, Math.min(5, Math.round(damage)));
        float pitch = 0.5F + Math.min(0.9F, (float) (damage * 0.12D));
        fx(p.getLocation().add(0, 1.0, 0), FxPriority.TRAIL)
            .particle(Particle.SOUL, motes, 0, 0.1D, 0, 0.2D, 0.02D)
            .sound(Sound.BLOCK_SAND_STEP, 0.2F, pitch);

        if (cleared) {
          fx(p.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
              .column(Particles.END_ROD, 6, 1.2D)
              .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.5F);
        }
      };

      if (J.isFoliaThreading()) {
        J.runEntity(p, apply);
      } else {
        apply.run();
      }
    }
  }

  @ConfigDescription("Defer a portion of incoming damage, paying it back in small ticks over the following seconds.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base fraction of incoming damage that is deferred.", impact = "Higher values move more damage into the payback window.")
    double baseDeferFraction = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra deferred fraction per adaptation level.", impact = "Higher values make leveling defer more damage.")
    double deferFractionPerLevel = 0.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hard cap on the deferred damage fraction.", impact = "Higher values allow more of each hit to be deferred.")
    double maxDeferFraction = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum final damage required before any deferral happens.", impact = "Higher values skip deferring small hits entirely.")
    double minimumDeferDamage = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Number of one second pulses the deferred damage is paid back over.", impact = "Higher values spread payback thinner over a longer window.")
    int paybackPulses = 10;

    public Config() {
      baseCost = 6;
      costFactor = 0.42;
      initialCost = 5;
    }
  }

  private static final class DeferredDamage {
    private final double perPulse;
    private int pulsesRemaining;

    private DeferredDamage(double perPulse, int pulsesRemaining) {
      this.perPulse = perPulse;
      this.pulsesRemaining = pulsesRemaining;
    }

    private double perPulse() {
      return perPulse;
    }

    private int consumePulse() {
      pulsesRemaining--;
      return pulsesRemaining;
    }
  }
}
