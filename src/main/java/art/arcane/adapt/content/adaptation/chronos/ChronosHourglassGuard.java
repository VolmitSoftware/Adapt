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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
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
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

public class ChronosHourglassGuard extends SimpleAdaptation<ChronosHourglassGuard.Config> {
  private static final double HARD_MAX_ENEMY_SLOW_RADIUS = 12D;
  private static final int HARD_MAX_CANDIDATES_PER_SAVE = 32;
  private static final int HARD_MAX_AFFECTED_PER_SAVE = 16;
  private static final int HARD_MAX_TARGET_CHAINS_PER_WINDOW = 128;

  private final Map<UUID, Long> cooldowns = playerState();
  private final Map<UUID, Long> invulnerableUntil = playerState();
  private final Map<UUID, Boolean> cooldownReadyNotify = playerState();
  private final HourglassWorkBudget slowBudget = new HourglassWorkBudget(
      HARD_MAX_TARGET_CHAINS_PER_WINDOW,
      50L,
      System::currentTimeMillis
  );

  public ChronosHourglassGuard() {
    super("chronos-hourglass-guard");
    registerConfiguration(Config.class);
    setIcon(Material.TOTEM_OF_UNDYING);
    setInterval(1000);
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
    statLore(v, (getConfig().invulnerabilityMillis / 1000D) + "s", 1);
    statLore(v, C.RED, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.HOURGLASS_GUARD_LORE3));
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
    cooldownReadyNotify.remove(id);
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

    cooldownReadyNotify.put(id, true);
    addStat(p, "chronos.hourglass-guard.saves", 1);
    xp(p, p.getLocation(), getConfig().xpOnSave + (level * getConfig().xpPerLevel));
  }

  private void slowNearbyEnemies(Player p) {
    int targetBudget = slowBudget.reserve(HARD_MAX_CANDIDATES_PER_SAVE);
    if (targetBudget <= 0) {
      return;
    }
    Location center = p.getLocation();
    double radius = Math.max(1D, Math.min(HARD_MAX_ENEMY_SLOW_RADIUS, getConfig().enemySlowRadius));
    List<LivingEntity> candidates = new ArrayList<>(targetBudget);
    for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
      if (!(entity instanceof LivingEntity living) || entity.getUniqueId().equals(p.getUniqueId())) {
        continue;
      }
      candidates.add(living);
      if (candidates.size() >= targetBudget) {
        break;
      }
    }

    SlowBatch batch = new SlowBatch(
        p,
        center,
        radius,
        Math.max(1, Math.min(1200, getConfig().enemySlowTicks)),
        Math.max(0, Math.min(4, getConfig().enemySlowAmplifier))
    );
    for (LivingEntity candidate : candidates) {
      J.runEntity(candidate, () -> inspectSlowCandidateOwned(batch, candidate));
    }
  }

  private void inspectSlowCandidateOwned(SlowBatch batch, LivingEntity target) {
    SlowTargetSnapshot snapshot = captureSlowTarget(batch, target);
    if (snapshot == null || !batch.hasCapacity()) {
      return;
    }
    J.runEntity(batch.player, () -> authorizeSlowCandidateOwned(batch, target, snapshot));
  }

  private SlowTargetSnapshot captureSlowTarget(SlowBatch batch, LivingEntity target) {
    if (!target.isValid() || target.isDead() || isProtectedFriendly(null, target)) {
      return null;
    }
    if (target instanceof Tameable tameable && tameable.isTamed()) {
      AnimalTamer owner = tameable.getOwner();
      if (owner != null && batch.playerId.equals(owner.getUniqueId())) {
        return null;
      }
    }
    Location location = target.getLocation();
    if (!isInsideSlowArea(batch, location)) {
      return null;
    }
    return new SlowTargetSnapshot(location, target instanceof Player);
  }

  private void authorizeSlowCandidateOwned(SlowBatch batch, LivingEntity target, SlowTargetSnapshot snapshot) {
    if (!batch.player.isOnline() || !batch.hasCapacity()) {
      return;
    }
    boolean allowed = snapshot.playerTarget
        ? canPVP(batch.player, snapshot.location)
        : canPVE(batch.player, snapshot.location);
    if (allowed) {
      J.runEntity(target, () -> applySlowOwned(batch, target));
    }
  }

  private void applySlowOwned(SlowBatch batch, LivingEntity target) {
    if (captureSlowTarget(batch, target) == null || !batch.claimAffected()) {
      return;
    }
    target.addPotionEffect(new PotionEffect(
        PotionEffectType.SLOWNESS,
        batch.slowTicks,
        batch.slowAmplifier,
        true,
        false,
        false
    ), true);
    if (batch.claimFx()) {
      fx(target.getLocation(), FxPriority.COMBAT).ring(Particles.END_ROD, 0.9D, 3, 0.15D);
    }
  }

  private boolean isInsideSlowArea(SlowBatch batch, Location location) {
    return location.getWorld() == batch.center.getWorld()
        && location.distanceSquared(batch.center) <= batch.radiusSquared;
  }

  @Override
  public void onTick() {
    long now = M.ms();
    invulnerableUntil.entrySet().removeIf(entry -> entry.getValue() <= now);

    if (!cooldownReadyNotify.isEmpty()) {
      for (UUID id : cooldownReadyNotify.keySet()) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
          cooldownReadyNotify.remove(id);
          continue;
        }

        if (cooldowns.getOrDefault(id, 0L) <= now && cooldownReadyNotify.remove(id) != null) {
          J.runEntity(p, () -> {
            if (!p.isOnline()) {
              return;
            }
            FxPresets.readyPing(this, p);
            if (getConfig().playClockSounds) {
              ChronosSoundFX.playCooldownReady(p);
            }
          });
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius in blocks for the slow applied to nearby enemies on save.", impact = "Higher values slow enemies from further away up to the hard 12-block radius limit.")
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

  private record SlowTargetSnapshot(Location location, boolean playerTarget) {
  }

  private static final class SlowBatch {
    private final Player player;
    private final UUID playerId;
    private final Location center;
    private final double radiusSquared;
    private final int slowTicks;
    private final int slowAmplifier;
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger renderedFx = new AtomicInteger();

    private SlowBatch(Player player, Location center, double radius, int slowTicks, int slowAmplifier) {
      this.player = player;
      this.playerId = player.getUniqueId();
      this.center = center;
      this.radiusSquared = radius * radius;
      this.slowTicks = slowTicks;
      this.slowAmplifier = slowAmplifier;
    }

    private boolean hasCapacity() {
      return affected.get() < HARD_MAX_AFFECTED_PER_SAVE;
    }

    private boolean claimAffected() {
      return claim(affected, HARD_MAX_AFFECTED_PER_SAVE);
    }

    private boolean claimFx() {
      return claim(renderedFx, 8);
    }

    private boolean claim(AtomicInteger counter, int limit) {
      int current = counter.get();
      while (current < limit) {
        if (counter.compareAndSet(current, current + 1)) {
          return true;
        }
        current = counter.get();
      }
      return false;
    }
  }

  static final class HourglassWorkBudget {
    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private long windowStartedAt = Long.MIN_VALUE;
    private int acquired;

    HourglassWorkBudget(int limit, long windowMillis, LongSupplier clock) {
      this.limit = Math.max(1, limit);
      this.windowMillis = Math.max(1L, windowMillis);
      this.clock = clock;
    }

    synchronized int reserve(int requested) {
      long now = clock.getAsLong();
      if (windowStartedAt == Long.MIN_VALUE || now < windowStartedAt || now - windowStartedAt >= windowMillis) {
        windowStartedAt = now;
        acquired = 0;
      }
      int granted = Math.min(Math.max(0, requested), limit - acquired);
      acquired += granted;
      return granted;
    }
  }
}
