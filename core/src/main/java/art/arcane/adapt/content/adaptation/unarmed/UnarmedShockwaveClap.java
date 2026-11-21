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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

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
  private static final Rgb WAVE_COLOR = new Rgb(255, 255, 255);
  private final Map<UUID, Long> nextClapAt = playerState();
  private final Cooldowns failCue = cooldowns();

  public UnarmedShockwaveClap() {
    super("unarmed-shockwave-clap");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("note_block"));
    setInterval(5230);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_clap_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_clap_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_clap_250", "unarmed.shockwave-clap.mobs-clapped", 250, 400);
    registerMilestone("challenge_unarmed_clap_2500", "unarmed.shockwave-clap.mobs-clapped", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.AIRBORNE, UnarmedPreferences.HUNGER, UnarmedPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRange(level)), 1);
    statLore(v, Form.f(getForce(level)), 2);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getCooldownMillis(level), 1), 3);
    statLore(v, C.RED, "- ", getConfig().hungerCost, 4);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.LEFT_CLICK_AIR && action != InteractAction.LEFT_CLICK_BLOCK) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking() || (preferenceEnabled(p, UnarmedPreferences.AIRBORNE) && p.isOnGround())) {
      return;
    }

    if (isTool(p.inventory().mainHand()) || isTool(p.inventory().offHand())) {
      return;
    }

    UUID id = p.uuid();
    long now = System.currentTimeMillis();
    Long next = nextClapAt.get(id);
    if (next != null && now < next) {
      playBlocked(p);
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (!preference(p, UnarmedPreferences.HUNGER).permits(p.foodLevel(), hungerCost)) {
      playBlocked(p);
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, p.location());
    if (context == null) {
      return;
    }

    int level = context.level();
    long cooldownMillis = getCooldownMillis(level);
    nextClapAt.put(id, now + cooldownMillis);
    AbilityGlyphs.cooldown(id, this, now, cooldownMillis);
    p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));

    double range = getRange(level);
    double force = getForce(level);
    Loc origin = p.location();
    Vec3 look = origin.direction().normalize();
    List<LivingRef> candidates = collectCandidates(p, origin, range);
    playShockwave(origin, look, range);
    addStat(p, "unarmed.shockwave-clap.claps", 1);

    if (candidates.isEmpty()) {
      return;
    }

    ShockwaveBatch batch = new ShockwaveBatch(p, origin, look, range, force,
        getUpwardForce(level), getConfig().coneDotThreshold, getAffectedLimit(), getTargetFxLimit(),
        getConfig().xpPerTargetHit, candidates.size());
    for (LivingRef target : candidates) {
      if (!Tasks.runEntity(target, () -> inspectCandidateOwned(batch, target))) {
        batch.complete();
      }
    }
    Tasks.runEntity(p, batch::finishTimedOut, 3);
  }

  private List<LivingRef> collectCandidates(PlayerRef player, Loc origin, double range) {
    int limit = getCandidateLimit();
    List<LivingRef> candidates = new ArrayList<>(limit);
    for (EntityRef entity : origin.world().nearbyEntities(origin.position(), range, range, range)) {
      if (!(entity instanceof LivingRef candidate) || candidate.uuid().equals(player.uuid())
          || !preference(player, UnarmedPreferences.TARGETS).accepts(candidate)) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(ShockwaveBatch batch, LivingRef target) {
    if (batch.isFinalized() || !batch.hasAffectedCapacity()) {
      batch.complete();
      return;
    }
    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    boolean playerTarget = target instanceof PlayerRef;
    if (!Tasks.runEntity(batch.player, () -> authorizeCandidate(batch, target, targetLocation, playerTarget))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(ShockwaveBatch batch, LivingRef target, Loc targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget ? canPVP(batch.player, targetLocation) : canPVE(batch.player, targetLocation);
    if (!allowed || !Tasks.runEntity(target, () -> applyImpactOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyImpactOwned(ShockwaveBatch batch, LivingRef target) {
    if (batch.isFinalized()) {
      return;
    }
    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null || !batch.claimAffected()) {
      batch.complete();
      return;
    }

    Vec3 to = targetLocation.position().subtract(batch.origin.position());
    double lengthSquared = to.lengthSquared();
    to = to.multiply(1D / Math.sqrt(lengthSquared));
    target.setVelocity(target.velocity().multiply(0.2D).add(to.multiply(batch.force).withY(batch.upwardForce)));
    if (batch.claimTargetFx()) {
      fx(targetLocation.add(0, 1, 0), FxPriority.COMBAT)
          .particle(Particles.CLOUD, 3, 0, 0, 0, 0.15D, 0.03D);
    }
    batch.complete();
  }

  private Loc validTargetLocation(ShockwaveBatch batch, LivingRef target) {
    if (!target.isValid() || target.isDead() || isProtectedFriendly(null, target)) {
      return null;
    }
    UUID owner = target.tameOwner();
    if (owner != null && batch.playerId.equals(owner)) {
      return null;
    }

    Loc location = target.location();
    if (!location.sameWorld(batch.origin)) {
      return null;
    }
    Vec3 to = location.position().subtract(batch.origin.position());
    double lengthSquared = to.lengthSquared();
    if (lengthSquared <= 0.0001D || lengthSquared > batch.rangeSquared) {
      return null;
    }
    to = to.multiply(1D / Math.sqrt(lengthSquared));
    return batch.look.dot(to) >= batch.coneDotThreshold ? location : null;
  }

  private void playShockwave(Loc origin, Vec3 look, double range) {
    fx(origin, FxPriority.COMBAT)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F, Sounds.BLOCK_BELL_RESONATE, 0.7F, 1.4F, Sounds.ENTITY_GENERIC_EXPLODE, 0.4F, 0.7F);
    Loc center = origin.add(look.multiply(0.6)).add(0, 1.0D, 0);
    fx(center, FxPriority.COMBAT)
        .particle(Particles.EXPLOSION, 1, 0, 0, 0, 0.1D, 0.02D)
        .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0);
    int ringPoints = Math.min(28, 12 + (int) (range * 2));
    timeline(center)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(Math.min(48.0D, (range * 2.0D) + 8.0D))
        .frame((fx, tick, progress) -> fx.dustRing(WAVE_COLOR, 0.4D + (range * progress), ringPoints, 1.0F))
        .start();
  }

  private void playBlocked(PlayerRef p) {
    if (!failCue.isReady(p.uuid(), 700L)) {
      return;
    }

    failCue.mark(p.uuid());
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.25F, 0.5F);
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
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc origin;
    private final Vec3 look;
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

    private ShockwaveBatch(PlayerRef player, Loc origin, Vec3 look, double range, double force,
                           double upwardForce, double coneDotThreshold, int affectedLimit, int targetFxLimit,
                           double xpPerTargetHit, int candidates) {
      this.player = player;
      playerId = player.uuid();
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
      Tasks.runEntity(player, this::finish);
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
