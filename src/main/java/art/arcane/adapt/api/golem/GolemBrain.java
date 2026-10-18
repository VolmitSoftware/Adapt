package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
  private boolean failureLogged;
  private GolemMode lastMode;
  private Location stayAnchor;
  private LivingEntity combatTarget;
  private LivingEntity appliedTarget;
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

  public Optional<LivingEntity> combatTarget() {
    return Optional.ofNullable(combatTarget);
  }

  public Optional<Location> stayAnchor() {
    return stayAnchor == null ? Optional.empty() : Optional.of(stayAnchor.clone());
  }

  public void alert() {
    alerted.set(true);
  }

  public void command(Entity target, long untilMillis) {
    command = target instanceof LivingEntity living ? new Command(living, untilMillis) : null;
  }

  public void clearCommand() {
    Command current = command;
    command = null;
    if (current != null && combatTarget == current.target()) {
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
        Adapt.warn("Failed to take control of bound golem " + handle.id() + "; it keeps its vanilla behavior.", failure);
        return;
      }
      attached = true;
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
      try {
        controller.release();
      } catch (RuntimeException failure) {
        Adapt.warn("Failed to restore the vanilla behavior of golem " + handle.id() + ".", failure);
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
        Adapt.warn("Golem brain pulse failed for bound golem " + handle.id() + "; it keeps running.", failure);
      }
    }
    schedule(token, intervalTicks());
  }

  private void think() {
    long now = context.platform().nowMillis();
    refreshStats(now);
    GolemBrainSettings settings = context.settings().get();
    Mob golem = handle.entity();
    Location here = golem.getLocation();
    handle.trackPosition(here);
    GolemMode mode = handle.state().mode();
    if (mode != lastMode) {
      lastMode = mode;
      stayAnchor = mode == GolemMode.STAY ? here.clone() : null;
    }
    Player owner = context.platform().player(handle.owner());
    OwnerSighting sighting = sightOwner(owner);
    Location ownerAt = sighting == null ? null : sighting.location();
    Location home = homeIn(here.getWorld());
    LivingEntity target = combat(owner, now);
    boolean working = work(mode, target, now, here, owner, home, settings);
    regen(now, here, ownerAt, home, !working && target == null);
    if (working) {
      return;
    }
    Location slot = mode == GolemMode.FOLLOW ? GolemFormation.slot(handle, ownerAt) : null;
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
    if (decision.dropTarget() && golem.getTarget() != null) {
      golem.setTarget(null);
    }
    move(decision.movement(), ownerAt, home, slot);
    controller.tick();
  }

  private boolean work(GolemMode mode, LivingEntity target, long now, Location here, Player owner, Location home, GolemBrainSettings settings) {
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

  private void regen(long now, Location here, Location ownerAt, Location home, boolean idle) {
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

  private LivingEntity combat(Player owner, long now) {
    if (!handle.body().fights()) {
      command = null;
      return null;
    }
    GolemTargetPolicy policy = context.policies().apply(GolemMastery.cores(handle));
    policy.tick(handle, owner);
    LivingEntity commanded = commanded(now);
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

  private LivingEntity commanded(long now) {
    Command current = command;
    if (current == null) {
      return null;
    }
    LivingEntity target = current.target();
    if (commandHolds(now, current.untilMillis(), target.isValid() && !target.isDead(), Objects.equals(target.getWorld(), handle.entity().getWorld()))) {
      return target;
    }
    if (command == current) {
      command = null;
    }
    if (combatTarget == target) {
      combatTarget = null;
    }
    return null;
  }

  private void applyTarget(LivingEntity target) {
    if (target == appliedTarget) {
      return;
    }
    appliedTarget = target;
    controller.setTarget(target);
  }

  private void move(GolemBrainMachine.Movement movement, Location ownerAt, Location home, Location slot) {
    switch (movement) {
      case NONE -> {
      }
      case STOP -> controller.stop();
      case OWNER -> controller.moveTo(ownerAt, MOVE_SPEED);
      case HOME -> controller.moveTo(home, MOVE_SPEED);
      case ANCHOR -> controller.moveTo(stayAnchor, MOVE_SPEED);
      case WANDER -> controller.moveTo(wanderPoint(home), MOVE_SPEED);
      case TELEPORT -> {
        controller.stop();
        GolemTeleport.teleport(handle, ownerAt.clone());
      }
      case SLOT -> controller.moveTo(slot, MOVE_SPEED);
    }
  }

  private OwnerSighting sightOwner(Player owner) {
    if (owner == null || !owner.isOnline()) {
      ownerSighting = null;
      return null;
    }
    if (context.platform().owns(owner)) {
      OwnerSighting fresh = new OwnerSighting(owner.getLocation(), owner.isOnGround());
      ownerSighting = fresh;
      return fresh;
    }
    if (ownerLookPending.compareAndSet(false, true)
        && !context.platform().runEntity(owner, () -> lookAtOwner(owner), () -> ownerLookPending.set(false))) {
      ownerLookPending.set(false);
    }
    return ownerSighting;
  }

  private void lookAtOwner(Player owner) {
    ownerSighting = new OwnerSighting(owner.getLocation(), owner.isOnGround());
    ownerLookPending.set(false);
  }

  private Location homeIn(World world) {
    GolemState.Anchor home = handle.state().home();
    if (world == null || home == null || !world.getUID().equals(home.world())) {
      return null;
    }
    return new Location(world, home.x() + 0.5D, home.y(), home.z() + 0.5D);
  }

  private int intervalTicks() {
    GolemBrainSettings settings = context.settings().get();
    return handle.body() == GolemBody.COPPER
        ? Math.min(settings.brainIntervalTicks(), settings.copperRepathTicks())
        : settings.brainIntervalTicks();
  }

  private static Location wanderPoint(Location home) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    double angle = random.nextDouble(Math.PI * 2D);
    double radius = random.nextDouble(GolemBrainMachine.WANDER_RADIUS);
    return home.clone().add(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
  }

  private static double distance(Location from, Location to) {
    if (to == null || from.getWorld() == null || to.getWorld() == null || !from.getWorld().getUID().equals(to.getWorld().getUID())) {
      return Double.NaN;
    }
    return from.distance(to);
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

  private record OwnerSighting(Location location, boolean grounded) {
  }

  private record Command(LivingEntity target, long untilMillis) {
  }
}
