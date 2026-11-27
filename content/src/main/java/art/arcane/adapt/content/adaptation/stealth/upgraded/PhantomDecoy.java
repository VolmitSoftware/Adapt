package art.arcane.adapt.content.adaptation.stealth.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.rift.RiftBlinkPath;
import art.arcane.adapt.content.adaptation.stealth.StealthCore;
import art.arcane.adapt.content.adaptation.stealth.StealthExecution;
import art.arcane.adapt.content.adaptation.stealth.StealthShadowDecoy;
import art.arcane.adapt.content.adaptation.stealth.StealthSmoke;
import art.arcane.adapt.content.adaptation.stealth.StealthSwap;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shadow Decoy, Decoy Swap and Smoke Pellet fused: a double sneak spends a Smoke Pellet to leave a decoy, and the swap
 * hands key trades places with it for as long as it stands. Using a pellet while unseen Shadow Steps: you vanish in
 * smoke and land behind the creature you look at, and your next hit on it within the window is a Death Mark opening.
 */
public class PhantomDecoy extends SkillUpgrade<PhantomDecoy.Config> {
  public static final Id ID = Id.adapt("stealth/upgrade/phantom_decoy");

  private static final long DOUBLE_TAP_MILLIS = 350L;

  private final StealthSmoke smoke = new StealthSmoke((Loc center, double radius, int pulsesLeft) ->
      fx(center, FxPriority.GAMEPLAY)
          .particle(Particles.SMOKE, (int) Math.max(8, radius * 6), 0, 0, 0, radius * 0.55D, 0.01D)
          .particle(Particles.SQUID_INK, (int) Math.max(2, radius), 0, 0, 0, radius * 0.4D, 0.005D));
  private final Cooldowns decoyCooldowns = cooldowns();
  private final Cooldowns swapCooldowns = cooldowns();
  private final Cooldowns stepCooldowns = cooldowns();
  private final Set<UUID> stepsInFlight = ConcurrentHashMap.newKeySet();
  private final SmokePellet pellet;
  private final Map<UUID, Long> lastSneakPress = playerState();
  private final StealthSwap swap = new StealthSwap(this, "Phantom Decoy", (PlayerRef player, Loc from, Loc to) -> {
    swapCooldowns.mark(player.uuid(), getConfig().swapCooldownMillis);
    addStat(player, "stealth.phantom-decoy.swaps", 1);
    cue(player, MutationCue.BOON);
    fx(from.add(0, 1.0D, 0), FxPriority.TRANSITION).burst(Particles.REVERSE_PORTAL, 10, 0.3D);
    fx(to.add(0, 1.0D, 0), FxPriority.TRANSITION).burst(Particles.REVERSE_PORTAL, 10, 0.3D);
  });

  public PhantomDecoy() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("armor_stand"));
    pellet = registerTool(new SmokePellet(this));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.duration(getDecoyTicks(level) * 50D, 1), 1);
    statLore(v, Format.f(getSmokeRadius(level), 1), 2);
    statLore(v, Format.duration(getCooldownMillis(level), 1), 3);
    statLore(v, Format.f(getStepRange(level), 0), 4);
    statLore(v, Format.duration(getStepCooldownMillis(level), 1), 5);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef player = e.player();
    if (!e.isSneaking()) {
      return;
    }

    long now = System.currentTimeMillis();
    Long last = lastSneakPress.put(player.uuid(), now);
    if (last == null || now - last > DOUBLE_TAP_MILLIS) {
      return;
    }
    lastSneakPress.remove(player.uuid());

    int level = getActiveLevel(player);
    long cooldown = getCooldownMillis(level);
    StealthShadowDecoy shadowDecoy = StealthShadowDecoy.of(getSkill());
    if (level <= 0 || shadowDecoy == null || !decoyCooldowns.isReady(player.uuid(), cooldown)) {
      return;
    }

    if (!pellet.take(player)) {
      return;
    }
    int durationTicks = getDecoyTicks(level);
    EntityRef anchor = shadowDecoy.spawnDecoy(this, player, durationTicks, getConfig().aggroRadius,
        (PlayerRef owner) -> addStat(owner, "stealth.phantom-decoy.mobs-distracted", 1));
    if (anchor == null) {
      pellet.refund(player);
      return;
    }

    decoyCooldowns.mark(player.uuid(), cooldown);
    addStat(player, "stealth.phantom-decoy.decoys", 1);
    cue(player, MutationCue.BOON);
    LingeringSound lingering = linger(anchor);
    AdaptApi.scheduler().runEntity(anchor, lingering::stop, durationTicks);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    StealthShadowDecoy shadowDecoy = StealthShadowDecoy.of(getSkill());
    EntityRef anchor = getActiveLevel(player) <= 0 || shadowDecoy == null
        ? null : shadowDecoy.activeDecoyAnchor(player.uuid());
    if (anchor == null) {
      return;
    }

    e.setCancelled(true);
    if (swapCooldowns.isReady(player.uuid(), getConfig().swapCooldownMillis)) {
      swap.swap(player, anchor, getConfig().swapRange);
    }
  }

  /** A pellet used in the main hand: steps behind the creature looked at when it cannot see you. */
  void shadowStep(ToolUse use) {
    PlayerRef player = use.player();
    StealthCore stealth = StealthCore.of(getSkill());
    if (stealth == null || !stepReady(player, use.level())) {
      return;
    }
    MobRef target = use.entity() instanceof MobRef clicked ? clicked : lookedAt(player, getStepRange(use.level()));
    if (target != null && stealth.unseenBy(player, target)) {
      stepBehind(player, target, use.level(), false);
    }
  }

  private boolean stepReady(PlayerRef player, int level) {
    return !stepsInFlight.contains(player.uuid()) && stepCooldowns.isReady(player.uuid(), getStepCooldownMillis(level));
  }

  /** Whether {@link #stepBehind} could put the player behind {@code target} now, without moving them. */
  public boolean canStepBehind(PlayerRef player, LivingRef target, int level) {
    double range = getStepRange(level);
    Loc destination = stepReady(player, level) ? standBehind(player, target, range) : null;
    return destination != null && landable(player, player.location(), destination, range);
  }

  private @Nullable Loc standBehind(PlayerRef player, LivingRef target, double range) {
    if (target == null || !target.isValid() || target.isDead() || StealthShadowDecoy.isDecoy(target)) {
      return null;
    }
    Loc origin = player.location();
    if (!origin.sameWorld(target.location()) || origin.distanceSquared(target.location()) > range * range
        || !RiftBlinkPath.hasClearSight(player.eyeLocation(), target.eyeLocation())) {
      return null;
    }
    return behind(player, target, range);
  }

  /**
   * Shadow Steps the player behind {@code target} within the range of {@code level}, under the one Shadow Step cooldown
   * every caller shares. A free step spends no Smoke Pellet; any other takes one and refunds it if the step fails. True
   * when the step started.
   */
  public boolean stepBehind(PlayerRef player, LivingRef target, int level, boolean free) {
    long cooldown = getStepCooldownMillis(level);
    if (!stepReady(player, level)) {
      return false;
    }
    double range = getStepRange(level);
    Loc origin = player.location();
    Loc destination = standBehind(player, target, range);
    if (destination != null) {
      destination = Integrations.announceTeleport(player, this, origin, destination).orElse(null);
    }
    if (destination == null || !landable(player, origin, destination, range)) {
      return false;
    }
    if (!free && !pellet.take(player)) {
      return false;
    }

    UUID id = player.uuid();
    stepsInFlight.add(id);
    Loc landing = destination;
    CompletableFuture<Boolean> teleport;
    try {
      teleport = player.teleport(landing, TeleportCause.PLUGIN);
    } catch (RuntimeException failed) {
      stepsInFlight.remove(id);
      if (!free) {
        pellet.refund(player);
      }
      return false;
    }
    teleport.whenComplete((Boolean moved, Throwable failure) -> {
      if (!AdaptApi.scheduler().runEntity(player, () -> {
        stepsInFlight.remove(id);
        if (failure != null || !Boolean.TRUE.equals(moved) || !isRuntimeRegistered()) {
          if (!free) {
            pellet.refund(player);
          }
          return;
        }
        landed(player, target, origin, landing, level, cooldown);
      })) {
        stepsInFlight.remove(id);
      }
    });
    return true;
  }

  private void landed(PlayerRef player, LivingRef target, Loc origin, Loc landing, int level, long cooldown) {
    stepCooldowns.mark(player.uuid(), cooldown);
    smoke.release(origin.add(0, 1.0D, 0), getSmokeRadius(level), getConfig().stepSmokePulses);
    if (StealthExecution.isExecutable(player, target)) {
      for (Adaptation<?> upgrade : getSkill().getUpgrades()) {
        if (upgrade instanceof DeathMark mark) {
          mark.open(player, target, getConfig().openingWindowMillis);
        }
      }
    }
    fx(landing.add(0, 1.0D, 0), FxPriority.TRANSITION).burst(Particles.SQUID_INK, 12, 0.35D);
    addStat(player, "stealth.phantom-decoy.shadow-steps", 1);
    cue(player, MutationCue.FIGHT);
  }

  private MobRef lookedAt(PlayerRef player, double range) {
    Loc eye = player.eyeLocation();
    EntityRef hit = eye.world().rayTraceEntity(eye.position(), eye.direction(), range, 0.3D,
        (EntityRef entity) -> entity instanceof MobRef && !entity.uuid().equals(player.uuid()));
    return hit instanceof MobRef mob ? mob : null;
  }

  /** The first safe stand behind the target's back, facing the way it faces, or null. */
  private Loc behind(PlayerRef player, LivingRef target, double range) {
    Loc at = target.location();
    double yaw = Math.toRadians(at.yaw());
    Vec3 facing = new Vec3(-Math.sin(yaw), 0D, Math.cos(yaw));
    double gap = Math.max(0.6D, getConfig().stepBehindDistance + (target.width() * 0.5D));
    for (double lift = 0D; lift <= 1D; lift += 1D) {
      Loc candidate = at.add(facing.multiply(-gap)).add(0D, lift, 0D).withRotation(at.yaw(), 0F);
      BlockRef floor = candidate.world().blockAt(candidate.add(0D, -0.5D, 0D));
      if (!floor.isPassable() && RiftBlinkPath.validCandidate(player, player.location(), candidate, range + gap + 1D, 0.5D)
          && RiftBlinkPath.hasClearSight(target.eyeLocation(), candidate.add(0D, player.height() * 0.5D, 0D))) {
        return candidate;
      }
    }
    return null;
  }

  private boolean landable(PlayerRef player, Loc origin, Loc destination, double range) {
    return RiftBlinkPath.validCandidate(player, origin, destination, range + getConfig().stepBehindDistance + 2D, 0.5D)
        && canInteract(player, destination) && checkRegion(player, destination);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityTargetEvent e) {
    smoke.onTarget(e);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    smoke.forget(e.player());
    lastSneakPress.remove(e.player().uuid());
    stepsInFlight.remove(e.player().uuid());
  }

  @Override
  public void unregister() {
    smoke.close();
    swap.clear();
    stepsInFlight.clear();
    super.unregister();
  }

  int getDecoyTicks(int level) {
    return Math.max(20, getConfig().decoyTicksBase + (level * getConfig().decoyTicksPerLevel));
  }

  double getSmokeRadius(int level) {
    return getConfig().smokeRadiusBase + (level * getConfig().smokeRadiusPerLevel);
  }

  long getCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumCooldownMillis,
        getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  public double getStepRange(int level) {
    return getConfig().stepRangeBase + (level * getConfig().stepRangePerLevel);
  }

  long getStepCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumStepCooldownMillis,
        getConfig().stepCooldownMillisBase - (level * getConfig().stepCooldownMillisPerLevel));
  }

  @ConfigDescription("A Smoke Pellet leaves a decoy you can swap places with, or steps you behind a creature that cannot see you.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Decoy lifetime before level scaling, in ticks.", impact = "Higher values keep the decoy and its smoke longer.")
    int decoyTicksBase = 80;
    @ConfigDoc(value = "Decoy lifetime added per level, in ticks.", impact = "Higher values reward higher levels more.")
    int decoyTicksPerLevel = 40;
    @ConfigDoc(value = "Radius around the decoy that it pulls aggro from, in blocks.", impact = "Higher values pull mobs from farther away.")
    double aggroRadius = 12;
    @ConfigDoc(value = "Smoke cloud radius before level scaling, in blocks.", impact = "Higher values hide more around the decoy.")
    double smokeRadiusBase = 2.5;
    @ConfigDoc(value = "Smoke cloud radius added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double smokeRadiusPerLevel = 0.75;
    @ConfigDoc(value = "Time between decoys before level scaling, in milliseconds.", impact = "Higher values make decoys rarer.")
    double cooldownMillisBase = 20000;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values let higher levels spawn decoys more often.")
    double cooldownMillisPerLevel = 4000;
    @ConfigDoc(value = "Shortest time between decoys, in milliseconds.", impact = "Lower values allow more frequent decoys.")
    double minimumCooldownMillis = 6000;
    @ConfigDoc(value = "Farthest the decoy can be for a swap, in blocks.", impact = "Higher values let you swap from farther away.")
    double swapRange = 48;
    @ConfigDoc(value = "Time between two swaps, in milliseconds.", impact = "Lower values let you swap back and forth faster.")
    long swapCooldownMillis = 1000;
    @ConfigDoc(value = "Shadow Step range before level scaling, in blocks.", impact = "Higher values let you step to farther creatures.")
    double stepRangeBase = 4;
    @ConfigDoc(value = "Shadow Step range added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double stepRangePerLevel = 4;
    @ConfigDoc(value = "Time between Shadow Steps before level scaling, in milliseconds.", impact = "Higher values make steps rarer.")
    double stepCooldownMillisBase = 25000;
    @ConfigDoc(value = "Shadow Step cooldown removed per level, in milliseconds.", impact = "Higher values let higher levels step more often.")
    double stepCooldownMillisPerLevel = 5000;
    @ConfigDoc(value = "Shortest time between Shadow Steps, in milliseconds.", impact = "Lower values allow more frequent steps.")
    double minimumStepCooldownMillis = 5000;
    @ConfigDoc(value = "Gap kept behind the target's back on landing, in blocks.", impact = "Higher values land you farther from the target.")
    double stepBehindDistance = 0.8;
    @ConfigDoc(value = "Smoke pulses left where you vanish.", impact = "Higher values keep the smoke longer.")
    int stepSmokePulses = 4;
    @ConfigDoc(value = "Time after a Shadow Step in which your first hit on the target is a Death Mark opening, in milliseconds.", impact = "Higher values give more time to strike.")
    long openingWindowMillis = 2000;
  }
}
