package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

public final class GolemBrain {
  private static final double MOVE_SPEED = 1D;
  private static final long TARGET_SCAN_MILLIS = 500L;

  private final GolemHandle handle;
  private final GolemController controller;
  private final Context context;
  private final GolemBrainMachine machine = new GolemBrainMachine();
  private final AtomicLong generation = new AtomicLong();
  private final AtomicBoolean ownerLookPending = new AtomicBoolean();
  private volatile boolean running;
  private volatile OwnerSighting ownerSighting;
  private boolean attached;
  private boolean failureLogged;
  private GolemMode lastMode;
  private Location stayAnchor;
  private LivingEntity combatTarget;
  private LivingEntity appliedTarget;
  private long nextScanAt;

  GolemBrain(GolemHandle handle, GolemController controller, Context context) {
    this.handle = Objects.requireNonNull(handle);
    this.controller = Objects.requireNonNull(controller);
    this.context = Objects.requireNonNull(context);
  }

  public GolemHandle handle() {
    return handle;
  }

  public boolean isRunning() {
    return running;
  }

  public GolemBrainState state() {
    return machine.state();
  }

  public Optional<LivingEntity> combatTarget() {
    return Optional.ofNullable(combatTarget);
  }

  void start() {
    if (running) {
      return;
    }
    running = true;
    machine.reset();
    ownerLookPending.set(false);
    lastMode = null;
    combatTarget = null;
    appliedTarget = null;
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
    combatTarget = null;
    appliedTarget = null;
    controller.setTarget(null);
    controller.stop();
  }

  void detach() {
    generation.incrementAndGet();
    running = false;
    if (attached && context.platform().owns(handle.entity())) {
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
    GolemBrainSettings settings = context.settings().get();
    Mob golem = handle.entity();
    Location here = golem.getLocation();
    handle.trackPosition(here);
    long now = context.platform().nowMillis();
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
    GolemBrainMachine.Senses senses = new GolemBrainMachine.Senses(now, mode, distance(here, ownerAt), sighting != null && sighting.grounded(),
        distance(here, home), distance(here, stayAnchor), target != null);
    GolemBrainMachine.Decision decision = machine.decide(senses, settings);
    if (decision.dropTarget()) {
      combatTarget = null;
    }
    applyTarget(decision.state() == GolemBrainState.COMBAT ? target : null);
    if (decision.dropTarget() && golem.getTarget() != null) {
      golem.setTarget(null);
    }
    move(decision.movement(), golem, ownerAt, home);
    controller.tick();
  }

  private LivingEntity combat(Player owner, long now) {
    if (!handle.body().fights()) {
      return null;
    }
    GolemTargetPolicy policy = context.policies().apply(handle.core());
    if (combatTarget != null && !policy.holds(handle, owner, combatTarget)) {
      combatTarget = null;
    }
    if (combatTarget == null && !machine.combatSuppressed() && now >= nextScanAt) {
      nextScanAt = now + TARGET_SCAN_MILLIS;
      combatTarget = policy.select(handle, owner);
    }
    return combatTarget;
  }

  private void applyTarget(LivingEntity target) {
    if (target == appliedTarget) {
      return;
    }
    appliedTarget = target;
    controller.setTarget(target);
  }

  private void move(GolemBrainMachine.Movement movement, Mob golem, Location ownerAt, Location home) {
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
        PaperCompat.teleportWithPassengers(golem, ownerAt.clone());
      }
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

  record Context(GolemPlatform platform, Supplier<GolemBrainSettings> settings, Function<GolemCoreType, GolemTargetPolicy> policies) {
    Context {
      Objects.requireNonNull(platform);
      Objects.requireNonNull(settings);
      Objects.requireNonNull(policies);
    }
  }

  private record OwnerSighting(Location location, boolean grounded) {
  }
}
