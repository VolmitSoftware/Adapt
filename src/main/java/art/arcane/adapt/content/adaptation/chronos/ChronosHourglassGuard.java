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
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChronosHourglassGuard extends SimpleAdaptation<ChronosHourglassGuard.Config> {
  private final Map<UUID, Long> cooldowns;
  private final Map<UUID, Long> invulnerableUntil;
  private final Set<UUID> cooldownReadyNotify;

  public ChronosHourglassGuard() {
    super("chronos-hourglass-guard");
    registerConfiguration(Config.class);
    setIcon(Material.TOTEM_OF_UNDYING);
    setInterval(1000);
    cooldowns = new ConcurrentHashMap<>();
    invulnerableUntil = new ConcurrentHashMap<>();
    cooldownReadyNotify = ConcurrentHashMap.newKeySet();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TOTEM_OF_UNDYING)
        .key("challenge_chronos_hourglass_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_hourglass_10", "chronos.hourglass-guard.saves", 10, 800);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + (getConfig().invulnerabilityMillis / 1000D) + "s " + Localizer.dLocalize("chronos.hourglass_guard.lore1"));
    v.addLore(C.RED + "* " + Form.duration(getCooldownMillis(level), 1) + " " + Localizer.dLocalize("chronos.hourglass_guard.lore2"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.hourglass_guard.lore3"));
  }

  private long getCooldownMillis(int level) {
    return Math.max(getConfig().minimumCooldownMillis,
        getConfig().baseCooldownMillis - (Math.max(1, level) * getConfig().cooldownReductionPerLevelMillis));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    cooldowns.remove(id);
    invulnerableUntil.remove(id);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    UUID id = p.getUniqueId();
    long now = M.ms();
    if (invulnerableUntil.getOrDefault(id, 0L) > now) {
      e.setCancelled(true);
      return;
    }

    if (p.getHealth() - e.getFinalDamage() > 0D) {
      return;
    }

    if (cooldowns.getOrDefault(id, 0L) > now) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    e.setCancelled(true);
    p.setHealth(Math.max(0.5D, getConfig().survivalHealth));
    invulnerableUntil.put(id, now + getConfig().invulnerabilityMillis);
    cooldowns.put(id, now + getCooldownMillis(level));
    p.setNoDamageTicks(Math.max(p.getNoDamageTicks(), (int) (getConfig().invulnerabilityMillis / 50L)));

    slowNearbyEnemies(p);

    Location saveCenter = p.getLocation();
    fx(saveCenter, FxPriority.GAMEPLAY)
        .particle(Particle.FLASH, 1, 0, 1.0D, 0, 0, 0)
        .sound(Sound.BLOCK_BELL_USE, 0.7F, 0.5F);
    timeline(p)
        .duration(6)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(48)
        .frame((f, tick, progress) -> {
          f.dome(Particles.END_ROD, 2.6D - (2.0D * progress), 16);
          if (tick >= 3) {
            f.particle(Particles.TOTEM, 6, 0, 1.0D, 0, 0.25D, 0.05D);
          }
        })
        .start();

    if (getConfig().playClockSounds) {
      ChronosSoundFX.playRewindFinish(p);
    }

    cooldownReadyNotify.add(id);
    addStat(p, "chronos.hourglass-guard.saves", 1);
    xp(p, p.getLocation(), getConfig().xpOnSave + (level * getConfig().xpPerLevel));
  }

  private void slowNearbyEnemies(Player p) {
    double radius = getConfig().enemySlowRadius;
    double radiusSq = radius * radius;
    int pulsed = 0;
    for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
      if (!(entity instanceof LivingEntity living) || entity.getUniqueId().equals(p.getUniqueId())) {
        continue;
      }

      if (entity.getLocation().distanceSquared(p.getLocation()) > radiusSq) {
        continue;
      }

      if (isProtectedFriendly(p, living)) {
        continue;
      }

      if (living instanceof Player target && !canDamageTarget(p, target)) {
        continue;
      }

      living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
          getConfig().enemySlowTicks, getConfig().enemySlowAmplifier, true, false, false), true);

      if (pulsed < 8) {
        fx(living.getLocation(), FxPriority.COMBAT).ring(Particles.END_ROD, 0.9D, 3, 0.15D);
        pulsed++;
      }
    }
  }

  @Override
  public void onTick() {
    long now = M.ms();
    invulnerableUntil.entrySet().removeIf(entry -> entry.getValue() <= now);

    if (!cooldownReadyNotify.isEmpty()) {
      for (Iterator<UUID> iterator = cooldownReadyNotify.iterator(); iterator.hasNext(); ) {
        UUID id = iterator.next();
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
          iterator.remove();
          continue;
        }

        if (cooldowns.getOrDefault(id, 0L) <= now) {
          FxPresets.readyPing(this, p);
          if (getConfig().playClockSounds) {
            ChronosSoundFX.playCooldownReady(p);
          }
          iterator.remove();
        }
      }
    }

    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  @ConfigDescription("A killing blow instead leaves you at half a heart, granting brief invulnerability and slowing nearby enemies, on a long cooldown.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Hourglass Guard adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Health the player is left with after a save.", impact = "Higher values leave the player healthier after cheating death.")
    double survivalHealth = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Invulnerability window in milliseconds after a save.", impact = "Higher values protect the player longer after a save.")
    long invulnerabilityMillis = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown in milliseconds between saves.", impact = "Higher values force longer waits between saves.")
    long baseCooldownMillis = 480000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds per adaptation level.", impact = "Higher values make leveling shorten the cooldown faster.")
    long cooldownReductionPerLevelMillis = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest possible cooldown in milliseconds regardless of level.", impact = "Higher values keep a floor under cooldown reduction.")
    long minimumCooldownMillis = 180000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius in blocks for the slow applied to nearby enemies on save.", impact = "Higher values slow enemies from further away.")
    double enemySlowRadius = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the slow applied to nearby enemies.", impact = "Higher values keep enemies slowed longer.")
    int enemySlowTicks = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Slowness amplifier applied to nearby enemies on save.", impact = "Higher values slow enemies more severely.")
    int enemySlowAmplifier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted when a save triggers.", impact = "Higher values grant more skill XP per save.")
    double xpOnSave = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra XP granted per adaptation level on save.", impact = "Higher values scale save XP with level faster.")
    double xpPerLevel = 10;

    public Config() {
      baseCost = 9;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 8;
    }
  }
}
