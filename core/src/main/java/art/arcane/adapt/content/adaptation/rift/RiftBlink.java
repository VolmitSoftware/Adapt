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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.input.DoubleJumpGesture;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RiftBlink extends SimpleAdaptation<RiftBlink.Config> implements RiftManualBlink {
  public static final PlayerPreference<Phasing> PHASING = new PlayerPreference<>(Phasing.class,
      new PlayerPreference.Definition<>("phasing", RiftMessages.BLINK_SETTING_PHASING, Phasing.SNEAK, List.of(
          new PlayerPreference.Choice<>(Phasing.SNEAK, RiftMessages.BLINK_OPTION_SNEAK, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Phasing.AIM, RiftMessages.BLINK_OPTION_AIM, Id.minecraft("ender_eye"), 1),
          new PlayerPreference.Choice<>(Phasing.NEVER, RiftMessages.BLINK_OPTION_NEVER, Id.minecraft("bricks"), 1),
          new PlayerPreference.Choice<>(Phasing.AUTO, RiftMessages.RIFTBLINK_PREFERENCE_AUTO, Id.minecraft("spyglass"), 1))));
  public static final PlayerPreference<Targeting> TARGETING = new PlayerPreference<>(Targeting.class,
      new PlayerPreference.Definition<>("targeting", RiftMessages.BLINK_SETTING_TARGETING, Targeting.DISTANCE, List.of(
          new PlayerPreference.Choice<>(Targeting.DISTANCE, RiftMessages.BLINK_OPTION_DISTANCE, Id.minecraft("arrow"), 1),
          new PlayerPreference.Choice<>(Targeting.VERTICALITY, RiftMessages.BLINK_OPTION_VERTICALITY, Id.minecraft("ladder"), 1))));
  public static final PlayerPreference<Activation> ACTIVATION = new PlayerPreference<>(Activation.class,
      new PlayerPreference.Definition<>("activation", RiftMessages.BLINK_SETTING_ACTIVATION, Activation.MANUAL, List.of(
          new PlayerPreference.Choice<>(Activation.MANUAL, RiftMessages.BLINK_OPTION_MANUAL, Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Activation.REACTIVE, RiftMessages.BLINK_OPTION_REACTIVE, Id.minecraft("shield"), 2))));
  public static final PlayerPreference<Direction> DIRECTION = new PlayerPreference<>(Direction.class,
      new PlayerPreference.Definition<>("direction", RiftMessages.BLINK_SETTING_DIRECTION, Direction.LOOK, List.of(
          new PlayerPreference.Choice<>(Direction.LOOK, RiftMessages.BLINK_OPTION_LOOK, Id.minecraft("compass"), 1),
          new PlayerPreference.Choice<>(Direction.MOMENTUM, RiftMessages.BLINK_OPTION_MOMENTUM, Id.minecraft("wind_charge"), Id.minecraft("feather"), 1))));
  public static final PlayerPreference<ReactiveDirection> REACTIVE_DIRECTION = new PlayerPreference<>(ReactiveDirection.class,
      new PlayerPreference.Definition<>("reactive-direction", RiftMessages.BLINK_SETTING_REACTIVE_DIRECTION, ReactiveDirection.LOOK, List.of(
          new PlayerPreference.Choice<>(ReactiveDirection.LOOK, RiftMessages.BLINK_OPTION_LOOK, Id.minecraft("spyglass"), 2),
          new PlayerPreference.Choice<>(ReactiveDirection.AWAY_FROM_ATTACKER, RiftMessages.BLINK_OPTION_AWAY, Id.minecraft("ender_pearl"), 2),
          new PlayerPreference.Choice<>(ReactiveDirection.MOMENTUM, RiftMessages.BLINK_OPTION_MOMENTUM, Id.minecraft("wind_charge"), Id.minecraft("feather"), 2))));
  public static final PlayerPreference<Landing> LANDING = new PlayerPreference<>(Landing.class,
      new PlayerPreference.Definition<>("landing", RiftMessages.RIFTBLINK_PREFERENCE_LANDING, Landing.SERVER, List.of(
          new PlayerPreference.Choice<>(Landing.SERVER, RiftMessages.RIFTBLINK_PREFERENCE_LANDING_SERVER, Id.minecraft("grass_block"), 1),
          new PlayerPreference.Choice<>(Landing.NEAR, RiftMessages.RIFTBLINK_PREFERENCE_LANDING_NEAR, Id.minecraft("stone_slab"), 1),
          new PlayerPreference.Choice<>(Landing.NONE, RiftMessages.RIFTBLINK_PREFERENCE_LANDING_NONE, Id.minecraft("feather"), 1))));
  public static final PlayerPreference<Momentum> MOMENTUM = new PlayerPreference<>(Momentum.class,
      new PlayerPreference.Definition<>("momentum", RiftMessages.RIFTBLINK_PREFERENCE_MOMENTUM, Momentum.FULL, List.of(
          new PlayerPreference.Choice<>(Momentum.FULL, RiftMessages.RIFTBLINK_PREFERENCE_MOMENTUM_FULL, Id.minecraft("arrow"), 1),
          new PlayerPreference.Choice<>(Momentum.HALF, RiftMessages.RIFTBLINK_PREFERENCE_MOMENTUM_HALF, Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Momentum.STOP, RiftMessages.RIFTBLINK_PREFERENCE_MOMENTUM_STOP, Id.minecraft("barrier"), 1))));
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, PHASING, TARGETING, ACTIVATION, DIRECTION, REACTIVE_DIRECTION, LANDING, MOMENTUM);

  public static final Id LAVA = Id.minecraft("lava");
  public static final Id FIRE = Id.minecraft("fire");
  public static final Id SOUL_FIRE = Id.minecraft("soul_fire");
  private static final Id ENDER_PEARL_DAMAGE = Id.minecraft("ender_pearl");

  private final Cooldowns lastBlink = cooldowns();
  private final DoubleJumpGesture doubleJump = new DoubleJumpGesture();
  private final Map<UUID, UUID> pendingBlinks = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> replayingAttacks = playerState();
  private final Map<UUID, PendingAttack> deferredAttacks = new ConcurrentHashMap<>();
  private final Map<UUID, MotionSample> movement = playerState();

  public RiftBlink() {
    super("rift-blink");
    registerConfiguration(Config.class);
    needs(PlatformNeed.emulatedFeature(EngineFeature.PLAYER_INPUT));
    setIcon(Id.minecraft("feather"));
    setInterval(9288);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_blink_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_rift_blink_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_blink_500", "rift.blink.blinks", 500, 400);
    registerMilestone("challenge_rift_blink_5k", "rift.blink.distance-blinked", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public boolean usesManualTrigger(PlayerRef player) {
    return isBlinkEligible(player) && preference(player, ACTIVATION) == Activation.MANUAL;
  }

  /** Whether a registered rift blink owns the player's double jump for its manual trigger. */
  public static boolean ownsManualTrigger(PlayerRef player) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    if (server == null || server.getSkillRegistry() == null) {
      return false;
    }
    Skill<?> rift = server.getSkillRegistry().getSkill("rift");
    if (rift == null) {
      return false;
    }
    return claimsManualTrigger(rift.getAdaptations(), player) || claimsManualTrigger(rift.getUpgrades(), player);
  }

  private static boolean claimsManualTrigger(List<Adaptation<?>> candidates, PlayerRef player) {
    for (Adaptation<?> candidate : candidates) {
      if (candidate instanceof RiftManualBlink blink && blink.usesManualTrigger(player)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isPlayerPreferenceVisible(AdaptPlayer player, PlayerPreference<?> preference) {
    if (preference == DIRECTION) {
      return PlayerPreferences.resolve(this, player.getData(), getLevel(player), ACTIVATION) == Activation.MANUAL;
    }
    return preference != REACTIVE_DIRECTION || (getLevel(player) >= 2
        && PlayerPreferences.resolve(this, player.getData(), getLevel(player), ACTIVATION) == Activation.REACTIVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getBlinkDistance(level), 1), 1);
    statLore(v, Form.f(getPearlDamage(level) / 2D, 1), 2);
    if (getConfig().allowPhasing) {
      v.addLore(C.LIGHT_PURPLE + "+ " + AdaptLanguage.text(RiftMessages.BLINK_LORE3));
    }
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (!isBlinkEligible(p)) {
      movement.remove(p.uuid());
      doubleJump.reset(p.uuid());
      return;
    }
    Activation activation = preference(p, ACTIVATION);
    boolean useMovement = activation == Activation.MANUAL ? preference(p, DIRECTION) == Direction.MOMENTUM
        : preference(p, REACTIVE_DIRECTION) == ReactiveDirection.MOMENTUM;
    if (useMovement && e.changedPosition()) {
      Loc from = e.from();
      Loc to = e.to();
      movement.put(p.uuid(), new MotionSample(to.x() - from.x(), to.y() - from.y(),
          to.z() - from.z(), p.ticksLived()));
    } else if (!useMovement) {
      movement.remove(p.uuid());
    }
    if (activation != Activation.MANUAL) {
      doubleJump.reset(p.uuid());
      return;
    }

    if (!doubleJump.update(p) || isOnCooldown(p.uuid())) {
      return;
    }

    attemptBlink(p, useMovement ? movementDirection(p) : p.eyeLocation().direction(), null);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent event) {
    if (!(event.entity() instanceof PlayerRef player)
        || !isReactiveAttack(event.cause(), event.finalDamage())
        || replayingAttacks.containsKey(player.uuid())
        || !isBlinkEligible(player)
        || getLevel(player) < 2
        || preference(player, ACTIVATION) != Activation.REACTIVE
        || isOnCooldown(player.uuid())) {
      return;
    }
    Vec3 direction = switch (preference(player, REACTIVE_DIRECTION)) {
      case LOOK -> player.eyeLocation().direction();
      case MOMENTUM -> movementDirection(player);
      case AWAY_FROM_ATTACKER -> awayDirection(player.location(), player.eyeLocation().direction(),
          attackLocation(event));
    };
    attemptBlink(player, direction, event);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent event) {
    movement.remove(event.player().uuid());
    doubleJump.restart(event.player().uuid());
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent event) {
    PlayerRef player = event.player();
    movement.remove(player.uuid());
    pendingBlinks.remove(player.uuid());
    PendingAttack attack = deferredAttacks.remove(player.uuid());
    if (attack != null) {
      replayAttack(player, attack);
    }
  }

  private boolean isBlinkEligible(PlayerRef player) {
    if (player.gameMode() != GameMode.SURVIVAL) {
      return false;
    }
    AdaptPlayer owner = getPlayer(player);
    return owner != null && owner.isRuntimeReady() && player.equals(owner.getPlayer())
        && PlayerPreferences.resolve(this, owner.getData(), getLevel(player), CommonPreferences.ENABLED) == CommonPreferences.Toggle.ON
        && hasActiveAdaptation(player);
  }

  private Loc attackLocation(EntityDamageByEntityEvent event) {
    Loc location = event.damageLocation();
    if (location != null) {
      return location;
    }
    EntityRef attacker = event.causingEntity();
    if (attacker != null && (!Tasks.isFoliaThreading() || Tasks.isOwnedByCurrentRegion(attacker))) {
      return attacker.location();
    }
    EntityRef direct = event.damager();
    return direct != null && (!Tasks.isFoliaThreading() || Tasks.isOwnedByCurrentRegion(direct)) ? direct.location() : null;
  }

  private boolean isOnCooldown(UUID id) {
    return pendingBlinks.containsKey(id) || !lastBlink.isReady(id, Math.max(0L, getConfig().cooldownMillis));
  }

  private Vec3 movementDirection(PlayerRef player) {
    MotionSample sample = movement.get(player.uuid());
    Vec3 look = player.eyeLocation().direction();
    if (sample == null || player.ticksLived() - sample.tick() > 3 || player.ticksLived() < sample.tick()) {
      return look;
    }
    Vec3 direction = new Vec3(sample.x(), sample.y(), sample.z());
    double lengthSquared = direction.lengthSquared();
    return Double.isFinite(lengthSquared) && lengthSquared > 0.000001D ? direction.normalize() : look;
  }

  private double getBlinkDistance(int level) {
    double distance = getConfig().baseDistance + (getLevelPercent(level) * getConfig().distanceFactor);
    return Double.isFinite(distance) ? Math.max(0D, Math.min(128D, distance)) : 0D;
  }

  private double getPearlDamage(int level) {
    return calculatePearlDamage(
        level,
        getConfig().pearlDamageBase,
        getConfig().pearlDamageReductionPerLevel,
        getConfig().minimumPearlDamage
    );
  }

  private void attemptBlink(PlayerRef p, Vec3 direction, EntityDamageByEntityEvent incomingAttack) {
    UUID id = p.uuid();
    if (isOnCooldown(id)) {
      return;
    }

    UUID ticket = UUID.randomUUID();
    if (pendingBlinks.putIfAbsent(id, ticket) != null) {
      return;
    }

    boolean started = false;
    try {
      started = startBlink(p, direction, incomingAttack, ticket);
    } catch (RuntimeException failure) {
      AdaptLog.error(failure);
      AdaptLog.error("Rift Blink failed to prepare a destination for " + id + ".");
    } finally {
      if (!started) {
        pendingBlinks.remove(id, ticket);
      }
    }
  }

  private boolean startBlink(PlayerRef p, Vec3 direction, EntityDamageByEntityEvent incomingAttack, UUID ticket) {
    UUID id = p.uuid();
    Loc origin = p.location();
    Loc destination = RiftBlinkPath.find(p, direction, new RiftBlinkPath.Shape(getBlinkDistance(getLevel(p)),
        getConfig().minBlinkDistance,
        (double verticalAim) -> phaseForDirection(getConfig().allowPhasing, preference(p, PHASING), p.isSneaking(), verticalAim),
        preference(p, TARGETING), preference(p, LANDING), getConfig().groundSnapDepth, getConfig().verticalSearchHeight));
    double minDistance = Math.max(0.5, getConfig().minBlinkDistance);
    if (destination == null || origin.distanceSquared(destination) < minDistance * minDistance) {
      fizzleFx(this, p);
      return false;
    }

    destination = destination.withRotation(origin.yaw(), origin.pitch());
    double momentum = Double.isFinite(getConfig().momentumCarry) ? Math.max(0D, getConfig().momentumCarry) : 0D;
    Vec3 carry = direction.normalize().multiply(momentum * momentumMultiplier(preference(p, MOMENTUM)));
    PendingAttack attack = incomingAttack == null ? null : new PendingAttack(ticket, incomingAttack,
        incomingAttack.originalDamage());
    destination = AdaptPorts.get(AdaptationTeleportPort.class, AdaptationTeleportPort.NONE)
        .announce(p, this, origin, destination);
    if (destination == null) {
      return false;
    }
    if (!RiftBlinkPath.validCandidate(p, origin, destination, getBlinkDistance(getLevel(p)), getConfig().minBlinkDistance)
        || !canInteract(p, destination) || !checkRegion(p, destination)) {
      return false;
    }

    BlinkOperation operation = new BlinkOperation(id, ticket, origin, destination, carry, getPearlDamage(getLevel(p)), attack);
    departFx(this, operation.origin(), operation.requestedDestination());

    CompletableFuture<Boolean> teleport;
    try {
      RiftTeleports.blinking(p, operation.requestedDestination());
      teleport = p.teleport(operation.requestedDestination(), TeleportCause.PLUGIN);
    } catch (RuntimeException exception) {
      AdaptLog.error(exception);
      AdaptLog.error("Rift Blink could not start a teleport for " + p.uuid() + ".");
      return false;
    }

    if (teleport == null) {
      AdaptLog.error("Rift Blink got no teleport future for " + id + ".");
      return false;
    }
    if (incomingAttack != null) {
      deferredAttacks.put(id, attack);
      incomingAttack.setCancelled(true);
    }
    teleport.whenComplete((Boolean success, Throwable failure) -> finishBlink(p, operation, success, failure));
    return true;
  }

  private void finishBlink(PlayerRef p, BlinkOperation operation, Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptLog.error(failure);
      AdaptLog.error("Rift Blink teleport failed for " + operation.playerId() + ".");
    }

    if (!Tasks.runEntity(p, () -> {
      if (!pendingBlinks.remove(operation.playerId(), operation.ticket())) {
        return;
      }
      if (operation.attack() != null && !deferredAttacks.remove(operation.playerId(), operation.attack())) {
        return;
      }
      if (!p.isOnline()) {
        return;
      }
      if (failure != null || !Boolean.TRUE.equals(success)) {
        replayAttack(p, operation.attack());
        return;
      }

      lastBlink.mark(operation.playerId(), Math.max(0L, getConfig().cooldownMillis));
      awardUsageBaselineXp(p, getLevel(p));
      if (getActiveSiblingLevel(p, "rift-resist") > 0) {
        RiftResist.riftResistStackAdd(this, p, 10, 5);
      }

      arrive(this, p, operation.carry(), operation.pearlDamage());
      Loc actualDestination = p.location();
      addStat(p, "rift.teleports", 1);
      addStat(p, "rift.blink.blinks", 1);
      if (operation.origin().sameWorld(actualDestination)) {
        addStat(p, "rift.blink.distance-blinked", (int) operation.origin().distance(actualDestination));
      }
    })) {
      pendingBlinks.remove(operation.playerId(), operation.ticket());
    }
  }

  private void replayAttack(PlayerRef player, PendingAttack attack) {
    if (attack == null || player.isDead()) {
      return;
    }
    replayingAttacks.put(player.uuid(), Boolean.TRUE);
    int immunity = player.noDamageTicks();
    try {
      player.setNoDamageTicks(0);
      attack.hit().replay(player, attack.damage());
    } finally {
      player.setNoDamageTicks(Math.max(immunity, player.noDamageTicks()));
      replayingAttacks.remove(player.uuid());
    }
  }

  public static void fizzleFx(Adaptation<?> owner, PlayerRef p) {
    Fx.now(owner, p, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.2)
        .sound(Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.5f, 1.4f);
  }

  public static void departFx(Adaptation<?> owner, Loc origin, Loc destination) {
    Fx.now(owner, origin, FxPriority.TRANSITION)
        .line(Particles.REVERSE_PORTAL, destination.x(), destination.y() + 1, destination.z(), 24)
        .particle(Particles.REVERSE_PORTAL, 6, 0, 1.0, 0, 0.25, 0.03)
        .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f, Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.4f, 1.7f);
  }

  /** Clears the fall, carries momentum and deals the pearl damage after a blink lands. */
  public static void arrive(Adaptation<?> owner, PlayerRef p, Vec3 carry, double pearlDamage) {
    land(p, carry, pearlDamage);
    Loc destination = p.location();
    Fx.now(owner, destination, FxPriority.TRANSITION)
        .ring(Particles.END_ROD, 0.8, 10, 0.1)
        .except(p)
        .sound(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.3f);
    Fx.sound(owner, p, destination, Sounds.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.3f);
  }

  /** {@link #arrive} without its particles and sound. */
  public static void land(PlayerRef p, Vec3 carry, double pearlDamage) {
    p.setFallDistance(0);
    p.setVelocity(carry);
    if (Double.isFinite(pearlDamage) && pearlDamage > 0D && !p.isDead()) {
      p.damage(pearlDamage, ENDER_PEARL_DAMAGE);
    }
  }

  static double momentumMultiplier(Momentum momentum) {
    return switch (momentum) {
      case FULL -> 1D;
      case HALF -> 0.5D;
      case STOP -> 0D;
    };
  }

  static int landingSnapDepth(int configuredDepth, Landing landing) {
    int depth = Math.max(0, Math.min(32, configuredDepth));
    return switch (landing) {
      case SERVER -> depth;
      case NEAR -> Math.min(2, depth);
      case NONE -> 0;
    };
  }

  static boolean phaseForDirection(boolean serverAllows, Phasing mode, boolean sneaking, double verticalAim) {
    return mode == Phasing.AUTO ? serverAllows && verticalAim <= 0.2D : canPhase(serverAllows, mode, sneaking);
  }

  static boolean canPhase(boolean serverAllows, Phasing mode, boolean sneaking) {
    return serverAllows && (mode == Phasing.AIM || (mode == Phasing.SNEAK && sneaking));
  }

  static boolean isReactiveAttack(DamageCause cause, double damage) {
    return Double.isFinite(damage) && damage > 0D && switch (cause) {
      case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE -> true;
      default -> false;
    };
  }

  static Vec3 awayDirection(Loc origin, Vec3 look, Loc source) {
    if (source == null || !origin.sameWorld(source)) {
      return new Vec3(look.x(), look.y(), look.z());
    }
    Vec3 away = origin.position().subtract(source.position()).withY(0D);
    return away.lengthSquared() > 0.000001D ? away.normalize() : new Vec3(look.x(), look.y(), look.z());
  }

  static Loc betterCandidate(Loc origin, Loc current, Loc candidate, Targeting targeting) {
    if (current == null) {
      return candidate;
    }
    if (targeting == Targeting.VERTICALITY && candidate.y() != current.y()) {
      return candidate.y() > current.y() ? candidate : current;
    }
    return origin.distanceSquared(candidate) > origin.distanceSquared(current) ? candidate : current;
  }

  static double blinkReach(boolean phase, double maxDistance, Double hitDistance) {
    if (phase || hitDistance == null) {
      return maxDistance;
    }
    return Math.max(0, hitDistance - 0.5);
  }

  static double calculatePearlDamage(int level, double baseDamage, double reductionPerLevel, double minimumDamage) {
    double safeBase = Double.isFinite(baseDamage) ? Math.max(0D, baseDamage) : 5D;
    double safeReduction = Double.isFinite(reductionPerLevel) ? Math.max(0D, reductionPerLevel) : 0D;
    double safeMinimum = Double.isFinite(minimumDamage) ? Math.max(0D, Math.min(safeBase, minimumDamage)) : 0D;
    double scaled = safeBase - (Math.max(0, level - 1) * safeReduction);
    return Math.max(safeMinimum, scaled);
  }

  @ConfigDescription("Blink with player-selectable activation, targeting and phasing. Reactive activation unlocks at level 2 and replaces double-jump with an attack dodge. Successful blinks deal level-scaled ender pearl damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between successful Rift Blink triggers in milliseconds.", impact = "Higher values reduce blink frequency; lower values allow faster reuse.")
    int cooldownMillis = 2000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla ender pearl damage applied after a successful level-1 blink.", impact = "Higher values make low-level blinks more dangerous.")
    double pearlDamageBase = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blink self-damage removed for each level beyond the first.", impact = "Higher values make leveling reduce the health cost faster.")
    double pearlDamageReductionPerLevel = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum ender pearl damage a successful blink can inflict.", impact = "Higher values retain more self-damage at high levels.")
    double minimumPearlDamage = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blink distance in blocks at level 0 before level scaling. The combined range is bounded from 0 to 128 blocks.", impact = "Higher values make every blink reach further regardless of level.")
    double baseDistance = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional blink distance in blocks granted at max level, scaling linearly with level.", impact = "Higher values widen the gap between low-level and max-level blink reach.")
    double distanceFactor = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Blocks searched downward from the aimed point to prefer landing on solid ground, bounded from 0 to 32.", impact = "Higher values snap blinks to ground from further above it; lower values allow more mid-air blinks.")
    int groundSnapDepth = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Velocity carried along the selected travel direction after a blink.", impact = "Higher values give a stronger dash feel on arrival; 0 stops the player dead.")
    double momentumCarry = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum distance a blink must cover to trigger.", impact = "Higher values prevent short hops from consuming the blink.")
    double minBlinkDistance = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows phasing through obstacles using the player-selected phasing mode.", impact = "False blocks phasing regardless of player preference.")
    boolean allowPhasing = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum extra blocks searched upward for a landing when verticality is preferred, bounded from 0 to 16.", impact = "Higher values allow higher visible ledges within the normal blink range.")
    int verticalSearchHeight = 6;

    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      initialCost = 6;
    }
  }

  public enum Phasing { SNEAK, AIM, NEVER, AUTO }

  public enum Targeting { DISTANCE, VERTICALITY }

  public enum Activation { MANUAL, REACTIVE }

  public enum Direction { LOOK, MOMENTUM }

  public enum ReactiveDirection { LOOK, AWAY_FROM_ATTACKER, MOMENTUM }

  private record MotionSample(double x, double y, double z, int tick) { }

  private record PendingAttack(UUID ticket, EntityDamageEvent hit, double damage) {
  }

  private record BlinkOperation(UUID playerId, UUID ticket, Loc origin, Loc requestedDestination, Vec3 carry,
                                double pearlDamage, PendingAttack attack) {
  }

  public enum Landing { SERVER, NEAR, NONE }
  public enum Momentum { FULL, HALF, STOP }
}
