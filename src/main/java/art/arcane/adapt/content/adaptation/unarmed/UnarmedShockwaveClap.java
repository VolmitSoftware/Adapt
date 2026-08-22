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

package art.arcane.adapt.content.adaptation.unarmed;

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
import org.bukkit.Color;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UnarmedShockwaveClap extends SimpleAdaptation<UnarmedShockwaveClap.Config> {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 16;
  private static final int HARD_MAX_TARGET_FX_PER_ACTIVATION = 12;
  private static final Color WAVE_COLOR = Color.WHITE;
  private final Map<UUID, Long> nextClapAt = playerState();
  private final Cooldowns failCue = cooldowns();

  public UnarmedShockwaveClap() {
    super("unarmed-shockwave-clap");
    registerConfiguration(Config.class);
    setIcon(Material.NOTE_BLOCK);
    setInterval(5230);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_clap_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_clap_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_clap_250", "unarmed.shockwave-clap.mobs-clapped", 250, 400);
    registerMilestone("challenge_unarmed_clap_2500", "unarmed.shockwave-clap.mobs-clapped", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(level)), 1);
    statLore(v, Form.f(getForce(level)), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getCooldownMillis(level), 1), 3);
    statLore(v, C.RED, "- ", getConfig().hungerCost, 4);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      return;
    }

    UUID id = p.getUniqueId();
    long now = System.currentTimeMillis();
    Long next = nextClapAt.get(id);
    if (next != null && now < next) {
      playBlocked(p);
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (p.getFoodLevel() < hungerCost) {
      playBlocked(p);
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    nextClapAt.put(id, now + getCooldownMillis(level));
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - hungerCost));

    double range = getRange(level);
    double force = getForce(level);
    Location origin = p.getLocation();
    Vector look = origin.getDirection().normalize();
    List<LivingEntity> candidates = collectCandidates(p, origin, range);
    playShockwave(origin, look, range);
    addStat(p, "unarmed.shockwave-clap.claps", 1);

    if (candidates.isEmpty()) {
      return;
    }

    ShockwaveBatch batch = new ShockwaveBatch(p, origin.clone(), look.clone(), range, force,
        getUpwardForce(level), getConfig().coneDotThreshold, getAffectedLimit(), getTargetFxLimit(),
        getConfig().xpPerTargetHit, candidates.size());
    for (LivingEntity target : candidates) {
      if (!J.runEntity(target, () -> inspectCandidateOwned(batch, target))) {
        batch.complete();
      }
    }
    J.runEntity(p, batch::finishTimedOut, 3);
  }

  private List<LivingEntity> collectCandidates(Player player, Location origin, double range) {
    int limit = getCandidateLimit();
    List<LivingEntity> candidates = new ArrayList<>(limit);
    for (LivingEntity candidate : PaperCompat.nearbyLivingEntities(origin, range, range, range)) {
      if (candidate == player) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(ShockwaveBatch batch, LivingEntity target) {
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

  private void authorizeCandidate(ShockwaveBatch batch, LivingEntity target, Location targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget ? canPVP(batch.player, targetLocation) : canPVE(batch.player, targetLocation);
    if (!allowed || !J.runEntity(target, () -> applyImpactOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyImpactOwned(ShockwaveBatch batch, LivingEntity target) {
    if (batch.isFinalized()) {
      return;
    }
    Location targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null || !batch.claimAffected()) {
      batch.complete();
      return;
    }

    Vector to = targetLocation.toVector().subtract(batch.origin.toVector());
    double lengthSquared = to.lengthSquared();
    to.multiply(1D / Math.sqrt(lengthSquared));
    target.setVelocity(target.getVelocity().multiply(0.2D).add(to.multiply(batch.force).setY(batch.upwardForce)));
    if (batch.claimTargetFx()) {
      fx(targetLocation.clone().add(0, 1, 0), FxPriority.COMBAT)
          .particle(Particle.CLOUD, 3, 0, 0, 0, 0.15D, 0.03D);
    }
    batch.complete();
  }

  private Location validTargetLocation(ShockwaveBatch batch, LivingEntity target) {
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
    if (location.getWorld() != batch.origin.getWorld()) {
      return null;
    }
    Vector to = location.toVector().subtract(batch.origin.toVector());
    double lengthSquared = to.lengthSquared();
    if (lengthSquared <= 0.0001D || lengthSquared > batch.rangeSquared) {
      return null;
    }
    to.multiply(1D / Math.sqrt(lengthSquared));
    return batch.look.dot(to) >= batch.coneDotThreshold ? location : null;
  }

  private void playShockwave(Location origin, Vector look, double range) {
    fx(origin, FxPriority.COMBAT)
        .chord(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F, Sound.BLOCK_BELL_RESONATE, 0.7F, 1.4F, Sound.ENTITY_GENERIC_EXPLODE, 0.4F, 0.7F);
    Location center = origin.clone().add(look.clone().multiply(0.6)).add(0, 1.0D, 0);
    fx(center, FxPriority.COMBAT)
        .particle(Particle.EXPLOSION, 1, 0, 0, 0, 0.1D, 0.02D)
        .particle(Particle.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0);
    int ringPoints = Math.min(28, 12 + (int) (range * 2));
    timeline(center)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(Math.min(48.0D, (range * 2.0D) + 8.0D))
        .frame((fx, tick, progress) -> fx.dustRing(WAVE_COLOR, 0.4D + (range * progress), ringPoints, 1.0F))
        .start();
  }

  private void playBlocked(Player p) {
    if (!failCue.isReady(p.getUniqueId(), 700L)) {
      return;
    }

    failCue.mark(p.getUniqueId());
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.25F, 0.5F);
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private double getUpwardForce(int level) {
    return getConfig().upwardForceBase + (getLevelPercent(level) * getConfig().upwardForceFactor);
  }

  private long getCooldownMillis(int level) {
    return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  private int getCandidateLimit() {
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(1, Math.min(Math.min(getConfig().maxAffectedPerActivation, getCandidateLimit()), HARD_MAX_AFFECTED_PER_ACTIVATION));
  }

  private int getTargetFxLimit() {
    return Math.max(0, Math.min(getConfig().maxTargetFxPerActivation, HARD_MAX_TARGET_FX_PER_ACTIVATION));
  }


  @ConfigDescription("Sneak and punch the air to clap a shockwave that knocks back enemies in a cone.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base shockwave range in blocks at level 1.", impact = "Higher values let the clap reach more distant enemies.")
    double rangeBase = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional shockwave range granted at max level.", impact = "Higher values extend the clap reach as levels increase.")
    double rangeFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knockback force at level 1.", impact = "Higher values launch enemies further.")
    double forceBase = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional knockback force granted at max level.", impact = "Higher values launch enemies further as levels increase.")
    double forceFactor = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base upward knockback component at level 1.", impact = "Higher values pop enemies into the air more.")
    double upwardForceBase = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional upward knockback granted at max level.", impact = "Higher values pop enemies higher as levels increase.")
    double upwardForceFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Look-direction dot threshold that defines the cone width.", impact = "Lower values widen the cone; higher values narrow it.")
    double coneDotThreshold = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base clap cooldown in milliseconds at level 1.", impact = "Higher values make the ability usable less often.")
    double cooldownMillisBase = 10000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values make the ability recharge faster as levels increase.")
    double cooldownMillisFactor = 6000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hunger points consumed per shockwave clap.", impact = "Higher values drain more food per clap; activation fails when food is below the cost. Set to 0 to disable the hunger cost.")
    int hungerCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per enemy knocked back by a clap.", impact = "Higher values speed up unarmed skill progression from claps.")
    double xpPerTargetHit = 14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum living targets inspected by one shockwave clap.", impact = "Lower values reduce dense-entity scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum living targets knocked back by one shockwave clap.", impact = "Lower values cap combat work and rewards in dense groups; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum knocked-back targets that receive individual cloud particles.", impact = "Lower values reduce particle dispatch while preserving the main shockwave; values are clamped to an absolute maximum of 12.")
    int maxTargetFxPerActivation = 8;

    public Config() {
      baseCost = 5;
      costFactor = 0.7;
      initialCost = 6;
    }
  }

  private final class ShockwaveBatch {
    private final Player player;
    private final UUID playerId;
    private final Location origin;
    private final Vector look;
    private final double rangeSquared;
    private final double force;
    private final double upwardForce;
    private final double coneDotThreshold;
    private final int affectedLimit;
    private final int targetFxLimit;
    private final double xpPerTargetHit;
    private final AtomicInteger remaining;
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger targetFx = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private ShockwaveBatch(Player player, Location origin, Vector look, double range, double force,
                           double upwardForce, double coneDotThreshold, int affectedLimit, int targetFxLimit,
                           double xpPerTargetHit, int candidates) {
      this.player = player;
      playerId = player.getUniqueId();
      this.origin = origin;
      this.look = look;
      rangeSquared = range * range;
      this.force = force;
      this.upwardForce = upwardForce;
      this.coneDotThreshold = coneDotThreshold;
      this.affectedLimit = affectedLimit;
      this.targetFxLimit = targetFxLimit;
      this.xpPerTargetHit = xpPerTargetHit;
      remaining = new AtomicInteger(candidates);
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
      J.runEntity(player, this::finish);
    }

    private synchronized boolean markFinalized() {
      return finalized.compareAndSet(false, true);
    }

    private boolean isFinalized() {
      return finalized.get();
    }

    private void finishTimedOut() {
      if (markFinalized()) {
        finish();
      }
    }

    private void finish() {
      if (!player.isOnline()) {
        return;
      }
      int hit = affected.get();
      if (hit > 0) {
        xp(player, xpPerTargetHit * hit);
        addStat(player, "unarmed.shockwave-clap.mobs-clapped", hit);
      }
    }
  }
}
