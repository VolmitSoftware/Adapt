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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChronosPocketWatch extends SimpleAdaptation<ChronosPocketWatch.Config> {
  private static final long PULSE_MILLIS = 250L;

  private final Map<UUID, Long> airBudgetMillis;

  public ChronosPocketWatch() {
    super("chronos-pocket-watch");
    registerConfiguration(Config.class);
    setIcon(Material.FEATHER);
    setInterval(PULSE_MILLIS);
    airBudgetMillis = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.FEATHER)
        .key("challenge_chronos_pocket_watch_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_pocket_watch_500", "chronos.pocket-watch.slow-fall-seconds", 500, 650);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + (getBudgetMillis(level) / 1000D) + "s " + Localizer.dLocalize("chronos.pocket_watch.lore1"));
    v.addLore(C.YELLOW + "* " + Localizer.dLocalize("chronos.pocket_watch.lore2"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.pocket_watch.lore3"));
  }

  private long getBudgetMillis(int level) {
    return (long) ((getConfig().baseBudgetSeconds + (Math.max(1, level) * getConfig().budgetSecondsPerLevel)) * 1000D);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    airBudgetMillis.remove(e.getPlayer().getUniqueId());
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }

      UUID id = p.getUniqueId();
      if (p.isOnGround()) {
        airBudgetMillis.remove(id);
        continue;
      }

      int level = getActiveLevel(p, Player::isSneaking);
      if (level <= 0) {
        continue;
      }

      if (p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isSwimming()) {
        continue;
      }

      if (!airBudgetMillis.containsKey(id) && p.getFallDistance() < getConfig().minFallDistance) {
        continue;
      }

      if (getConfig().requireClock && !p.getInventory().contains(Material.CLOCK)) {
        continue;
      }

      long max = getBudgetMillis(level);
      boolean firstPulse = !airBudgetMillis.containsKey(id);
      long budget = airBudgetMillis.computeIfAbsent(id, k -> max);
      if (budget < PULSE_MILLIS) {
        continue;
      }

      long remaining = budget - PULSE_MILLIS;
      airBudgetMillis.put(id, remaining);

      boolean activate = firstPulse;
      boolean deplete = remaining < PULSE_MILLIS;
      int pulseIndex = (int) ((max - remaining) / PULSE_MILLIS);
      float driftPitch = max <= 0 ? 1.8F : (float) (1.2D + (0.6D * (1D - ((double) remaining / (double) max))));

      Runnable apply = () -> {
        if (!p.isOnline() || p.isDead()) {
          return;
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
            getConfig().pulseDurationTicks, 0, true, false, false), true);

        Location feet = p.getLocation();
        if (activate) {
          fx(feet, FxPriority.TRANSITION)
              .burst(Particle.CLOUD, 6, 0.2D)
              .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.2F);
        } else if (deplete) {
          fx(feet, FxPriority.TRANSITION)
              .burst(Particles.SMOKE, 3, 0.2D)
              .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.3F, 0.7F);
        } else {
          FxEmitter drift = fx(feet, FxPriority.TRAIL)
              .particle(Particle.CLOUD, 2, 0, 0.1D, 0, 0.1D, 0.01D);
          if ((pulseIndex & 3) == 0) {
            drift.particle(Particles.END_ROD, 1, 0, 0.2D, 0, 0.05D, 0.02D);
          }
          if ((pulseIndex & 1) == 0) {
            drift.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.15F, driftPitch);
          }
        }
      };

      if (J.isFoliaThreading()) {
        J.runEntity(p, apply);
      } else {
        apply.run();
      }

      addStat(p, "chronos.pocket-watch.slow-fall-seconds", PULSE_MILLIS / 1000D);
      xpSilent(p, getConfig().xpPerPulse, "chronos:pocket-watch");
    }
  }

  @ConfigDescription("Sneak while airborne to fall in slow motion, with a level scaled time budget per airtime.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base seconds of slow falling sustainable per airtime.", impact = "Higher values let the player drift longer each fall.")
    double baseBudgetSeconds = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra sustain seconds per adaptation level.", impact = "Higher values make leveling extend the drift budget faster.")
    double budgetSecondsPerLevel = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of each refreshing slow falling pulse.", impact = "Higher values leave slow falling lingering longer after sneaking stops.")
    int pulseDurationTicks = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum fall distance in blocks before the slow fall kicks in.", impact = "Lower values start the slow fall earlier in the drop.")
    double minFallDistance = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Requires a clock anywhere in the inventory for the slow fall to apply.", impact = "False removes the item requirement entirely.")
    boolean requireClock = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per slow fall pulse.", impact = "Higher values grant more skill XP while drifting.")
    double xpPerPulse = 0.3;

    public Config() {
      costFactor = 0.35;
      initialCost = 3;
    }
  }
}
