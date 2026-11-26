package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.sound.LingeringSound;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.content.adaptation.rift.RiftBlink;
import art.arcane.adapt.content.adaptation.rift.RiftBlinkPath;
import art.arcane.adapt.content.adaptation.rift.RiftEnderTaglock;
import art.arcane.adapt.content.adaptation.rift.RiftManualBlink;
import art.arcane.adapt.content.adaptation.rift.RiftPearlBounce;
import art.arcane.adapt.content.adaptation.rift.RiftPearlRebound;
import art.arcane.adapt.content.adaptation.rift.RiftPearls;
import art.arcane.adapt.content.adaptation.rift.RiftResist;
import art.arcane.adapt.content.adaptation.rift.RiftTeleports;
import art.arcane.adapt.content.adaptation.rift.RiftVoidSkin;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
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
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Rift Blink, Pearl Rebound and Rift Resistance fused: a double jump blinks farther and always through walls, landing
 * with resistance, and thrown pearls bounce once off the first block they hit. Each blink costs a plain ender pearl, or
 * half a heart without one, and pays no skill XP. From level 2 a sneak hit tags a creature and the next blink swaps
 * places with it; from level 3 players too, where PvP is allowed.
 */
public class PhaseStep extends SkillUpgrade<PhaseStep.Config> implements RiftManualBlink {
  private static final Set<Predicate<PlayerRef>> QUIET = ConcurrentHashMap.newKeySet();
  private final Cooldowns blinkCooldowns = cooldowns();
  private final DoubleJumpGesture doubleJump = new DoubleJumpGesture();
  private final Map<UUID, Loc> pendingBlinks = new ConcurrentHashMap<>();
  private final Map<UUID, Tag> tags = playerState();
  private final RiftPearlBounce bounce = new RiftPearlBounce(
      (int level) -> Math.max(0D, Math.min(RiftPearlRebound.HARD_MAX_AIM_BIAS, getConfig().pearlAimBias)),
      () -> Math.max(0.4D, getConfig().pearlSpeed), (PlayerRef p) -> {
        addStat(p, "rift.phase-step.rebounds", 1);
        cue(p, MutationCue.WORK);
      });

  public PhaseStep() {
    super("upgraded-rift-phase-step");
    registerConfiguration(Config.class);
    needs(PlatformNeed.emulatedFeature(EngineFeature.PLAYER_INPUT));
    setIcon(Id.minecraft("ender_eye"));
  }

  /** Blinks of a player {@code check} accepts leave no particles or blink sound, until {@link #stopQuiet}. */
  public static void quietWhile(Predicate<PlayerRef> check) {
    QUIET.add(check);
  }

  public static void stopQuiet(Predicate<PlayerRef> check) {
    QUIET.remove(check);
  }

  private static boolean quiet(PlayerRef p) {
    for (Predicate<PlayerRef> check : QUIET) {
      if (check.test(p)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getDistance(level), 0), 1);
    statLore(v, Form.duration(getResistanceTicks(level) * 50D, 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getConfig().cooldownMillis, 1), 3);
    statLore(v, C.YELLOW, "* ", 1, 4);
    if (level >= getConfig().taglockLevel) {
      unlockLore(v, RiftMessages.PHASE_STEP_LORE5);
    }
    if (level >= getConfig().playerSwapLevel) {
      unlockLore(v, RiftMessages.PHASE_STEP_LORE6);
    }
  }

  @Override
  public boolean usesManualTrigger(PlayerRef player) {
    return player.gameMode() == GameMode.SURVIVAL && hasActiveAdaptation(player);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (!usesManualTrigger(p)) {
      doubleJump.reset(p.uuid());
      return;
    }
    if (doubleJump.update(p) && !pendingBlinks.containsKey(p.uuid())
        && blinkCooldowns.isReady(p.uuid(), getConfig().cooldownMillis)) {
      blink(p, getActiveLevel(p));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    doubleJump.restart(e.player().uuid());
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    pendingBlinks.remove(e.player().uuid());
    tags.remove(e.player().uuid());
    doubleJump.reset(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p) || !(e.entity() instanceof LivingRef target) || !p.isSneaking()
        || target.uuid().equals(p.uuid())) {
      return;
    }
    int level = getActiveLevel(p);
    if (level < getConfig().taglockLevel || !swappable(p, target, level)) {
      return;
    }
    tags.put(p.uuid(), new Tag(target, System.currentTimeMillis() + getConfig().tagMillis));
    fx(target.location().add(0D, 1D, 0D), FxPriority.COMBAT)
        .ring(Particles.PORTAL, 0.8, 10, 1.0)
        .sound(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.4f);
    addStat(p, "rift.phase-step.tags", 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef pearl = e.projectile();
    PlayerRef p = bounce.thrower(pearl);
    int level = p == null ? 0 : getActiveLevel(p);
    if (level > 0) {
      bounce.tag(pearl, level);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(ProjectileHitEvent e) {
    bounce.hit(this, e);
  }

  private void blink(PlayerRef p, int level) {
    LivingRef tagged = tagged(p, level);
    if (tagged != null) {
      swap(p, tagged, level);
      return;
    }
    Loc origin = p.location();
    Vec3 direction = p.eyeLocation().direction();
    double distance = getDistance(level);
    Loc destination = RiftBlinkPath.find(p, direction, new RiftBlinkPath.Shape(distance, getConfig().minDistance,
        (double verticalAim) -> true, RiftBlink.Targeting.DISTANCE, RiftBlink.Landing.SERVER,
        getConfig().groundSnapDepth, 0));
    if (destination == null) {
      RiftBlink.fizzleFx(this, p);
      return;
    }

    destination = AdaptPorts.get(AdaptationTeleportPort.class, AdaptationTeleportPort.NONE)
        .announce(p, this, origin, destination.withRotation(origin.yaw(), origin.pitch()));
    if (destination == null
        || !RiftBlinkPath.validCandidate(p, origin, destination, distance, getConfig().minDistance)
        || !canInteract(p, destination) || !checkRegion(p, destination)) {
      return;
    }

    UUID id = p.uuid();
    if (pendingBlinks.putIfAbsent(id, origin) != null) {
      return;
    }
    if (!payBlink(p)) {
      pendingBlinks.remove(id, origin);
      RiftBlink.fizzleFx(this, p);
      return;
    }
    if (!quiet(p)) {
      RiftBlink.departFx(this, origin, destination);
    }
    CompletableFuture<Boolean> teleport;
    try {
      RiftTeleports.phasing(p, destination);
      teleport = p.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException exception) {
      AdaptLog.error(exception);
      teleport = null;
    }
    if (teleport == null) {
      pendingBlinks.remove(id, origin);
      return;
    }
    double momentum = Double.isFinite(getConfig().momentumCarry) ? Math.max(0D, getConfig().momentumCarry) : 0D;
    Vec3 carry = direction.normalize().multiply(momentum);
    teleport.whenComplete((Boolean success, Throwable failure) -> {
      if (failure != null) {
        AdaptLog.error(failure);
      }
      if (!Tasks.runEntity(p, () -> arrive(p, origin, carry, level, Boolean.TRUE.equals(success) && failure == null))) {
        pendingBlinks.remove(id, origin);
      }
    });
  }

  private record Tag(LivingRef target, long expiresAt) {
  }

  private void arrive(PlayerRef p, Loc origin, Vec3 carry, int level, boolean landed) {
    if (!pendingBlinks.remove(p.uuid(), origin) || !landed || !p.isOnline()) {
      return;
    }

    blinkCooldowns.mark(p.uuid(), getConfig().cooldownMillis);
    int resistanceTicks = getResistanceTicks(level);
    RiftResist.riftResistStackAdd(this, p, resistanceTicks,
        RiftVoidSkin.clampResistanceAmplifier(getConfig().resistanceAmplifier));
    if (quiet(p)) {
      RiftBlink.land(p, carry, 0D);
    } else {
      RiftBlink.arrive(this, p, carry, 0D);
    }
    addStat(p, "rift.teleports", 1);
    addStat(p, "rift.phase-step.blinks", 1);
    cue(p, MutationCue.BOON);
    LingeringSound lingering = linger(p);
    Tasks.runEntity(p, lingering::stop, resistanceTicks);
  }

  /** The tagged creature when the tag is fresh, alive, near enough and still allowed; clears a stale tag. */
  private @Nullable LivingRef tagged(PlayerRef p, int level) {
    Tag tag = tags.remove(p.uuid());
    if (tag == null || tag.expiresAt() < System.currentTimeMillis() || level < getConfig().taglockLevel) {
      return null;
    }
    LivingRef target = tag.target();
    if (!target.isValid() || target.isDead()
        || !Tasks.isOwnedByCurrentRegion(target) || !target.location().sameWorld(p.location())
        || target.location().distance(p.location()) > getDistance(level) || !swappable(p, target, level)) {
      return null;
    }
    return target;
  }

  /** Whether the player may swap with {@code target} at both ends: claims, PvP for players, PvE for creatures. */
  private boolean swappable(PlayerRef p, LivingRef target, int level) {
    if (isProtectedFriendly(p, target) || p.uuid().equals(target.tameOwner())
        || !RiftEnderTaglock.taggable(target, level, RiftEnderTaglock.LARGE_WIDTH, RiftEnderTaglock.LARGE_HEIGHT)) {
      return false;
    }
    Loc there = target.location();
    Loc here = p.location();
    boolean allowed = target instanceof PlayerRef
        ? level >= getConfig().playerSwapLevel && canPVP(p, there) && canPVP(p, here)
        : canPVE(p, there) && canPVE(p, here);
    return allowed && canInteract(p, there) && checkRegion(p, there);
  }

  /**
   * Swaps places with {@code target}. A player target must be allowed into the caster's spot and its teleport is
   * announced too; the target moves only once the caster's own teleport landed.
   */
  private void swap(PlayerRef p, LivingRef target, int level) {
    Loc origin = p.location();
    AdaptationTeleportPort port = AdaptPorts.get(AdaptationTeleportPort.class, AdaptationTeleportPort.NONE);
    Loc destination = port.announce(p, this, origin, target.location().withRotation(origin.yaw(), origin.pitch()));
    if (destination == null) {
      return;
    }
    Loc targetDestination = origin;
    if (target instanceof PlayerRef other) {
      targetDestination = checkRegion(other, origin) ? port.announce(other, this, other.location(), origin) : null;
      if (targetDestination == null) {
        return;
      }
    }
    UUID id = p.uuid();
    if (pendingBlinks.putIfAbsent(id, origin) != null) {
      return;
    }
    if (!payBlink(p)) {
      pendingBlinks.remove(id, origin);
      RiftBlink.fizzleFx(this, p);
      return;
    }
    if (!quiet(p)) {
      RiftBlink.departFx(this, origin, destination);
    }
    CompletableFuture<Boolean> teleport;
    try {
      RiftTeleports.phasing(p, destination);
      teleport = p.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException exception) {
      AdaptLog.error(exception);
      teleport = null;
    }
    if (teleport == null) {
      pendingBlinks.remove(id, origin);
      return;
    }
    Loc swapTo = targetDestination;
    teleport.whenComplete((Boolean success, Throwable failure) -> {
      if (failure != null) {
        AdaptLog.error(failure);
      }
      boolean landed = Boolean.TRUE.equals(success) && failure == null;
      if (landed) {
        Tasks.runEntity(target, () -> target.teleport(swapTo, TeleportCause.PLUGIN));
      }
      if (!Tasks.runEntity(p, () -> {
        if (landed) {
          addStat(p, "rift.phase-step.swaps", 1);
        }
        arrive(p, origin, new Vec3(0D, 0D, 0D), level, landed);
      })) {
        pendingBlinks.remove(id, origin);
      }
    });
  }

  /** A plain ender pearl, or half a heart when the player has none and more than half a heart left. */
  private boolean payBlink(PlayerRef p) {
    if (RiftPearls.payPlainPearls(this, p, "blink", 1)) {
      return true;
    }
    double cost = Math.max(0D, getConfig().healthCost);
    return p.health() > cost && payHealthCost(p, "blink", (int) Math.ceil(cost), () -> {
      if (p.health() <= cost) {
        return false;
      }
      p.setHealth(p.health() - cost);
      return true;
    });
  }

  double getDistance(int level) {
    double distance = getConfig().distanceBase + (level * getConfig().distancePerLevel);
    return Double.isFinite(distance) ? Math.max(0D, Math.min(128D, distance)) : 0D;
  }

  int getResistanceTicks(int level) {
    return getConfig().resistanceTicksBase + (level * getConfig().resistanceTicksPerLevel);
  }

  @ConfigDescription("Double jump to blink far through walls with resistance on arrival for a pearl or half a heart; thrown pearls bounce once.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Blink distance before level scaling, in blocks.", impact = "Higher values reach farther.")
    double distanceBase = 30;
    @ConfigDoc(value = "Blink distance added per level, in blocks.", impact = "Higher values reward higher levels more; capped at 128.")
    double distancePerLevel = 6;
    @ConfigDoc(value = "Shortest blink that counts, in blocks.", impact = "Higher values stop short hops from using the blink.")
    double minDistance = 2;
    @ConfigDoc(value = "Blocks searched downward from the aimed point for solid ground.", impact = "Higher values snap more blinks onto the ground.")
    int groundSnapDepth = 5;
    @ConfigDoc(value = "Speed kept along the look direction after a blink.", impact = "Higher values give a stronger dash on arrival.")
    double momentumCarry = 0.35;
    @ConfigDoc(value = "Time between two blinks, in milliseconds.", impact = "Lower values allow blinking more often.")
    long cooldownMillis = 2500;
    @ConfigDoc(value = "Resistance after a blink before level scaling, in ticks.", impact = "Higher values protect you longer on arrival.")
    int resistanceTicksBase = 40;
    @ConfigDoc(value = "Resistance after a blink added per level, in ticks.", impact = "Higher values reward higher levels more.")
    int resistanceTicksPerLevel = 20;
    @ConfigDoc(value = "Resistance amplifier on arrival.", impact = "Higher values cut more damage while it lasts.")
    int resistanceAmplifier = 2;
    @ConfigDoc(value = "Share of a bounced pearl's direction taken from your crosshair.", impact = "Higher values steer bounces toward where you look.")
    double pearlAimBias = 0.6;
    @ConfigDoc(value = "Launch speed of a bounced pearl.", impact = "Higher values throw the bounce farther.")
    double pearlSpeed = 1.5;
    @ConfigDoc(value = "Health taken per blink when you carry no plain ender pearl.", impact = "Higher values make pearl-free blinks cost more; you never blink below this.")
    double healthCost = 1;
    @ConfigDoc(value = "Level from which a sneak hit tags a creature to swap places with.", impact = "Lower values unlock the swap sooner.")
    int taglockLevel = 2;
    @ConfigDoc(value = "Level from which players can be tagged and swapped, where PvP is allowed.", impact = "Lower values allow player swaps sooner.")
    int playerSwapLevel = 3;
    @ConfigDoc(value = "How long a tag waits for your next blink, in milliseconds.", impact = "Higher values keep the tag longer.")
    long tagMillis = 30000;
  }
}
