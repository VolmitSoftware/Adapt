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
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
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
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

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
    setIcon(Id.minecraft("netherite_axe"));
    setInterval(4333);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_axe"))
        .key("challenge_axe_ground_smash_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("netherite_axe"))
        .key("challenge_axe_ground_smash_5")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_ground_smash_500", "axe.ground-smash.mobs-hit", 500, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.IGNORE_PASSIVE, AxePreferences.RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    double f = getLevelPercent(level);
    statLore(v, C.RED, "+ ", Form.f(getFalloffDamage(f), 1) + " - " + Form.f(getDamage(f), 1), 1);
    statLore(v, C.RED, "+ ", Form.f(getRadius(f), 1), 2);
    statLore(v, C.RED, "+ ", Form.pc(getForce(f), 0), 3);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1), 4);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    if (!e.isSneaking()) {
      armedPlayers.remove(playerId);
      return;
    }

    ItemRef mainHand = p.inventory().mainHand();
    int level = getActiveLevel(p);
    long cooldownMs = getCooldownTime(getLevelPercent(level)) * 50L;
    if (shouldArm(e.isSneaking(), p.isOnGround(), isAxe(mainHand), level,
        cooldowns.isReady(playerId, cooldownMs))) {
      armedPlayers.put(playerId, Boolean.TRUE);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    if (e.to() == null) {
      return;
    }

    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    if (!p.isSneaking()) {
      armedPlayers.remove(playerId);
      return;
    }
    if (!p.isOnGround() || armedPlayers.remove(playerId) == null) {
      return;
    }

    ItemRef mainHand = p.inventory().mainHand();
    int level = getActiveLevel(p);
    if (!preference(p, AxePreferences.RESERVE).permits(p.foodLevel()) || !shouldActivate(p.isOnGround(), p.isSneaking(), isAxe(mainHand), level)) {
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

  private void smash(PlayerRef p, int level) {
    double f = getLevelPercent(level);
    double radius = getRadius(f);
    Loc center = p.location();
    List<LivingRef> candidates = collectCandidates(p, center, radius);
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
    for (LivingRef target : candidates) {
      if (!Tasks.runEntity(target, () -> inspectCandidateOwned(batch, target))) {
        batch.complete();
      }
    }
    Tasks.runEntity(p, batch::finishTimedOut, BATCH_TIMEOUT_TICKS);
  }

  private List<LivingRef> collectCandidates(PlayerRef player, Loc center, double radius) {
    List<LivingRef> candidates = new ArrayList<>(HARD_MAX_CANDIDATES);
    WorldRef world = center.world();
    if (world == null || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(center, radius, radius))) {
      return candidates;
    }
    for (EntityRef nearby : world.entitiesIn(Box.around(center.position(), radius, radius, radius), entity -> entity instanceof LivingRef)) {
      if (!(nearby instanceof LivingRef candidate)) {
        continue;
      }
      if (candidate.uuid().equals(player.uuid()) || !AdaptationDamageTargets.allows(candidate, getConfig().ignorePassiveMobs || preferenceEnabled(player, AxePreferences.IGNORE_PASSIVE))) {
        continue;
      }
      candidates.add(candidate);
      if (candidates.size() >= HARD_MAX_CANDIDATES) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(GroundSmashBatch batch, LivingRef target) {
    if (batch.isFinalized()) {
      return;
    }

    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    boolean playerTarget = target instanceof PlayerRef;
    if (!Tasks.runEntity(batch.player,
        () -> authorizeCandidate(batch, target, targetLocation, playerTarget))) {
      batch.complete();
    }
  }

  private void authorizeCandidate(GroundSmashBatch batch, LivingRef target,
      Loc targetLocation, boolean playerTarget) {
    if (batch.isFinalized() || !batch.player.isOnline()) {
      batch.complete();
      return;
    }

    boolean allowed = playerTarget
        ? canPVP(batch.player, targetLocation)
        : canPVE(batch.player, targetLocation);
    if (!allowed || !Tasks.runEntity(target, () -> applyImpactOwned(batch, target))) {
      batch.complete();
    }
  }

  private void applyImpactOwned(GroundSmashBatch batch, LivingRef target) {
    if (batch.isFinalized()) {
      return;
    }

    Loc targetLocation = validTargetLocation(batch, target);
    if (targetLocation == null) {
      batch.complete();
      return;
    }

    Vec3 offset = targetLocation.position().subtract(batch.center.position());
    double distance = Math.sqrt(offset.lengthSquared());
    double damage = falloffValue(batch.maximumDamage, batch.minimumDamage, distance, batch.radius);
    double force = falloffValue(batch.maximumForce, 0D, distance, batch.radius);
    try {
      target.damage(damage, batch.player);
      if (force > 0D && offset.lengthSquared() > 1.0E-8D) {
        target.setVelocity(target.velocity().add(offset.normalize().multiply(force)));
      }
      batch.markHit();
    } finally {
      batch.complete();
    }
  }

  private Loc validTargetLocation(GroundSmashBatch batch, LivingRef target) {
    if (!target.isValid() || target.isDead() || isProtectedFriendly(null, target)
        || !AdaptationDamageTargets.allows(target, batch.ignorePassive)) {
      return null;
    }
    UUID owner = target.tameOwner();
    if (owner != null && batch.playerId.equals(owner)) {
      return null;
    }

    Loc location = target.location();
    if (!location.world().uid().equals(batch.center.world().uid())
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

  private void renderSmash(Loc center, double radius) {
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
            fx.chord(Sounds.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.6F, 0.4F, Sounds.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5F, 0.1F, Sounds.ENTITY_TURTLE_EGG_CRACK, 1.0F, 0.4F);
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
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc center;
    private final double radius;
    private final double radiusSquared;
    private final double maximumDamage;
    private final double minimumDamage;
    private final double maximumForce;
    private final boolean ignorePassive;
    private final AtomicInteger remaining;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private GroundSmashBatch(PlayerRef player, Loc center, double radius,
        double maximumDamage, double minimumDamage, double maximumForce, int candidateCount) {
      this.player = player;
      ignorePassive = getConfig().ignorePassiveMobs || preferenceEnabled(player, AxePreferences.IGNORE_PASSIVE);
      playerId = player.uuid();
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
        Tasks.runEntity(player, this::finishOwnerOwned);
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
    @ConfigDoc(value = "Exclude passive and neutral mobs from area damage and its secondary effects.", impact = "When enabled, protected mobs do not consume target limits. Direct attacks and player targeting are unchanged.")
    boolean ignorePassiveMobs = false;
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
