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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AxeGroundSmash extends SimpleAdaptation<AxeGroundSmash.Config> {
  private static final int BATCH_TIMEOUT_TICKS = 3;
  private static final int HARD_MAX_CANDIDATES = 128;

  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Boolean> armedPlayers = playerState();

  public AxeGroundSmash() {
    super("axe-ground-smash");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_AXE);
    setInterval(4333);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_AXE)
        .key("challenge_axe_ground_smash_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_AXE)
        .key("challenge_axe_ground_smash_5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_ground_smash_500", "axe.ground-smash.mobs-hit", 500, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "+ ", Form.f(getFalloffDamage(f), 1) + " - " + Form.f(getDamage(f), 1), 1);
    statLore(v, C.RED, "+ ", Form.f(getRadius(f), 1), 2);
    statLore(v, C.RED, "+ ", Form.pc(getForce(f), 0), 3);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1), 4);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    UUID playerId = p.getUniqueId();
    if (!e.isSneaking()) {
      armedPlayers.remove(playerId);
      return;
    }

    ItemStack mainHand = p.getInventory().getItemInMainHand();
    int level = getActiveLevel(p);
    long cooldownMs = getCooldownTime(getLevelPercent(level)) * 50L;
    if (shouldArm(e.isSneaking(), p.isOnGround(), isAxe(mainHand), level,
        cooldowns.isReady(playerId, cooldownMs))) {
      armedPlayers.put(playerId, Boolean.TRUE);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null) {
      return;
    }

    Player p = e.getPlayer();
    UUID playerId = p.getUniqueId();
    if (!p.isSneaking()) {
      armedPlayers.remove(playerId);
      return;
    }
    if (!p.isOnGround() || armedPlayers.remove(playerId) == null) {
      return;
    }

    ItemStack mainHand = p.getInventory().getItemInMainHand();
    int level = getActiveLevel(p);
    if (!shouldActivate(p.isOnGround(), p.isSneaking(), isAxe(mainHand), level)) {
      return;
    }

    long cooldownMs = getCooldownTime(getLevelPercent(level)) * 50L;
    if (!cooldowns.isReady(playerId, cooldownMs)) {
      return;
    }

    cooldowns.mark(playerId);
    smash(p, level);
  }

  static boolean shouldArm(boolean sneaking, boolean onGround, boolean holdingAxe,
      int level, boolean cooldownReady) {
    return sneaking && !onGround && holdingAxe && level > 0 && cooldownReady;
  }

  static boolean shouldActivate(boolean onGround, boolean sneaking, boolean holdingAxe,
      int level) {
    return onGround && sneaking && holdingAxe && level > 0;
  }

  private void smash(Player p, int level) {
    double f = getLevelPercent(level);
    double radius = getRadius(f);
    Location center = p.getLocation().clone();
    List<LivingEntity> candidates = collectCandidates(p, center, radius);
    renderSmash(center, radius);
    if (candidates.isEmpty()) {
      return;
    }

    GroundSmashBatch batch = new GroundSmashBatch(
        p,
        center,
        radius,
        getDamage(f),
        getFalloffDamage(f),
        getForce(f),
        candidates.size()
    );
    for (LivingEntity target : candidates) {
      if (!J.runEntity(target, () -> inspectCandidateOwned(batch, target))) {
        batch.complete();
      }
    }
    J.runEntity(p, batch::finishTimedOut, BATCH_TIMEOUT_TICKS);
  }

  private List<LivingEntity> collectCandidates(Player player, Location center, double radius) {
    List<LivingEntity> candidates = new ArrayList<>(HARD_MAX_CANDIDATES);
    for (LivingEntity candidate : PaperCompat.nearbyLivingEntities(center, radius)) {
      if (candidate == player) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= HARD_MAX_CANDIDATES) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(GroundSmashBatch batch, LivingEntity target) {
    if (batch.isFinalized()) {
      return;
    }

    Location targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    boolean playerTarget = target instanceof Player;
    if (!J.runEntity(batch.player,
        () -> authorizeCandidate(batch, target, targetLocation, playerTarget))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(GroundSmashBatch batch, LivingEntity target,
      Location targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget
        ? canPVP(batch.player, targetLocation)
        : canPVE(batch.player, targetLocation);
    if (!allowed || !J.runEntity(target, () -> applyImpactOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyImpactOwned(GroundSmashBatch batch, LivingEntity target) {
    if (batch.isFinalized()) {
      return;
    }

    Location targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    Vector offset = targetLocation.toVector().subtract(batch.center.toVector());
    double distance = Math.sqrt(offset.lengthSquared());
    double damage = falloffValue(batch.maximumDamage, batch.minimumDamage, distance, batch.radius);
    double force = falloffValue(batch.maximumForce, 0D, distance, batch.radius);
    try {
      target.damage(damage, batch.player);
      if (force > 0D && offset.lengthSquared() > 1.0E-8D) {
        target.setVelocity(target.getVelocity().add(offset.normalize().multiply(force)));
      }
      batch.markHit();
    } finally {
      batch.complete();
    }
  }

  private Location validTargetLocation(GroundSmashBatch batch, LivingEntity target) {
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
    if (location.getWorld() != batch.center.getWorld()
        || location.distanceSquared(batch.center) > batch.radiusSquared) {
      return null;
    }
    return location;
  }

  static double falloffValue(double maximum, double minimum, double distance, double radius) {
    if (!Double.isFinite(maximum) || !Double.isFinite(minimum)
        || !Double.isFinite(distance) || !Double.isFinite(radius) || radius <= 0D) {
      return 0D;
    }

    double progress = 1D - Math.min(1D, Math.max(0D, distance / radius));
    return minimum + ((maximum - minimum) * progress);
  }

  private void renderSmash(Location center, double radius) {
    int points = (int) Math.min(24.0D, Math.max(8.0D, radius * 2.0D));
    timeline(center)
        .duration(6)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          double r = 0.4D + ((radius - 0.4D) * progress);
          fx.ring(Particles.CRIT_MAGIC, r, points, 0.2D);
          fx.dustRing(r, points, 1.1F);
          if (tick == 0) {
            fx.burst(Particles.SMOKE, 6, 0.35D);
            fx.chord(Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.6F, 0.4F, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5F, 0.1F, Sound.ENTITY_TURTLE_EGG_CRACK, 1.0F, 0.4F);
          }
        })
        .start();
  }


  public int getCooldownTime(double factor) {
    return (int) (((1D - factor) * getConfig().cooldownTicksInverseLevelMultiplier) + getConfig().cooldownTicksBase);
  }

  public double getRadius(double factor) {
    return getConfig().radiusLevelFactorMultiplier * factor;
  }

  public double getDamage(double factor) {
    return getConfig().damageLevelFactorMultiplier * factor;
  }

  public double getForce(double factor) {
    return (getConfig().forceFactorMultiplier * factor) + getConfig().forceBase;
  }

  public double getFalloffDamage(double factor) {
    return getConfig().falloffFactor * factor;
  }

  private final class GroundSmashBatch {
    private final Player player;
    private final UUID playerId;
    private final Location center;
    private final double radius;
    private final double radiusSquared;
    private final double maximumDamage;
    private final double minimumDamage;
    private final double maximumForce;
    private final AtomicInteger remaining;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private GroundSmashBatch(Player player, Location center, double radius,
        double maximumDamage, double minimumDamage, double maximumForce, int candidateCount) {
      this.player = player;
      playerId = player.getUniqueId();
      this.center = center;
      this.radius = radius;
      radiusSquared = radius * radius;
      this.maximumDamage = maximumDamage;
      this.minimumDamage = minimumDamage;
      this.maximumForce = maximumForce;
      remaining = new AtomicInteger(candidateCount);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void markHit() {
      hits.incrementAndGet();
    }

    private void complete() {
      if (remaining.decrementAndGet() == 0) {
        finish();
      }
    }

    private void finishTimedOut() {
      finish();
    }

    private void finish() {
      if (finalized.compareAndSet(false, true)) {
        J.runEntity(player, this::finishOwnerOwned);
      }
    }

    private void finishOwnerOwned() {
      if (!player.isOnline()) {
        return;
      }
      int totalHits = hits.get();
      if (totalHits <= 0) {
        return;
      }
      addStat(player, "axe.ground-smash.mobs-hit", totalHits);
      if (totalHits >= 5) {
        grantOnce(player, "challenge_axe_ground_smash_5");
      }
    }
  }


  @ConfigDescription("Jump then crouch to smash all nearby enemies with your axe.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Falloff Factor for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double falloffFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Level Factor Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusLevelFactorMultiplier = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Level Factor Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageLevelFactorMultiplier = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Factor Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceFactorMultiplier = 1.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Force Base for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double forceBase = 0.27;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Inverse Level Multiplier for the Axe Ground Smash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksInverseLevelMultiplier = 225;

    public Config() {
      baseCost = 6;
      costFactor = 0.75;
      initialCost = 8;
    }
  }
}
