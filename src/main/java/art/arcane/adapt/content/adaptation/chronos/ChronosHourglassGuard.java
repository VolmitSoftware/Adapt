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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChronosHourglassGuard extends SimpleAdaptation<ChronosHourglassGuard.Config> {
  private final Map<UUID, Long> cooldowns;
  private final Map<UUID, Long> invulnerableUntil;

  public ChronosHourglassGuard() {
    super("chronos-hourglass-guard");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("chronos.hourglass_guard.description"));
    setDisplayName(Localizer.dLocalize("chronos.hourglass_guard.name"));
    setIcon(Material.TOTEM_OF_UNDYING);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(1000);
    cooldowns = new ConcurrentHashMap<>();
    invulnerableUntil = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TOTEM_OF_UNDYING)
        .key("challenge_chronos_hourglass_10")
        .title(Localizer.dLocalize("advancement.challenge_chronos_hourglass_10.title"))
        .description(Localizer.dLocalize("advancement.challenge_chronos_hourglass_10.description"))
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

    if (areParticlesEnabled()) {
      p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.35, 0.5, 0.35, 0.12);
      p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 10, 0.25, 0.4, 0.25, 0.03);
    }

    if (getConfig().playClockSounds) {
      ChronosSoundFX.playRewindFinish(p);
    }

    getPlayer(p).getData().addStat("chronos.hourglass-guard.saves", 1);
    xp(p, p.getLocation(), getConfig().xpOnSave + (level * getConfig().xpPerLevel));
  }

  private void slowNearbyEnemies(Player p) {
    double radius = getConfig().enemySlowRadius;
    double radiusSq = radius * radius;
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
    }
  }

  @Override
  public void onTick() {
    long now = M.ms();
    invulnerableUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("A killing blow instead leaves you at half a heart, granting brief invulnerability and slowing nearby enemies, on a long cooldown.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Hourglass Guard adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 3;
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
  }
}
