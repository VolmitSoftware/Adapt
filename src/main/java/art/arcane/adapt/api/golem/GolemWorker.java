package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.node.DepositBehavior;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class GolemWorker {
  static final double REACH = 2.5D;
  static final long BACKOFF_MILLIS = 10_000L;
  static final long MAX_BACKOFF_MILLIS = 60_000L;
  static final long FAILURE_MEMORY_MILLIS = 120_000L;
  static final long FULL_SIGNAL_MILLIS = 3_000L;
  static final long SEEK_INTERVAL_MILLIS = 1_000L;
  static final long REROUTE_INTERVAL_MILLIS = 3_000L;
  static final String DELIVER_TYPE = "deliver";

  private static final double MOVE_SPEED = 1D;
  private static final long TICK_MILLIS = 50L;

  private final GolemHandle handle;
  private final GolemController controller;
  private final GolemBrain.Context context;
  private final GolemCargo cargo;
  private final GolemStuckWatch stuck = new GolemStuckWatch();
  private final Map<TaskKey, Long> taskBackoff = new HashMap<>();
  private final Map<TaskKey, Failure> failures = new HashMap<>();
  private final Map<GolemState.Anchor, Long> containerBackoff = new HashMap<>();
  private Player owner;
  private Location home;
  private Job job;
  private Delivery delivery;
  private WorkedNode lastNode;
  private boolean arrived;
  private boolean routeHeld;
  private int stuckReleases;
  private int pulseCapacity = -1;
  private GolemRuneEffects pulseRunes;
  private List<GolemCoreType> pulseCores;
  private long nextActAt;
  private long nextSeekAt;
  private long nextRouteAt;
  private long nextSignalAt;
  private boolean failureLogged;
  private boolean tickFailureLogged;

  GolemWorker(GolemHandle handle, GolemController controller, GolemBrain.Context context) {
    this.handle = Objects.requireNonNull(handle);
    this.controller = Objects.requireNonNull(controller);
    this.context = Objects.requireNonNull(context);
    this.cargo = new GolemCargo(handle);
  }

  GolemBrainState pulse(long nowMillis, Location here, Player currentOwner, Location currentHome, GolemBrainSettings brain) {
    owner = currentOwner;
    home = currentHome;
    pulseCapacity = -1;
    pulseRunes = null;
    pulseCores = null;
    if (!canWork(here, brain)) {
      abandon();
      return null;
    }
    expireBackoff(nowMillis);
    tickCores(nowMillis);
    GolemWorkSettings settings = context.work().get();
    if (job != null) {
      GolemBrainState pursuing = pursue(nowMillis, here, settings);
      if (pursuing != null) {
        return pursuing;
      }
    }
    if (delivery != null) {
      GolemBrainState delivering = deliver(nowMillis, here, settings);
      if (delivering != null) {
        return delivering;
      }
    }
    int carried = cargo.count();
    if (carried == 0) {
      routeHeld = false;
    }
    boolean full = carried > 0 && carried >= capacity();
    if (!full && nowMillis >= nextSeekAt && !deliveringFirst(carried)) {
      nextSeekAt = nowMillis + SEEK_INTERVAL_MILLIS;
      if (claim(nowMillis, here, settings) || seekCores(nowMillis)) {
        return pursue(nowMillis, here, settings);
      }
    }
    if (carried > 0 && nowMillis >= nextRouteAt && route(nowMillis, here)) {
      return job != null ? pursue(nowMillis, here, settings) : deliver(nowMillis, here, settings);
    }
    if (full) {
      signalFull(nowMillis);
    }
    return null;
  }

  void abandon() {
    if (job != null) {
      releaseClaim(job);
    }
    job = null;
    delivery = null;
    arrived = false;
    stuck.reset();
  }

  void forgetWorkedNode() {
    lastNode = null;
  }

  GolemHandle handle() {
    return handle;
  }

  Player owner() {
    return owner;
  }

  GolemPlatform platform() {
    return context.platform();
  }

  GolemCargo cargo() {
    return cargo;
  }

  GolemController controller() {
    return controller;
  }

  int capacity() {
    if (pulseCapacity < 0) {
      pulseCapacity = GolemCapacity.carry(handle);
    }
    return pulseCapacity;
  }

  GolemRuneEffects runes() {
    if (pulseRunes == null) {
      pulseRunes = GolemRunes.effects(handle);
    }
    return pulseRunes;
  }

  List<GolemCoreType> cores() {
    if (pulseCores == null) {
      pulseCores = GolemMastery.cores(handle);
    }
    return pulseCores;
  }

  Location home() {
    return home;
  }

  double workRadius() {
    return context.work().get().baseWorkRadius() + runes().radiusBonus();
  }

  boolean inWorkRadius(UUID world, double x, double y, double z) {
    Location center = home;
    World centerWorld = center == null ? null : center.getWorld();
    if (centerWorld == null || !centerWorld.getUID().equals(world)) {
      return false;
    }
    double radius = workRadius();
    double dx = x - center.getX();
    double dy = y - center.getY();
    double dz = z - center.getZ();
    return dx * dx + dy * dy + dz * dz <= radius * radius;
  }

  int actIndex() {
    return job == null ? 0 : job.actIndex;
  }

  void interact(Block container, int ticks) {
    if (!context.platform().owns(container.getLocation())) {
      return;
    }
    controller.interact(container, ticks);
    context.platform().visitLid(container, ticks + ContainerLids.CLOSE_TICKS);
  }

  boolean skips(GolemTask task, long nowMillis) {
    return taskBackoff.containsKey(task.key()) || board(task.target().world()).reservedByOther(task, handle.id(), nowMillis);
  }

  boolean renew(GolemTask leased, long nowMillis) {
    Job current = job;
    if (current == null || current.task != leased) {
      return false;
    }
    return switch (current.source) {
      case BOARD -> board(leased.nodeKey().world()).renew(leased, handle.id(), nowMillis, context.work().get().leaseMillis());
      case CORE -> keepReservation(leased, nowMillis);
      case ROUTE -> false;
    };
  }

  private boolean canWork(Location here, GolemBrainSettings brain) {
    if (owner == null || home == null || here.getWorld() == null || !GolemWorkGate.canWork(handle)
        || !GolemancyRuntime.settings().allows(handle.body(), handle.core())) {
      return false;
    }
    return here.distanceSquared(home) <= brain.leashRadius() * brain.leashRadius();
  }

  private GolemBrainState pursue(long nowMillis, Location here, GolemWorkSettings settings) {
    Job current = job;
    GolemTask task = current.task;
    World world = here.getWorld();
    if ((current.source == Source.BOARD && !task.heldBy(handle.id(), nowMillis))
        || (current.source == Source.CORE && !keepReservation(task, nowMillis))) {
      dropJob();
      return null;
    }
    NodeKey key = current.node == null ? null : current.node.key();
    NodeRecord record = null;
    if (key != null) {
      if (!world.getUID().equals(key.world())) {
        giveUp(nowMillis, false);
        return null;
      }
      record = board(key.world()).node(key).orElse(null);
      if (record == null) {
        closeJob();
        return null;
      }
    }
    if (!world.getUID().equals(task.target().world())) {
      giveUp(nowMillis, false);
      return null;
    }
    Location target = context.platform().targetLocation(task.target(), world);
    double distance = here.distance(target);
    if (distance > REACH) {
      arrived = false;
      GolemStuckWatch.Progress progress = stuck.track(nowMillis, distance, settings.stuckMillis());
      if (progress == GolemStuckWatch.Progress.STUCK) {
        giveUp(nowMillis, true);
        pauseIfTrapped(nowMillis, settings);
        return null;
      }
      if (progress == GolemStuckWatch.Progress.IMPROVED) {
        renewBoard(current, nowMillis, settings);
      }
      controller.moveTo(target, MOVE_SPEED);
      return current.moving();
    }
    if (!arrived) {
      arrive();
      if (!current.reached) {
        current.reached = true;
        nextActAt = nowMillis;
      }
    }
    if (nowMillis < nextActAt) {
      keepLease(current, nowMillis, settings);
      return current.acting();
    }
    if (!context.platform().owns(target) || !actorStillRegistered(current)) {
      giveUp(nowMillis, false);
      return current.acting();
    }
    if (record != null && !nodeActive(world, key, record)) {
      closeJob();
      return current.acting();
    }
    act(current, key, record, nowMillis, settings);
    int carried = cargo.count();
    if (job == current && current.source != Source.ROUTE && carried > 0 && carried >= capacity()) {
      dropJob();
    }
    return current.acting();
  }

  private void act(Job current, NodeKey key, NodeRecord record, long nowMillis, GolemWorkSettings settings) {
    GolemTask task = current.task;
    GolemContext golem = new GolemContext(this, new GolemContext.Scope(current.actor.policy(), task, record, current.core, nowMillis));
    if (current.source != Source.ROUTE) {
      lastNode = current.node;
    }
    GolemActResult result;
    try {
      result = Objects.requireNonNull(current.actor.act(golem, task), "GolemActor.act returned null");
    } catch (RuntimeException failure) {
      logFailure(task, failure);
      giveUp(nowMillis, false);
      return;
    }
    current.actIndex++;
    switch (result) {
      case WORKING -> {
        renewBoard(current, nowMillis, settings);
        markWorked(key, nowMillis);
        nextActAt = nowMillis + intervalMillis(current.actor.actIntervalTicks(golem, task));
      }
      case DONE -> {
        if (current.source == Source.BOARD) {
          board(key.world()).complete(task, handle.id());
          failures.remove(task.key());
        }
        markWorked(key, nowMillis);
        if (current.source != Source.ROUTE) {
          handle.addTasks(1L);
          GolemWorkStats.completed(handle);
        } else {
          nextRouteAt = nowMillis;
        }
        dropJob();
      }
      case FAILED -> failed(current, nowMillis);
      case BLOCKED -> {
        if (key != null) {
          board(key.world()).markBlocked(key, nowMillis);
        }
        giveUp(nowMillis, false);
      }
    }
  }

  private boolean claim(long nowMillis, Location here, GolemWorkSettings settings) {
    GolemTaskBoard board = board(home.getWorld().getUID());
    GolemState state = handle.state();
    List<GolemCoreType> cores = cores();
    GolemTaskBoard.Seeker seeker = new GolemTaskBoard.Seeker(handle.id(), handle.owner(), cores, handle.body(), runes().channels(state.channels()),
        home.getX(), home.getY(), home.getZ(), workRadius(), here.getX(), here.getY(), here.getZ());
    Optional<GolemTask> claimed = board.claim(seeker, candidate -> skips(candidate, nowMillis), nowMillis, settings.leaseMillis());
    if (claimed.isEmpty()) {
      return false;
    }
    GolemTask task = claimed.get();
    NodeType nodeType = task.nodeType().orElseThrow();
    NodeBehavior behavior = NodeBehaviors.of(nodeType).orElse(null);
    if (behavior == null) {
      board.release(task, handle.id());
      backOff(task, nowMillis);
      return false;
    }
    GolemCoreType consumer = GolemTaskMatch.consumer(nodeType, cores).orElse(handle.core());
    start(new Job(task, behavior, Source.BOARD, consumer, new WorkedNode(task.nodeKey(), nodeType)));
    return true;
  }

  private boolean seekCores(long nowMillis) {
    for (GolemCoreType core : cores()) {
      GolemCoreBehavior behavior = GolemCoreBehaviors.of(core).orElse(null);
      if (behavior == null) {
        continue;
      }
      GolemContext golem = new GolemContext(this, new GolemContext.Scope(behavior.policy(), null, null, core, nowMillis));
      Optional<GolemTask> found;
      try {
        found = behavior.seek(golem);
      } catch (RuntimeException failure) {
        logFailure(null, failure);
        continue;
      }
      if (found.isPresent() && reserveCoreJob(found.get(), nowMillis)) {
        start(new Job(found.get(), behavior, Source.CORE, core, workedNode(found.get())));
        return true;
      }
    }
    return false;
  }

  private void tickCores(long nowMillis) {
    for (GolemCoreType core : cores()) {
      GolemCoreBehavior behavior = GolemCoreBehaviors.of(core).orElse(null);
      if (behavior == null) {
        continue;
      }
      try {
        behavior.tick(new GolemContext(this, new GolemContext.Scope(behavior.policy(), null, null, core, nowMillis)));
      } catch (RuntimeException failure) {
        logTickFailure(core, failure);
      }
    }
  }

  private boolean reserveCoreJob(GolemTask task, long nowMillis) {
    GolemTarget target = task.target();
    return !skips(task, nowMillis) && inWorkRadius(target.world(), target.x(), target.y(), target.z())
        && board(target.world()).reserve(task, handle.id(), nowMillis, GolemTaskBoard.RESERVATION_MILLIS);
  }

  private boolean keepReservation(GolemTask task, long nowMillis) {
    return board(task.target().world()).keepReservation(task, handle.id(), nowMillis, GolemTaskBoard.RESERVATION_MILLIS);
  }

  private GolemBrainState deliver(long nowMillis, Location here, GolemWorkSettings settings) {
    World world = here.getWorld();
    GolemState.Anchor chest = delivery.chest();
    if (!world.getUID().equals(chest.world())) {
      finishDelivery();
      return null;
    }
    Location target = new Location(world, chest.x() + 0.5D, chest.y() + 0.5D, chest.z() + 0.5D);
    double distance = here.distance(target);
    if (distance > REACH) {
      arrived = false;
      if (stuck.track(nowMillis, distance, settings.stuckMillis()) == GolemStuckWatch.Progress.STUCK) {
        containerBackoff.put(chest, nowMillis + BACKOFF_MILLIS);
        stuckReleases++;
        finishDelivery();
        controller.stop();
        pauseIfTrapped(nowMillis, settings);
        return null;
      }
      controller.moveTo(target, MOVE_SPEED);
      return GolemBrainState.DELIVER;
    }
    Block block = world.getBlockAt(chest.x(), chest.y(), chest.z());
    if (!arrived) {
      arrive();
      long interval = intervalMillis(GolemActor.DEFAULT_ACT_INTERVAL_TICKS);
      interact(block, (int) Math.max(1L, interval / TICK_MILLIS));
      nextActAt = nowMillis + interval;
      return GolemBrainState.DELIVER;
    }
    if (nowMillis < nextActAt) {
      return GolemBrainState.DELIVER;
    }
    if (delivery.kind() == GolemDeliveryRoute.Kind.DEPOSIT) {
      depositInto(world, block, nowMillis);
    } else {
      homeInto(block, nowMillis);
    }
    if (cargo.count() > 0) {
      containerBackoff.put(chest, nowMillis + BACKOFF_MILLIS);
    }
    finishDelivery();
    return GolemBrainState.DELIVER;
  }

  private void depositInto(World world, Block block, long nowMillis) {
    NodeKey key = delivery.deposit();
    NodeBehavior behavior = NodeBehaviors.of(NodeType.DEPOSIT).orElse(null);
    GolemTaskBoard board = board(world.getUID());
    NodeRecord record = board.node(key).orElse(null);
    if (behavior == null || record == null || !context.platform().owns(block.getLocation())) {
      return;
    }
    GolemTask drop = GolemTask.builder(key, NodeType.DEPOSIT, DELIVER_TYPE, GolemTarget.block(block)).build();
    GolemContext golem = new GolemContext(this, new GolemContext.Scope(behavior.policy(), drop, record, handle.core(), nowMillis));
    GolemActResult result;
    try {
      result = Objects.requireNonNull(behavior.act(golem, drop), "GolemActor.act returned null");
    } catch (RuntimeException failure) {
      logFailure(drop, failure);
      return;
    }
    switch (result) {
      case WORKING, DONE -> board.markWorked(key, nowMillis);
      case BLOCKED -> board.markBlocked(key, nowMillis);
      case FAILED -> {
      }
    }
  }

  private void homeInto(Block block, long nowMillis) {
    GolemContext golem = new GolemContext(this, new GolemContext.Scope(context.platform().bindingPolicy(), null, null, handle.core(), nowMillis));
    Material container = block.getType();
    Inventory inventory = golem.container(block).orElse(null);
    if (inventory != null && golem.deliver(inventory, item -> !NodeContainers.nests(container, item)) > 0) {
      golem.visit(block);
    }
  }

  private boolean route(long nowMillis, Location here) {
    nextRouteAt = nowMillis + REROUTE_INTERVAL_MILLIS;
    List<ItemStack> items = cargo.items();
    if (items.isEmpty()) {
      return false;
    }
    boolean routed = routeByNode(nowMillis, items) || routeByDefault(here, items);
    routeHeld = !routed;
    return routed;
  }

  private boolean deliveringFirst(int carried) {
    WorkedNode worked = lastNode;
    if (carried <= 0 || routeHeld || worked == null) {
      return false;
    }
    NodeBehavior behavior = NodeBehaviors.of(worked.type()).orElse(null);
    return behavior != null && behavior.deliversBeforeClaiming();
  }

  private boolean routeByDefault(Location here, List<ItemStack> items) {
    World world = home.getWorld();
    GolemDeliveryRoute.Route route = GolemDeliveryRoute.choose(depositOptions(world, here, home, items), homeOption(world, items));
    delivery = switch (route.kind()) {
      case DEPOSIT -> new Delivery(GolemDeliveryRoute.Kind.DEPOSIT, route.deposit(), route.deposit().anchor());
      case HOME_CHEST -> new Delivery(GolemDeliveryRoute.Kind.HOME_CHEST, null, route.chest());
      case HOLD -> null;
    };
    arrived = false;
    stuck.reset();
    return delivery != null;
  }

  private boolean routeByNode(long nowMillis, List<ItemStack> items) {
    WorkedNode worked = lastNode;
    NodeBehavior behavior = worked == null ? null : NodeBehaviors.of(worked.type()).orElse(null);
    NodeRecord record = behavior == null ? null : board(worked.key().world()).node(worked.key()).orElse(null);
    if (record == null) {
      return false;
    }
    GolemCoreType core = GolemTaskMatch.consumer(worked.type(), cores()).orElse(handle.core());
    GolemContext golem = new GolemContext(this, new GolemContext.Scope(behavior.policy(), null, record, core, nowMillis));
    Optional<GolemTask> routed;
    try {
      routed = behavior.route(golem, worked.key(), items);
    } catch (RuntimeException failure) {
      logFailure(null, failure);
      return false;
    }
    if (routed.isEmpty() || skips(routed.get(), nowMillis)) {
      return false;
    }
    start(new Job(routed.get(), behavior, Source.ROUTE, core, worked));
    return true;
  }

  private List<GolemDeliveryRoute.DepositOption> depositOptions(World world, Location here, Location home, List<ItemStack> items) {
    NodeBehavior behavior = NodeBehaviors.of(NodeType.DEPOSIT).orElse(null);
    if (behavior == null) {
      return List.of();
    }
    Adaptation<?> policy = behavior.policy();
    int channels = runes().channels(handle.state().channels());
    List<NodeIndex.Entry> near = context.platform().nodesNear(world.getUID(), home.getX(), home.getY(), home.getZ(), workRadius());
    List<GolemDeliveryRoute.DepositOption> options = new ArrayList<>(near.size());
    for (NodeIndex.Entry entry : near) {
      NodeKey key = entry.key();
      NodeRecord record = entry.record();
      if (record.type() != NodeType.DEPOSIT || !record.owner().equals(handle.owner())
          || !GolemTaskMatch.channel(record.channel(), channels) || containerBackoff.containsKey(key.anchor())) {
        continue;
      }
      options.add(depositOption(world, here, key, record, policy, items));
    }
    return options;
  }

  private GolemDeliveryRoute.DepositOption depositOption(World world, Location here, NodeKey key, NodeRecord record, Adaptation<?> policy,
                                                         List<ItemStack> items) {
    double distanceSquared = key.distanceSquared(here.getX(), here.getY(), here.getZ());
    List<ItemStack> accepted = new ArrayList<>(items.size());
    for (ItemStack item : items) {
      if (DepositBehavior.accepts(record, item)) {
        accepted.add(item);
      }
    }
    Location at = new Location(world, key.x(), key.y(), key.z());
    if (!context.platform().owns(at)) {
      return new GolemDeliveryRoute.DepositOption(key, record.priority(), distanceSquared, false, !accepted.isEmpty(), false, false);
    }
    Block block = world.getBlockAt(key.x(), key.y(), key.z());
    boolean accessible = policy != null && policy.canAccessChest(owner, at);
    boolean active = NodeRules.active(record, block);
    boolean room = accessible && active && !accepted.isEmpty()
        && NodeContainers.inventory(block).map(inventory -> NodeContainers.fits(block.getType(), inventory, accepted)).orElse(false);
    return new GolemDeliveryRoute.DepositOption(key, record.priority(), distanceSquared, active, !accepted.isEmpty(), room, accessible);
  }

  private GolemDeliveryRoute.HomeOption homeOption(World world, List<ItemStack> items) {
    GolemState.Anchor chest = handle.state().homeChest();
    if (chest == null || !chest.world().equals(world.getUID()) || containerBackoff.containsKey(chest)) {
      return GolemDeliveryRoute.HomeOption.NONE;
    }
    Location at = new Location(world, chest.x(), chest.y(), chest.z());
    Adaptation<?> policy = context.platform().bindingPolicy();
    if (policy == null || !context.platform().owns(at)) {
      return new GolemDeliveryRoute.HomeOption(chest, false, false);
    }
    boolean accessible = policy.canAccessChest(owner, at);
    Block block = world.getBlockAt(chest.x(), chest.y(), chest.z());
    boolean room = accessible && NodeContainers.inventory(block)
        .map(inventory -> NodeContainers.fits(block.getType(), inventory, items)).orElse(false);
    return new GolemDeliveryRoute.HomeOption(chest, room, accessible);
  }

  private boolean nodeActive(World world, NodeKey key, NodeRecord record) {
    if (record.redstone() == RedstoneMode.IGNORE || !context.platform().owns(new Location(world, key.x(), key.y(), key.z()))) {
      return true;
    }
    return NodeRules.active(record, world.getBlockAt(key.x(), key.y(), key.z()));
  }

  private boolean actorStillRegistered(Job current) {
    return current.source != Source.BOARD || NodeBehaviors.of(current.task.nodeType().orElseThrow()).orElse(null) == current.actor;
  }

  private void start(Job next) {
    job = next;
    arrived = false;
    stuck.reset();
  }

  private void arrive() {
    arrived = true;
    stuck.reset();
    stuckReleases = 0;
    controller.stop();
  }

  private void keepLease(Job current, long nowMillis, GolemWorkSettings settings) {
    if (current.source == Source.BOARD && current.task.leaseRemaining(nowMillis) < settings.leaseMillis() / 2L) {
      renewBoard(current, nowMillis, settings);
    }
  }

  private void renewBoard(Job current, long nowMillis, GolemWorkSettings settings) {
    if (current.source == Source.BOARD) {
      board(current.task.nodeKey().world()).renew(current.task, handle.id(), nowMillis, settings.leaseMillis());
    }
  }

  private void markWorked(NodeKey key, long nowMillis) {
    if (key != null) {
      board(key.world()).markWorked(key, nowMillis);
    }
  }

  private void signalFull(long nowMillis) {
    if (nowMillis < nextSignalAt) {
      return;
    }
    nextSignalAt = nowMillis + FULL_SIGNAL_MILLIS;
    context.platform().signalFull(handle.entity());
  }

  private void giveUp(long nowMillis, boolean stuckRelease) {
    backOff(job.task, nowMillis);
    if (stuckRelease) {
      stuckReleases++;
    }
    dropJob();
  }

  private void backOff(GolemTask task, long nowMillis) {
    taskBackoff.put(task.key(), nowMillis + Math.round(BACKOFF_MILLIS * runes().reclaimDelayMultiplier()));
  }

  private void failed(Job current, long nowMillis) {
    switch (current.source) {
      case ROUTE -> {
        backOff(current.task, nowMillis);
        dropJob();
      }
      case CORE -> {
        backOff(current.task, nowMillis);
        closeJob();
      }
      case BOARD -> {
        if (current.actor instanceof NodeBehavior behavior && behavior.deliversBeforeClaiming()) {
          escalate(current.task, nowMillis);
          dropJob();
        } else {
          closeJob();
        }
      }
    }
  }

  private void escalate(GolemTask task, long nowMillis) {
    TaskKey key = task.key();
    Failure previous = failures.get(key);
    int count = previous == null || nowMillis - previous.at() > FAILURE_MEMORY_MILLIS ? 1 : previous.count() + 1;
    failures.put(key, new Failure(count, nowMillis));
    long delay = Math.min(MAX_BACKOFF_MILLIS, BACKOFF_MILLIS << Math.min(count - 1, 3));
    taskBackoff.put(key, nowMillis + Math.round(delay * runes().reclaimDelayMultiplier()));
  }

  private void closeJob() {
    if (job.source == Source.BOARD) {
      board(job.task.nodeKey().world()).withdraw(job.task);
    }
    lastNode = null;
    dropJob();
  }

  private void dropJob() {
    releaseClaim(job);
    job = null;
    arrived = false;
    stuck.reset();
    controller.stop();
  }

  private void releaseClaim(Job current) {
    switch (current.source) {
      case BOARD -> board(current.task.nodeKey().world()).release(current.task, handle.id());
      case CORE -> board(current.task.target().world()).releaseReservation(current.task, handle.id());
      case ROUTE -> {
      }
    }
  }

  private void pauseIfTrapped(long nowMillis, GolemWorkSettings settings) {
    if (stuckReleases < settings.stuckReleasesBeforePause()) {
      return;
    }
    stuckReleases = 0;
    long resumeAt = nowMillis + settings.stuckPauseMillis();
    nextSeekAt = Math.max(nextSeekAt, resumeAt);
    nextRouteAt = Math.max(nextRouteAt, resumeAt);
  }

  private void finishDelivery() {
    delivery = null;
    arrived = false;
    stuck.reset();
  }

  private void expireBackoff(long nowMillis) {
    if (!taskBackoff.isEmpty()) {
      taskBackoff.values().removeIf(until -> until <= nowMillis);
    }
    if (!containerBackoff.isEmpty()) {
      containerBackoff.values().removeIf(until -> until <= nowMillis);
    }
    if (!failures.isEmpty()) {
      failures.values().removeIf(failure -> nowMillis - failure.at() > FAILURE_MEMORY_MILLIS);
    }
  }

  private GolemTaskBoard board(UUID world) {
    return context.boards().apply(world);
  }

  private static WorkedNode workedNode(GolemTask task) {
    NodeKey key = task.nodeKey();
    return key == null ? null : new WorkedNode(key, task.nodeType().orElseThrow());
  }

  private long intervalMillis(int ticks) {
    double millis = Math.max(1, ticks) * TICK_MILLIS * GolemWorkSpeed.intervalMultiplier(handle);
    return Math.max(TICK_MILLIS, Math.round(millis));
  }

  private void logFailure(GolemTask failed, RuntimeException failure) {
    if (failureLogged) {
      return;
    }
    failureLogged = true;
    String what = failed == null ? "find work" : "work " + failed;
    Adapt.warn("Bound golem " + handle.id() + " failed to " + what + "; the golem keeps working.", failure);
  }

  private void logTickFailure(GolemCoreType core, RuntimeException failure) {
    if (tickFailureLogged) {
      return;
    }
    tickFailureLogged = true;
    Adapt.warn("Bound golem " + handle.id() + " failed to run its " + core.id() + " core tick; the golem keeps working.", failure);
  }

  private enum Source {
    BOARD,
    ROUTE,
    CORE
  }

  private record Delivery(GolemDeliveryRoute.Kind kind, NodeKey deposit, GolemState.Anchor chest) {
  }

  private record WorkedNode(NodeKey key, NodeType type) {
  }

  private record Failure(int count, long at) {
  }

  private static final class Job {
    private final GolemTask task;
    private final GolemActor actor;
    private final Source source;
    private final GolemCoreType core;
    private final WorkedNode node;
    private boolean reached;
    private int actIndex;

    private Job(GolemTask task, GolemActor actor, Source source, GolemCoreType core, WorkedNode node) {
      this.task = task;
      this.actor = actor;
      this.source = source;
      this.core = core;
      this.node = node;
    }

    private GolemBrainState moving() {
      return source == Source.ROUTE ? GolemBrainState.DELIVER : GolemBrainState.MOVE;
    }

    private GolemBrainState acting() {
      return source == Source.ROUTE ? GolemBrainState.DELIVER : GolemBrainState.ACT;
    }
  }
}
