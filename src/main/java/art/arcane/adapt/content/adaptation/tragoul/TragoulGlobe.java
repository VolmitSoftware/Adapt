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
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TragoulGlobe extends SimpleAdaptation<TragoulGlobe.Config> {
  private static final double HARD_MAX_RANGE = 24D;
  private static final int MAX_CANDIDATES_PER_ACTIVATION = 24;
  private static final int MAX_SHARED_TARGETS_PER_ACTIVATION = 8;
  private static final int MAX_ACTIVATIONS_PER_WINDOW = 32;
  private static final int MAX_SHARED_TARGETS_PER_WINDOW = 256;
  private static final long WORK_WINDOW_MILLIS = 50L;
  private static final long MIN_COOLDOWN_MILLIS = 500L;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Boolean> sharingDamage = playerState();
  private final GlobeWorkBudget workBudget = new GlobeWorkBudget(
      MAX_ACTIVATIONS_PER_WINDOW,
      MAX_SHARED_TARGETS_PER_WINDOW,
      WORK_WINDOW_MILLIS
  );

  public TragoulGlobe() {
    super("tragoul-globe");
    registerConfiguration(TragoulGlobe.Config.class);
    setIcon(Material.CRYING_OBSIDIAN);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLASS)
        .key("challenge_tragoul_globe_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_tragoul_globe_1k", "tragoul.globe.mobs-shared-with", 1000, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GLASS)
        .key("challenge_tragoul_globe_5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.globe.lore1"));
    v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.globe.lore2") + Form.f((getConfig().rangePerLevel * level) + getConfig().initalRange, 1));
    v.addLore(C.YELLOW + Localizer.dLocalize("tragoul.globe.lore3") + Form.f(getConfig().bonusDamagePerLevel * level, 1));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity originalTarget)) {
      return;
    }

    if (!J.isOwnedByCurrentRegion(p) || !J.isOwnedByCurrentRegion(originalTarget)) {
      return;
    }

    UUID playerId = p.getUniqueId();
    int level = getActiveLevel(p);
    if (level <= 0 || sharingDamage.containsKey(playerId) || !canDamageTarget(p, originalTarget)) {
      return;
    }

    long cooldownMillis = getCooldownMillis();
    long now = System.currentTimeMillis();
    if (!cooldowns.isReady(playerId, cooldownMillis) || !workBudget.tryActivation(now)) {
      return;
    }

    double range = getRange(level);
    Location playerLocation = p.getLocation();
    Collection<LivingEntity> nearby = p.getWorld().getNearbyLivingEntities(playerLocation, range, range, range);
    List<LivingEntity> targets = collectTargets(p, originalTarget, nearby);
    int targetLimit = workBudget.reserveTargets(targets.size(), now);
    if (targetLimit <= 0) {
      return;
    }

    double originalDamage = e.getDamage();
    double damagePerEntity = Math.max(0D,
        (originalDamage / (targetLimit + 1D)) + getBonusDamage(level));
    e.setDamage(damagePerEntity);

    Location chest = playerLocation.clone().add(0, 1.0, 0);
    FxEmitter tether = fx(chest, FxPriority.COMBAT);
    int mobsSharedWith = shareDamage(p, playerId, targets, targetLimit, damagePerEntity, tether);
    if (mobsSharedWith <= 0) {
      e.setDamage(originalDamage);
      return;
    }

    cooldowns.mark(playerId);
    addStat(p, "tragoul.globe.mobs-shared-with", mobsSharedWith);
    playShareShockwave(playerLocation, range, mobsSharedWith);
    if (mobsSharedWith >= 5) {
      fx(chest, FxPriority.TRANSITION)
          .burst(Particle.SCULK_SOUL, 12, 0.5D)
          .sound(Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.6F, 1.2F);
      grantOnce(p, "challenge_tragoul_globe_5");
    }
  }

  private List<LivingEntity> collectTargets(Player player, LivingEntity originalTarget,
                                            Collection<LivingEntity> nearby) {
    List<LivingEntity> targets = new ArrayList<>(MAX_SHARED_TARGETS_PER_ACTIVATION);
    int examined = 0;
    for (LivingEntity candidate : nearby) {
      if (examined++ >= MAX_CANDIDATES_PER_ACTIVATION) {
        break;
      }
      if (candidate == player || candidate == originalTarget || !J.isOwnedByCurrentRegion(candidate)) {
        continue;
      }
      if (!candidate.isValid() || candidate.isDead() || !canDamageTarget(player, candidate)) {
        continue;
      }
      targets.add(candidate);
      if (targets.size() >= MAX_SHARED_TARGETS_PER_ACTIVATION) {
        break;
      }
    }
    return targets;
  }

  private int shareDamage(Player player, UUID playerId, List<LivingEntity> targets, int targetLimit,
                          double damage, FxEmitter tether) {
    int shared = 0;
    sharingDamage.put(playerId, Boolean.TRUE);
    try {
      for (int index = 0; index < targetLimit; index++) {
        LivingEntity target = targets.get(index);
        double healthBefore = target.getHealth() + target.getAbsorptionAmount();
        target.damage(damage, player);
        double healthAfter = target.isDead() ? 0D : target.getHealth() + target.getAbsorptionAmount();
        if (healthAfter >= healthBefore) {
          continue;
        }

        Location targetLocation = target.getLocation();
        tether.line(Particle.SOUL, targetLocation.getX(), targetLocation.getY() + 1.0, targetLocation.getZ(), 3);
        shared++;
      }
    } finally {
      sharingDamage.remove(playerId);
    }
    return shared;
  }

  private double getRange(int level) {
    double configured = (getConfig().rangePerLevel * level) + getConfig().initalRange;
    if (!Double.isFinite(configured)) {
      return 1D;
    }
    return Math.max(1D, Math.min(HARD_MAX_RANGE, configured));
  }

  private long getCooldownMillis() {
    double configured = getConfig().cooldown;
    if (!Double.isFinite(configured)) {
      return MIN_COOLDOWN_MILLIS;
    }
    return Math.max(MIN_COOLDOWN_MILLIS, (long) (1000D * configured));
  }

  private double getBonusDamage(int level) {
    double configured = getConfig().bonusDamagePerLevel * level;
    return Double.isFinite(configured) ? Math.max(0D, configured) : 0D;
  }

  private void playShareShockwave(Location center, double range, int mobsSharedWith) {
    if (mobsSharedWith <= 0) {
      return;
    }

    double inner = range * 0.3D;
    int ringPoints = Math.max(8, Math.min(24, mobsSharedWith * 4));
    timeline(center)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(range + 8)
        .frame((fx, tick, progress) -> {
          double radius = inner + ((range - inner) * progress);
          Color ringColor = Color.fromRGB((int) (150 - (120 * progress)), (int) (12 - (7 * progress)), (int) (12 - (7 * progress)));
          fx.dustRing(ringColor, radius, ringPoints, 1.2F);
          if (tick == 0) {
            fx.chord(Sound.ENTITY_WITHER_SHOOT, 0.4F, 1.6F, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5F, 0.9F);
          }
        })
        .start();
  }




  @ConfigDescription("Spread your damage among all nearby enemies.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldown = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Per Level for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangePerLevel = 3.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Inital Range for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double initalRange = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Damage Per Level for the Tragoul Globe adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamagePerLevel = 1;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }

  static final class GlobeWorkBudget {
    private final int activationLimit;
    private final int targetLimit;
    private final long windowMillis;
    private long windowStartedAt = Long.MIN_VALUE;
    private int activations;
    private int targets;

    GlobeWorkBudget(int activationLimit, int targetLimit, long windowMillis) {
      this.activationLimit = Math.max(1, activationLimit);
      this.targetLimit = Math.max(1, targetLimit);
      this.windowMillis = Math.max(1L, windowMillis);
    }

    synchronized boolean tryActivation(long now) {
      refresh(now);
      if (activations >= activationLimit) {
        return false;
      }
      activations++;
      return true;
    }

    synchronized int reserveTargets(int requested, long now) {
      refresh(now);
      int granted = Math.min(Math.max(0, requested), targetLimit - targets);
      targets += granted;
      return granted;
    }

    private void refresh(long now) {
      if (windowStartedAt == Long.MIN_VALUE || now - windowStartedAt >= windowMillis || now < windowStartedAt) {
        windowStartedAt = now;
        activations = 0;
        targets = 0;
      }
    }
  }
}
