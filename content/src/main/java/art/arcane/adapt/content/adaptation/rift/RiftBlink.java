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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.ability.DoubleJump;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RiftBlink extends SimpleAdaptation<RiftBlink.Config> implements RiftManualBlink {
  public static final Id ID = Id.adapt("rift/blink");
  public static final PlayerPreference<Phasing> PHASING = PlayerPreference.of("phasing", Phasing.class, Phasing.SNEAK, List.of(
          new PlayerPreference.Choice<>(Phasing.SNEAK, Text.key("adapt.rift.blink.option.sneak"), Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Phasing.AIM, Text.key("adapt.rift.blink.option.aim"), Id.minecraft("ender_eye"), 1),
          new PlayerPreference.Choice<>(Phasing.NEVER, Text.key("adapt.rift.blink.option.never"), Id.minecraft("bricks"), 1),
          new PlayerPreference.Choice<>(Phasing.AUTO, Text.key("adapt.riftblink.preferences.preference_auto"), Id.minecraft("spyglass"), 1))).labelled(Text.key("adapt.rift.blink.setting.phasing"));
  public static final PlayerPreference<Targeting> TARGETING = PlayerPreference.of("targeting", Targeting.class, Targeting.DISTANCE, List.of(
          new PlayerPreference.Choice<>(Targeting.DISTANCE, Text.key("adapt.rift.blink.option.distance"), Id.minecraft("arrow"), 1),
          new PlayerPreference.Choice<>(Targeting.VERTICALITY, Text.key("adapt.rift.blink.option.verticality"), Id.minecraft("ladder"), 1))).labelled(Text.key("adapt.rift.blink.setting.targeting"));
  public static final PlayerPreference<Activation> ACTIVATION = PlayerPreference.of("activation", Activation.class, Activation.MANUAL, List.of(
          new PlayerPreference.Choice<>(Activation.MANUAL, Text.key("adapt.rift.blink.option.manual"), Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Activation.REACTIVE, Text.key("adapt.rift.blink.option.reactive"), Id.minecraft("shield"), 2))).labelled(Text.key("adapt.rift.blink.setting.activation"));
  public static final PlayerPreference<Direction> DIRECTION = PlayerPreference.of("direction", Direction.class, Direction.LOOK, List.of(
          new PlayerPreference.Choice<>(Direction.LOOK, Text.key("adapt.rift.blink.option.look"), Id.minecraft("compass"), 1),
          new PlayerPreference.Choice<>(Direction.MOMENTUM, Optional.of(Text.key("adapt.rift.blink.option.momentum")), Id.minecraft("wind_charge"), Id.minecraft("feather"), 1))).labelled(Text.key("adapt.rift.blink.setting.direction"));
  public static final PlayerPreference<ReactiveDirection> REACTIVE_DIRECTION = PlayerPreference.of("reactive-direction", ReactiveDirection.class, ReactiveDirection.LOOK, List.of(
          new PlayerPreference.Choice<>(ReactiveDirection.LOOK, Text.key("adapt.rift.blink.option.look"), Id.minecraft("spyglass"), 2),
          new PlayerPreference.Choice<>(ReactiveDirection.AWAY_FROM_ATTACKER, Text.key("adapt.rift.blink.option.away"), Id.minecraft("ender_pearl"), 2),
          new PlayerPreference.Choice<>(ReactiveDirection.MOMENTUM, Optional.of(Text.key("adapt.rift.blink.option.momentum")), Id.minecraft("wind_charge"), Id.minecraft("feather"), 2))).labelled(Text.key("adapt.rift.blink.setting.reactive_direction"));
  public static final PlayerPreference<Landing> LANDING = PlayerPreference.of("landing", Landing.class, Landing.SERVER, List.of(
          new PlayerPreference.Choice<>(Landing.SERVER, Text.key("adapt.riftblink.preferences.preference_landing_server"), Id.minecraft("grass_block"), 1),
          new PlayerPreference.Choice<>(Landing.NEAR, Text.key("adapt.riftblink.preferences.preference_landing_near"), Id.minecraft("stone_slab"), 1),
          new PlayerPreference.Choice<>(Landing.NONE, Text.key("adapt.riftblink.preferences.preference_landing_none"), Id.minecraft("feather"), 1))).labelled(Text.key("adapt.riftblink.preferences.preference_landing"));
  public static final PlayerPreference<Momentum> MOMENTUM = PlayerPreference.of("momentum", Momentum.class, Momentum.FULL, List.of(
          new PlayerPreference.Choice<>(Momentum.FULL, Text.key("adapt.riftblink.preferences.preference_momentum_full"), Id.minecraft("arrow"), 1),
          new PlayerPreference.Choice<>(Momentum.HALF, Text.key("adapt.riftblink.preferences.preference_momentum_half"), Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Momentum.STOP, Text.key("adapt.riftblink.preferences.preference_momentum_stop"), Id.minecraft("barrier"), 1))).labelled(Text.key("adapt.riftblink.preferences.preference_momentum"));
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, PHASING, TARGETING, ACTIVATION, DIRECTION, REACTIVE_DIRECTION, LANDING, MOMENTUM);

  public static final Id LAVA = Id.minecraft("lava");
  public static final Id FIRE = Id.minecraft("fire");
  public static final Id SOUL_FIRE = Id.minecraft("soul_fire");
  private static final Id ENDER_PEARL_DAMAGE = Id.minecraft("ender_pearl");

  private final Cooldowns lastBlink = cooldowns();
  private final DoubleJump doubleJump = DoubleJump.create();
  private final Map<UUID, UUID> pendingBlinks = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> replayingAttacks = playerState();
  private final Map<UUID, PendingAttack> deferredAttacks = new ConcurrentHashMap<>();
  private final Map<UUID, MotionSample> movement = playerState();

  public RiftBlink() {
    super(ID);
    registerConfiguration(Config.class);
    needs(PlatformNeed.emulatedFeature(EngineFeature.PLAYER_INPUT));
    setIcon(Id.minecraft("feather"));
    setInterval(9288);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_blink_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_rift_blink_5k")
            .frame(AdvancementFrame.CHALLENGE)
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
    Skill<?> rift = AdaptApi.isRunning() ? AdaptApi.content().skill(Id.adapt("rift")).orElse(null) : null;
    if (rift == null || !rift.isEnabled()) {
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
  public boolean isPlayerPreferenceVisible(PlayerProfile player, PlayerPreference<?> preference) {
    if (preference == DIRECTION) {
      return preference(player.player(), ACTIVATION) == Activation.MANUAL;
    }
    return preference != REACTIVE_DIRECTION || (getLevel(player) >= 2
        && preference(player.player(), ACTIVATION) == Activation.REACTIVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getBlinkDistance(level), 1), 1);
    statLore(v, Format.f(getPearlDamage(level) / 2D, 1), 2);
    if (getConfig().allowPhasing) {
      v.addLore(TextFormat.LIGHT_PURPLE + "+ " + Text.key("adapt.rift.blink.lore3").resolve());
    }
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
    PlayerProfile owner = getPlayer(player);
    return owner != null && owner.isReady() && player.equals(owner.player())
        && preference(player, CommonPreferences.ENABLED) == CommonPreferences.Toggle.ON
        && hasActiveAdaptation(player);
  }

  private Loc attackLocation(EntityDamageByEntityEvent event) {
    Loc location = event.damageLocation();
    if (location != null) {
      return location;
    }
    EntityRef attacker = event.causingEntity();
    if (attacker != null && (!AdaptApi.scheduler().isRegionized() || AdaptApi.scheduler().owns(attacker))) {
      return attacker.location();
    }
    EntityRef direct = event.damager();
    return direct != null && (!AdaptApi.scheduler().isRegionized() || AdaptApi.scheduler().owns(direct)) ? direct.location() : null;
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
      AdaptApi.log().error("RiftBlink failed", failure);
      AdaptApi.log().error("Rift Blink failed to prepare a destination for " + id + ".", null);
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
    destination = Integrations.announceTeleport(p, this, origin, destination).orElse(null);
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
      AdaptApi.log().error("RiftBlink failed", exception);
      AdaptApi.log().error("Rift Blink could not start a teleport for " + p.uuid() + ".", null);
      return false;
    }

    if (teleport == null) {
      AdaptApi.log().error("Rift Blink got no teleport future for " + id + ".", null);
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
      AdaptApi.log().error("RiftBlink failed", failure);
      AdaptApi.log().error("Rift Blink teleport failed for " + operation.playerId() + ".", null);
    }

    if (!AdaptApi.scheduler().runEntity(p, () -> {
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
      if (getActiveSiblingLevel(p, Id.adapt("rift/resist")) > 0) {
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
    @ConfigDoc(value = "Cooldown between successful Rift Blink triggers in milliseconds.", impact = "Higher values reduce blink frequency; lower values allow faster reuse.")
    int cooldownMillis = 2000;
    @ConfigDoc(value = "Vanilla ender pearl damage applied after a successful level-1 blink.", impact = "Higher values make low-level blinks more dangerous.")
    double pearlDamageBase = 5.0;
    @ConfigDoc(value = "Blink self-damage removed for each level beyond the first.", impact = "Higher values make leveling reduce the health cost faster.")
    double pearlDamageReductionPerLevel = 1.0;
    @ConfigDoc(value = "Minimum ender pearl damage a successful blink can inflict.", impact = "Higher values retain more self-damage at high levels.")
    double minimumPearlDamage = 1.0;
    @ConfigDoc(value = "Blink distance in blocks at level 0 before level scaling. The combined range is bounded from 0 to 128 blocks.", impact = "Higher values make every blink reach further regardless of level.")
    double baseDistance = 12;
    @ConfigDoc(value = "Additional blink distance in blocks granted at max level, scaling linearly with level.", impact = "Higher values widen the gap between low-level and max-level blink reach.")
    double distanceFactor = 20;
    @ConfigDoc(value = "Blocks searched downward from the aimed point to prefer landing on solid ground, bounded from 0 to 32.", impact = "Higher values snap blinks to ground from further above it; lower values allow more mid-air blinks.")
    int groundSnapDepth = 5;
    @ConfigDoc(value = "Velocity carried along the selected travel direction after a blink.", impact = "Higher values give a stronger dash feel on arrival; 0 stops the player dead.")
    double momentumCarry = 0.35;
    @ConfigDoc(value = "Minimum distance a blink must cover to trigger.", impact = "Higher values prevent short hops from consuming the blink.")
    double minBlinkDistance = 1.5;
    @ConfigDoc(value = "Allows phasing through obstacles using the player-selected phasing mode.", impact = "False blocks phasing regardless of player preference.")
    boolean allowPhasing = true;
    @ConfigDoc(value = "Maximum extra blocks searched upward for a landing when verticality is preferred, bounded from 0 to 16.", impact = "Higher values allow higher visible ledges within the normal blink range.")
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
