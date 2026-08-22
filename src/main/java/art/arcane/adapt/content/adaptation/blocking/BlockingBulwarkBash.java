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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockingBulwarkBash extends SimpleAdaptation<BlockingBulwarkBash.Config> {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 12;
  private final Cooldowns sprintTimestamps = cooldowns();

  public BlockingBulwarkBash() {
    super("blocking-bulwark-bash");
    registerConfiguration(Config.class);
    setIcon(Material.BELL);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_bulwark_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_blocking_bulwark_500", "blocking.bulwark-bash.mobs-bashed", 500, 500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_bulwark_4")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(level)), 1);
    statLore(v, Form.pc(getDamageBonus(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSprintEvent e) {
    if (e.isSprinting()) {
      sprintTimestamps.mark(e.getPlayer().getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    LivingEntity target = combat.target();
    if (p.getInventory().getItemInOffHand().getType() != Material.SHIELD || p.hasCooldown(Material.SHIELD)) {
      return;
    }

    if (!isJumpCrit(p) || !wasRecentlySprinting(p)) {
      return;
    }

    int level = combat.level();
    double radius = getRange(level);
    Location playerOrigin = p.getLocation().clone();
    Vector fallbackDirection = playerOrigin.getDirection().clone().setY(0);
    Location center = target.getLocation().clone();
    int targetFxLimit = getTargetFxLimit();
    applyImpact(target, playerOrigin, fallbackDirection, getKnockback(level), getUpwardKnockback(level),
        getStunTicks(level), getStunAmplifier(level), targetFxLimit > 0);

    e.setDamage(e.getDamage() + getBaseDamage(level));
    p.setCooldown(Material.SHIELD, getCooldownTicks(level));

    List<LivingEntity> candidates = collectCandidates(p, target, center, radius);
    if (candidates.isEmpty() || getAffectedLimit() <= 1) {
      finishBash(p, radius, 1, getConfig().xpPerTargetHit);
      return;
    }

    BulwarkBatch batch = new BulwarkBatch(p, playerOrigin, fallbackDirection, center, radius,
        getKnockback(level), getUpwardKnockback(level), getStunTicks(level), getStunAmplifier(level),
        getAffectedLimit(), targetFxLimit, getConfig().xpPerTargetHit, targetFxLimit > 0, candidates.size());
    for (LivingEntity hit : candidates) {
      if (!J.runEntity(hit, () -> inspectCandidateOwned(batch, hit))) {
        batch.complete();
      }
    }
    J.runEntity(p, batch::finishTimedOut, 3);
  }

  private List<LivingEntity> collectCandidates(Player player, LivingEntity primaryTarget, Location center, double radius) {
    int limit = getCandidateLimit();
    List<LivingEntity> candidates = new ArrayList<>(limit);
    for (LivingEntity candidate : PaperCompat.nearbyLivingEntities(center, radius, radius, radius)) {
      if (candidate == player || candidate == primaryTarget) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(BulwarkBatch batch, LivingEntity target) {
    if (batch.isFinalized() || !batch.hasAffectedCapacity()) {
      batch.complete();
      return;
    }
    Location targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    boolean playerTarget = target instanceof Player;
    if (!J.runEntity(batch.player, () -> authorizeCandidate(batch, target, targetLocation, playerTarget))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(BulwarkBatch batch, LivingEntity target, Location targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget ? canPVP(batch.player, targetLocation) : canPVE(batch.player, targetLocation);
    if (!allowed || !J.runEntity(target, () -> applyImpactOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyImpactOwned(BulwarkBatch batch, LivingEntity target) {
    if (batch.isFinalized()) {
      return;
    }
    if (validTargetLocation(batch, target) == null || !batch.claimAffected()) {
      batch.complete();
      return;
    }

    applyImpact(target, batch.playerOrigin, batch.fallbackDirection, batch.knockback,
        batch.upwardKnockback, batch.stunTicks, batch.stunAmplifier, batch.claimTargetFx());
    batch.complete();
  }

  private Location validTargetLocation(BulwarkBatch batch, LivingEntity target) {
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
        || Math.abs(location.getX() - batch.center.getX()) > batch.radius
        || Math.abs(location.getY() - batch.center.getY()) > batch.radius
        || Math.abs(location.getZ() - batch.center.getZ()) > batch.radius) {
      return null;
    }
    return location;
  }

  private void finishBash(Player player, double radius, int affected, double xpPerTargetHit) {
    boolean hype = affected >= 4;
    bashShockwave(player, radius, hype);
    xp(player, xpPerTargetHit * affected);
    addStat(player, "blocking.bulwark-bash.mobs-bashed", affected);
    if (hype) {
      grantOnce(player, "challenge_blocking_bulwark_4");
    }
  }

  private void bashShockwave(Player p, double radius, boolean hype) {
    FxEmitter burst = fx(p, FxPriority.GAMEPLAY)
        .particle(Particle.SWEEP_ATTACK, 1, 0, 1.0D, 0, 0, 0)
        .particle(Particle.CLOUD, 18, 0, 0.3D, 0, 0.35D, 0.06D);
    if (hype) {
      burst.burst(Particle.FLASH, 1, 0)
          .chord(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.85F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 0.7F, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.0F);
    } else {
      burst.chord(Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.85F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 0.7F, Sound.ENTITY_GENERIC_EXPLODE, 0.5F, 1.4F);
    }

    double maxRadius = Math.max(0.5D, radius);
    timeline(p.getLocation())
        .duration(5)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(Math.max(16.0D, maxRadius * 3.0D))
        .frame((fxE, tick, progress) -> {
          fxE.ring(Particle.CLOUD, 0.4D + (maxRadius * progress), 8, 0.2D);
          if (tick == 0) {
            fxE.sound(Sound.BLOCK_ANVIL_PLACE, 0.4F, 0.8F);
          }
        })
        .start();
  }

  private void applyImpact(LivingEntity target, Location playerOrigin, Vector fallbackDirection,
                           double knockback, double upwardKnockback, int stunTicks, int stunAmplifier,
                           boolean visual) {
    Vector kb = target.getLocation().toVector().subtract(playerOrigin.toVector()).setY(0);
    if (kb.lengthSquared() <= 0.0001) {
      kb = fallbackDirection.clone();
    }
    if (kb.lengthSquared() <= 0.0001) {
      kb = new Vector(1, 0, 0);
    }

    kb.normalize();
    target.setVelocity(target.getVelocity().multiply(0.25D).add(kb.multiply(knockback).setY(upwardKnockback)));
    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stunTicks, stunAmplifier, false, false, true), true);

    if (visual) {
      fx(target.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT)
          .burst(Particles.CRIT_MAGIC, 3, 0.2D);
    }
  }

  private boolean wasRecentlySprinting(Player p) {
    return p.isSprinting() || !sprintTimestamps.isReady(p.getUniqueId(), getConfig().recentSprintWindowMillis);
  }

  private boolean isJumpCrit(Player p) {
    return p.getFallDistance() > getConfig().minFallDistanceForCrit
        && !p.isOnGround()
        && !p.isInWater()
        && !p.isInsideVehicle()
        && !p.isClimbing();
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private double getDamageBonus(int level) {
    return getConfig().damageBonusBase + (getLevelPercent(level) * getConfig().damageBonusFactor);
  }

  private double getBaseDamage(int level) {
    return getConfig().baseDamage + getDamageBonus(level);
  }

  private double getKnockback(int level) {
    return getConfig().knockbackBase + (getLevelPercent(level) * getConfig().knockbackFactor);
  }

  private double getUpwardKnockback(int level) {
    return getConfig().upwardKnockbackBase + (getLevelPercent(level) * getConfig().upwardKnockbackFactor);
  }

  private int getStunTicks(int level) {
    return Math.max(10, (int) Math.round(getConfig().stunTicksBase + (getLevelPercent(level) * getConfig().stunTicksFactor)));
  }

  private int getStunAmplifier(int level) {
    return Math.max(0, (int) Math.round(getConfig().stunAmplifierBase + (getLevelPercent(level) * getConfig().stunAmplifierFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private int getCandidateLimit() {
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(1, Math.min(Math.min(getConfig().maxAffectedPerActivation, getCandidateLimit() + 1), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  private int getTargetFxLimit() {
    return Math.max(0, Math.min(getConfig().maxTargetFxPerActivation, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }


  @ConfigDescription("Sprint, jump, and land a shielded crit to unleash a bash shockwave.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseDamage = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusBase = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Bonus Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageBonusFactor = 2.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 2.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 1.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Upward Knockback Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double upwardKnockbackBase = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Upward Knockback Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double upwardKnockbackFactor = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stun Ticks Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stunTicksBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stun Ticks Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stunTicksFactor = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stun Amplifier Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stunAmplifierBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stun Amplifier Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stunAmplifierFactor = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 220;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Fall Distance For Crit for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    float minFallDistanceForCrit = 0.08f;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Recent Sprint Window Millis for the Blocking Bulwark Bash adaptation.", impact = "Allows crit bash to trigger shortly after sprint momentum, even if sprint toggles off mid-air.")
    long recentSprintWindowMillis = 900L;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Hit for the Blocking Bulwark Bash adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTargetHit = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum secondary living targets inspected by one bulwark bash.", impact = "Lower values reduce dense-entity scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum total targets affected by one bulwark bash, including the primary target.", impact = "Lower values cap combat work and rewards in dense groups; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum affected targets that receive individual impact particles.", impact = "Lower values reduce particle dispatch while preserving the main shockwave; values are clamped to an absolute maximum of 12.")
    int maxTargetFxPerActivation = 6;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }

  private final class BulwarkBatch {
    private final Player player;
    private final UUID playerId;
    private final Location playerOrigin;
    private final Vector fallbackDirection;
    private final Location center;
    private final double radius;
    private final double knockback;
    private final double upwardKnockback;
    private final int stunTicks;
    private final int stunAmplifier;
    private final int affectedLimit;
    private final int targetFxLimit;
    private final double xpPerTargetHit;
    private final AtomicInteger remaining;
    private final AtomicInteger affected = new AtomicInteger(1);
    private final AtomicInteger targetFx;
    private final AtomicBoolean finalized = new AtomicBoolean();

    private BulwarkBatch(Player player, Location playerOrigin, Vector fallbackDirection, Location center,
                         double radius, double knockback, double upwardKnockback, int stunTicks,
                         int stunAmplifier, int affectedLimit, int targetFxLimit, double xpPerTargetHit,
                         boolean primaryFx, int candidates) {
      this.player = player;
      playerId = player.getUniqueId();
      this.playerOrigin = playerOrigin;
      this.fallbackDirection = fallbackDirection;
      this.center = center;
      this.radius = radius;
      this.knockback = knockback;
      this.upwardKnockback = upwardKnockback;
      this.stunTicks = stunTicks;
      this.stunAmplifier = stunAmplifier;
      this.affectedLimit = affectedLimit;
      this.targetFxLimit = targetFxLimit;
      this.xpPerTargetHit = xpPerTargetHit;
      remaining = new AtomicInteger(candidates);
      targetFx = new AtomicInteger(primaryFx ? 1 : 0);
    }

    private synchronized boolean claimAffected() {
      if (finalized.get() || affected.get() >= affectedLimit) {
        return false;
      }
      affected.incrementAndGet();
      return true;
    }

    private boolean hasAffectedCapacity() {
      return affected.get() < affectedLimit;
    }

    private boolean claimTargetFx() {
      return claim(targetFx, targetFxLimit);
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

    private void complete() {
      if (finalized.get() || remaining.decrementAndGet() != 0 || !markFinalized()) {
        return;
      }
      J.runEntity(player, () -> finishBash(player, radius, affected.get(), xpPerTargetHit));
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        finishBash(player, radius, affected.get(), xpPerTargetHit);
      }
    }
  }
}
