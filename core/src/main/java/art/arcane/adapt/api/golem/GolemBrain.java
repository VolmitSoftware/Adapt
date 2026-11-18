package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

public final class GolemBrain {
  private static final double MOVE_SPEED = 1D;
  private static final long TARGET_SCAN_MILLIS = 500L;
  private static final long MAX_IDLE_SCAN_MILLIS = 2_000L;
  private static final long STATS_REFRESH_MILLIS = 1_000L;

  private final GolemHandle handle;
  private final GolemController controller;
  private final Context context;
  private final GolemBrainMachine machine = new GolemBrainMachine();
  private final GolemWorker worker;
  private final AtomicLong generation = new AtomicLong();
  private final AtomicBoolean ownerLookPending = new AtomicBoolean();
  private final AtomicBoolean alerted = new AtomicBoolean();
  private volatile boolean running;
  private volatile OwnerSighting ownerSighting;
  private volatile Command command;
  private boolean attached;
  private boolean fighting = true;
  private boolean failureLogged;
  private GolemMode lastMode;
  private Loc stayAnchor;
  private LivingRef combatTarget;
  private LivingRef appliedTarget;
  private GolemBrainState workState;
  private long nextScanAt;
  private long scanDelay = TARGET_SCAN_MILLIS;
  private long nextStatsAt;
  private long nextRegenAt;

  GolemBrain(GolemHandle handle, GolemController controller, Context context) {
    this.handle = Objects.requireNonNull(handle);
    this.controller = Objects.requireNonNull(controller);
    this.context = Objects.requireNonNull(context);
    this.worker = new GolemWorker(handle, controller, context);
  }

  public GolemHandle handle() {
    return handle;
  }

  public boolean isRunning() {
    return running;
  }

  public GolemBrainState state() {
    GolemBrainState working = workState;
    return working == null ? machine.state() : working;
  }

  public Optional<Loc> stayAnchor() {
    return Optional.ofNullable(stayAnchor);
  }

  public void alert() {
    alerted.set(true);
  }

  public void command(EntityRef target, long untilMillis) {
    command = target instanceof LivingRef living ? new Command(living, untilMillis) : null;
  }

  public void clearCommand() {
    Command current = command;
    command = null;
    if (current != null && same(combatTarget, current.target())) {
      combatTarget = null;
    }
  }

  static boolean commandHolds(long nowMillis, long untilMillis, boolean targetLive, boolean sameWorld) {
    return targetLive && sameWorld && nowMillis < untilMillis;
  }

  void start() {
    if (running) {
      return;
    }
    running = true;
    machine.reset();
    workState = null;
    ownerLookPending.set(false);
    alerted.set(false);
    scanDelay = TARGET_SCAN_MILLIS;
    lastMode = null;
    combatTarget = null;
    appliedTarget = null;
    nextRegenAt = 0L;
    command = null;
    if (!attached) {
      try {
        controller.attach();
      } catch (RuntimeException failure) {
        running = false;
        AdaptLog.warn("Failed to take control of bound golem " + handle.id() + "; it keeps its vanilla behavior.", failure);
        return;
      }
      attached = true;
      fighting = true;
    }
    schedule(generation.incrementAndGet(), 1);
  }

  void sleep() {
    generation.incrementAndGet();
    if (!running) {
      return;
    }
    running = false;
    ownerLookPending.set(false);
    worker.abandon();
    workState = null;
    combatTarget = null;
    appliedTarget = null;
    command = null;
    controller.setTarget(null);
    controller.stop();
  }

  void detach() {
    generation.incrementAndGet();
    running = false;
    if (!context.platform().owns(handle.entity())) {
      return;
    }
    worker.abandon();
    workState = null;
    if (attached) {
      attached = false;
      fighting = true;
      try {
        controller.release();
      } catch (RuntimeException failure) {
        AdaptLog.warn("Failed to restore the vanilla behavior of golem " + handle.id() + ".", failure);
      }
    }
  }

  private void schedule(long token, int delayTicks) {
    if (!context.platform().runEntity(handle.entity(), () -> pulse(token), delayTicks)) {
      running = false;
    }
  }

  private void pulse(long token) {
    if (token != generation.get() || handle.isRetired()) {
      return;
    }
    if (!handle.entity().isValid()) {
      running = false;
      return;
    }
    try {
      think();
    } catch (RuntimeException failure) {
      if (!failureLogged) {
        failureLogged = true;
        AdaptLog.warn("Golem brain pulse failed for bound golem " + handle.id() + "; it keeps running.", failure);
      }
    }
    schedule(token, intervalTicks());
  }

  private void think() {
    long now = context.platform().nowMillis();
    refreshStats(now);
    GolemBrainSettings settings = context.settings().get();
    MobRef golem = handle.entity();
    Loc here = golem.location();
    handle.trackPosition(here);
    applyCombat(GolemCombat.enabled(handle));
    GolemMode mode = handle.state().mode();
    if (mode != lastMode) {
      lastMode = mode;
      stayAnchor = mode == GolemMode.STAY ? here : null;
    }
    PlayerRef owner = context.platform().player(handle.owner());
    OwnerSighting sighting = sightOwner(owner);
    Loc ownerAt = sighting == null ? null : sighting.location();
    Loc home = homeIn(here.world());
    LivingRef target = combat(owner, now);
    boolean working = work(mode, target, now, here, owner, home, settings);
    regen(now, here, ownerAt, home, !working && target == null);
    if (working) {
      return;
    }
    Loc slot = mode == GolemMode.FOLLOW ? GolemFormation.slot(handle, ownerAt) : null;
    GolemBrainMachine.Senses senses = new GolemBrainMachine.Senses(now, mode, distance(here, ownerAt), sighting != null && sighting.grounded(),
        distance(here, home), distance(here, stayAnchor), distance(here, slot), target != null);
    GolemBrainMachine.Decision decision = machine.decide(senses, settings);
    if (decision.state() == GolemBrainState.RETURN_HOME) {
      worker.forgetWorkedNode();
    }
    if (decision.dropTarget()) {
      combatTarget = null;
      command = null;
    }
    applyTarget(decision.state() == GolemBrainState.COMBAT ? target : null);
    if (decision.dropTarget() && golem.target() != null) {
      golem.setTarget(null);
    }
    move(decision.movement(), ownerAt, home, slot);
    controller.tick();
  }

  private boolean work(GolemMode mode, LivingRef target, long now, Loc here, PlayerRef owner, Loc home, GolemBrainSettings settings) {
    GolemBrainState working = null;
    if (mode == GolemMode.WORK && target == null && !machine.combatSuppressed()) {
      working = worker.pulse(now, here, owner != null && owner.isOnline() ? owner : null, home, settings);
    } else {
      worker.abandon();
    }
    if (working == null) {
      if (workState != null) {
        workState = null;
        controller.stop();
      }
      return false;
    }
    workState = working;
    machine.working(now);
    applyTarget(null);
    controller.tick();
    return true;
  }

  private void regen(long now, Loc here, Loc ownerAt, Loc home, boolean idle) {
    long interval = GolemMend.regenIntervalMillis();
    if (interval <= 0L) {
      nextRegenAt = 0L;
      return;
    }
    if (nextRegenAt == 0L) {
      nextRegenAt = now + interval;
      return;
    }
    if (now < nextRegenAt) {
      return;
    }
    nextRegenAt = now + interval;
    double amount = GolemMend.regen(handle, new GolemMend.RegenSenses(distance(here, ownerAt), distance(here, home), idle));
    if (amount > 0D) {
      context.platform().heal(handle.entity(), amount);
    }
  }

  private void refreshStats(long now) {
    boolean weathered = handle.refreshPatinaStage();
    if (!weathered && now < nextStatsAt) {
      return;
    }
    nextStatsAt = now + STATS_REFRESH_MILLIS;
    GolemColossus.settle(handle);
    GolemStats.recompute(handle);
  }

  private void applyCombat(boolean enabled) {
    if (enabled == fighting) {
      return;
    }
    controller.setCombat(enabled);
    fighting = enabled;
    if (enabled) {
      return;
    }
    combatTarget = null;
    command = null;
    applyTarget(null);
    MobRef golem = handle.entity();
    if (golem.target() != null) {
      golem.setTarget(null);
    }
  }

  private LivingRef combat(PlayerRef owner, long now) {
    if (!handle.body().fights() || !fighting) {
      command = null;
      combatTarget = null;
      return null;
    }
    GolemTargetPolicy policy = context.policies().apply(GolemMastery.cores(handle));
    policy.tick(handle, owner);
    LivingRef commanded = commanded(now);
    if (commanded != null) {
      combatTarget = commanded;
      return commanded;
    }
    if (combatTarget != null && !policy.holds(handle, owner, combatTarget)) {
      combatTarget = null;
    }
    if (alerted.getAndSet(false)) {
      scanDelay = TARGET_SCAN_MILLIS;
      nextScanAt = now;
    }
    if (combatTarget == null && !machine.combatSuppressed() && now >= nextScanAt) {
      combatTarget = policy.select(handle, owner);
      if (combatTarget == null && policy.idleBackoff()) {
        nextScanAt = now + scanDelay;
        scanDelay = Math.min(scanDelay * 2L, MAX_IDLE_SCAN_MILLIS);
      } else {
        scanDelay = TARGET_SCAN_MILLIS;
        nextScanAt = now + TARGET_SCAN_MILLIS;
      }
    }
    return combatTarget;
  }

  private LivingRef commanded(long now) {
    Command current = command;
    if (current == null) {
      return null;
    }
    LivingRef target = current.target();
    if (commandHolds(now, current.untilMillis(), target.isValid() && !target.isDead(), sameWorld(target.world(), handle.entity().world()))) {
      return target;
    }
    if (command == current) {
      command = null;
    }
    if (same(combatTarget, target)) {
      combatTarget = null;
    }
    return null;
  }

  private void applyTarget(LivingRef target) {
    if (same(target, appliedTarget)) {
      return;
    }
    appliedTarget = target;
    controller.setTarget(target);
  }

  private void move(GolemBrainMachine.Movement movement, Loc ownerAt, Loc home, Loc slot) {
    switch (movement) {
      case NONE -> {
      }
      case STOP -> controller.stop();
      case OWNER -> controller.moveTo(ownerAt, MOVE_SPEED);
      case HOME -> controller.moveTo(home, MOVE_SPEED);
      case ANCHOR -> controller.moveTo(stayAnchor, MOVE_SPEED);
      case TELEPORT -> {
        controller.stop();
        GolemTeleport.teleport(handle, ownerAt);
      }
      case SLOT -> controller.moveTo(slot, MOVE_SPEED);
    }
  }

  private OwnerSighting sightOwner(PlayerRef owner) {
    if (owner == null || !owner.isOnline()) {
      ownerSighting = null;
      return null;
    }
    if (context.platform().owns(owner)) {
      OwnerSighting fresh = new OwnerSighting(owner.location(), owner.isOnGround());
      ownerSighting = fresh;
      return fresh;
    }
    if (ownerLookPending.compareAndSet(false, true)
        && !context.platform().runEntity(owner, () -> lookAtOwner(owner), () -> ownerLookPending.set(false))) {
      ownerLookPending.set(false);
    }
    return ownerSighting;
  }

  private void lookAtOwner(PlayerRef owner) {
    ownerSighting = new OwnerSighting(owner.location(), owner.isOnGround());
    ownerLookPending.set(false);
  }

  private Loc homeIn(WorldRef world) {
    GolemAnchor home = handle.state().home();
    if (world == null || home == null || !world.uid().equals(home.world())) {
      return null;
    }
    return new Loc(world, home.x() + 0.5D, home.y(), home.z() + 0.5D);
  }

  private int intervalTicks() {
    GolemBrainSettings settings = context.settings().get();
    return handle.body() == GolemBody.COPPER
        ? Math.min(settings.brainIntervalTicks(), settings.copperRepathTicks())
        : settings.brainIntervalTicks();
  }

  private static double distance(Loc from, Loc to) {
    if (to == null || from.world() == null || to.world() == null || !from.world().uid().equals(to.world().uid())) {
      return Double.NaN;
    }
    return from.distance(to);
  }

  private static boolean sameWorld(WorldRef first, WorldRef second) {
    return first == null ? second == null : second != null && first.uid().equals(second.uid());
  }

  private static boolean same(LivingRef first, LivingRef second) {
    if (first == second) {
      return true;
    }
    return first != null && second != null && first.uuid() != null && first.uuid().equals(second.uuid());
  }

  record Context(GolemPlatform platform, Supplier<GolemBrainSettings> settings, Function<List<GolemCoreType>, GolemTargetPolicy> policies,
                 Supplier<GolemWorkSettings> work, Function<UUID, GolemTaskBoard> boards) {
    Context {
      Objects.requireNonNull(platform);
      Objects.requireNonNull(settings);
      Objects.requireNonNull(policies);
      Objects.requireNonNull(work);
      Objects.requireNonNull(boards);
    }
  }

  private record OwnerSighting(Loc location, boolean grounded) {
  }

  private record Command(LivingRef target, long untilMillis) {
  }
}
