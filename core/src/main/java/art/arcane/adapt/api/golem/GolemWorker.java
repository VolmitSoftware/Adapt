package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptLog;
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
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class GolemWorker {
  static final double REACH = 2.5D;
  static final long BACKOFF_MILLIS = 10_000L;
  static final long MAX_BACKOFF_MILLIS = 60_000L;
  static final long FAILURE_MEMORY_MILLIS = 120_000L;
  static final long FULL_SIGNAL_MILLIS = 3_000L;
  static final long SEEK_INTERVAL_MILLIS = 1_000L;
  static final long REROUTE_INTERVAL_MILLIS = 3_000L;
  static final long TOOL_MISS_MILLIS = 30_000L;
  static final int MAX_TOOL_SKIPS = 8;
  static final String DELIVER_TYPE = "deliver";

  private static final double MOVE_SPEED = 1D;
  private static final long TICK_MILLIS = 50L;

  private final GolemHandle handle;
  private final GolemController controller;
  private final GolemBrain.Context context;
  private final GolemCargo cargo;
  private final GolemStuckWatch stuck = new GolemStuckWatch();
  private final GolemHeldTool held;
  private final Map<String, Long> toolMisses = new HashMap<>();
  private final Map<TaskKey, Long> toolBackoff = new HashMap<>();
  private final Map<TaskKey, Long> taskBackoff = new HashMap<>();
  private final Map<TaskKey, Failure> failures = new HashMap<>();
  private final Map<GolemAnchor, Long> containerBackoff = new HashMap<>();
  private PlayerRef owner;
  private Loc home;
  private Job job;
  private Delivery delivery;
  private Fetch fetch;
  private GolemAnchor toolChest;
  private WorkedNode lastNode;
  private boolean arrived;
  private boolean routeHeld;
  private boolean toolMissing;
  private int stuckReleases;
  private int pulseCapacity = -1;
  private GolemRuneEffects pulseRunes;
  private List<GolemCoreType> pulseCores;
  private long nextActAt;
  private long nextSeekAt;
  private long nextRouteAt;
  private long nextSignalAt;
  private long pausedUntil;
  private boolean failureLogged;
  private boolean tickFailureLogged;

  GolemWorker(GolemHandle handle, GolemController controller, GolemBrain.Context context) {
    this.handle = Objects.requireNonNull(handle);
    this.controller = Objects.requireNonNull(controller);
    this.context = Objects.requireNonNull(context);
    this.cargo = new GolemCargo(handle);
    this.held = new GolemHeldTool(handle);
  }

  GolemBrainState pulse(long nowMillis, Loc here, PlayerRef currentOwner, Loc currentHome, GolemBrainSettings brain) {
    owner = currentOwner;
    home = currentHome;
    pulseCapacity = -1;
    pulseRunes = null;
    pulseCores = null;
    held.sync();
    handle.setWorkStatus(GolemWorkStatus.WAITING);
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
    if (fetch != null) {
      GolemBrainState fetching = fetchTool(nowMillis, here, settings);
      if (fetching != null) {
        return fetching;
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
        return pursueClaimed(nowMillis, here, settings);
      }
    }
    if (carried > 0 && nowMillis >= nextRouteAt && route(nowMillis, here)) {
      return job != null ? pursue(nowMillis, here, settings) : deliver(nowMillis, here, settings);
    }
    if (full) {
      signalFull(nowMillis);
    }
    if (nowMillis < pausedUntil) {
      status(GolemWorkStatus.STUCK_PAUSE);
    } else if (toolMissing || !toolBackoff.isEmpty()) {
      status(GolemWorkStatus.NEEDS_TOOL);
    } else if (carried > 0 && routeHeld) {
      status(GolemWorkStatus.NO_OUTPUT);
    } else if (cores().stream().noneMatch(core -> GolemMastery.coreUnlocked(handle.owner(), core))) {
      status(GolemWorkStatus.CORE_UNLEARNED);
    } else if (!taskBackoff.isEmpty()) {
      status(GolemWorkStatus.NO_REACHABLE_WORK);
    }
    return null;
  }

  void abandon() {
    if (job != null) {
      releaseClaim(job);
    }
    job = null;
    delivery = null;
    fetch = null;
    arrived = false;
    stuck.reset();
  }

  void forgetWorkedNode() {
    lastNode = null;
  }

  GolemHandle handle() {
    return handle;
  }

  PlayerRef owner() {
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

  Loc home() {
    return home;
  }

  double workRadius() {
    return context.work().get().baseWorkRadius() + runes().radiusBonus();
  }

  boolean inWorkRadius(UUID world, double x, double y, double z) {
    Loc center = home;
    WorldRef centerWorld = center == null ? null : center.world();
    if (centerWorld == null || !centerWorld.uid().equals(world)) {
      return false;
    }
    double radius = workRadius();
    double dx = x - center.x();
    double dy = y - center.y();
    double dz = z - center.z();
    return dx * dx + dy * dy + dz * dz <= radius * radius;
  }

  int actIndex() {
    return job == null ? 0 : job.actIndex;
  }

  Optional<ItemRef> heldTool() {
    return held.item();
  }

  Optional<ItemRef> toolInUse() {
    Job current = job;
    return current == null || current.tool == null ? Optional.empty() : Optional.of(current.tool.copy());
  }

  void wearTool() {
    held.wear(() -> ThreadLocalRandom.current().nextDouble());
  }

  void interact(BlockRef container, int ticks) {
    if (!context.platform().owns(container.location())) {
      return;
    }
    controller.interact(container, ticks);
    context.platform().visitLid(container, ticks + ContainerLids.CLOSE_TICKS);
  }

  boolean skips(GolemTask task, long nowMillis) {
    return board(task.target().world()).blocked(task, nowMillis) || taskBackoff.containsKey(task.key()) || toolBackoff.containsKey(task.key())
        || board(task.target().world()).reservedByOther(task, handle.id(), nowMillis);
  }

  boolean renew(GolemTask leased, long nowMillis) {
    Job current = job;
    if (current == null || current.task != leased || !validNode(current)) {
      return false;
    }
    return switch (current.source) {
      case BOARD -> board(leased.nodeKey().world()).renew(leased, handle.id(), nowMillis, context.work().get().leaseMillis());
      case CORE -> keepReservation(leased, nowMillis);
      case ROUTE -> false;
    };
  }

  private boolean canWork(Loc here, GolemBrainSettings brain) {
    if (owner == null || home == null || here.world() == null || !GolemWorkGate.canWork(handle)
        || !GolemancyRuntime.settings().allows(handle.body(), handle.core())) {
      return false;
    }
    return here.distanceSquared(home) <= brain.leashRadius() * brain.leashRadius();
  }

  private GolemBrainState pursueClaimed(long nowMillis, Loc here, GolemWorkSettings settings) {
    for (int skipped = 0; ; skipped++) {
      toolMissing = false;
      GolemBrainState pursuing = pursue(nowMillis, here, settings);
      if (pursuing != null) {
        return pursuing;
      }
      if (fetch != null) {
        return fetchTool(nowMillis, here, settings);
      }
      if (!toolMissing || skipped >= MAX_TOOL_SKIPS || !(claim(nowMillis, here, settings) || seekCores(nowMillis))) {
        return null;
      }
    }
  }

  private GolemBrainState pursue(long nowMillis, Loc here, GolemWorkSettings settings) {
    Job current = job;
    GolemTask task = current.task;
    WorldRef world = here.world();
    if (!validNode(current) || board(task.target().world()).blocked(task, nowMillis)) {
      dropJob();
      nextSeekAt = nowMillis;
      return null;
    }
    if ((current.source == Source.BOARD && !task.heldBy(handle.id(), nowMillis))
        || (current.source == Source.CORE && !keepReservation(task, nowMillis))) {
      dropJob();
      return null;
    }
    NodeKey key = current.node == null ? null : current.node.key();
    NodeRecord record = null;
    if (key != null) {
      if (!world.uid().equals(key.world())) {
        giveUp(nowMillis, false);
        return null;
      }
      record = board(key.world()).node(key).orElse(null);
      if (record == null) {
        closeJob();
        return null;
      }
    }
    if (!world.uid().equals(task.target().world())) {
      giveUp(nowMillis, false);
      return null;
    }
    if (!current.toolsChecked) {
      current.toolsChecked = true;
      if (!readyTools(current, record, nowMillis)) {
        return null;
      }
    }
    Loc target = context.platform().targetLocation(task.target(), world);
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
      status(current.source == Source.ROUTE ? GolemWorkStatus.DELIVERING : GolemWorkStatus.MOVING);
      return current.moving();
    }
    status(current.source == Source.ROUTE ? GolemWorkStatus.DELIVERING : GolemWorkStatus.WORKING);
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
    if (!readyTools(current, record, nowMillis)) {
      return null;
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
        double speed = GolemMastery.coreSpeed(handle, current.core);
        nextActAt = nowMillis + Math.round(intervalMillis(current.actor.actIntervalTicks(golem, task)) / speed);
      }
      case DONE -> {
        if (current.source == Source.BOARD) {
          board(key.world()).complete(task, handle.id());
          failures.remove(task.key());
        }
        markWorked(key, nowMillis);
        if (current.source != Source.ROUTE) {
          handle.addTasks(1L);
          GolemWorkStats.completed(handle, task);
        } else {
          nextRouteAt = nowMillis;
        }
        dropJob();
      }
      case FAILED -> failed(current, nowMillis);
      case BLOCKED -> {
        board(task.target().world()).block(task, nowMillis);
        if (key != null) {
          board(key.world()).markBlocked(key, nowMillis);
        }
        giveUp(nowMillis, false);
      }
    }
  }

  private boolean claim(long nowMillis, Loc here, GolemWorkSettings settings) {
    GolemTaskBoard board = board(home.world().uid());
    Optional<GolemTask> claimed = board.claim(seeker(here), candidate -> skips(candidate, nowMillis), nowMillis, settings.leaseMillis());
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
    GolemCoreType consumer = GolemTaskMatch.consumer(nodeType, cores()).orElse(handle.core());
    start(new Job(task, behavior, Source.BOARD, consumer, new WorkedNode(task.nodeKey(), nodeType)));
    return true;
  }

  private GolemTaskBoard.Seeker seeker(Loc here) {
    return new GolemTaskBoard.Seeker(handle.id(), handle.owner(), cores(), handle.body(), runes().channels(handle.state().channels()),
        home.x(), home.y(), home.z(), workRadius(), here.x(), here.y(), here.z());
  }

  /**
   * Acts once, where the golem stands, on the next open task of the same job as {@code last}; the task is not counted
   * as a finished job. A partly done task goes back to the board.
   */
  boolean repeat(GolemTask last, long nowMillis, PlayerRef currentOwner, Loc currentHome) {
    owner = currentOwner;
    home = currentHome;
    pulseCapacity = -1;
    pulseRunes = null;
    pulseCores = null;
    GolemTask busy = job == null ? null : job.task;
    Optional<NodeType> nodeType = last.nodeType();
    if (nodeType.isPresent()) {
      return repeatBoard(last, nodeType.get(), busy, nowMillis);
    }
    return repeatCore(last, busy, nowMillis);
  }

  private boolean repeatBoard(GolemTask last, NodeType nodeType, GolemTask busy, long nowMillis) {
    NodeBehavior behavior = NodeBehaviors.of(nodeType).orElse(null);
    if (behavior == null) {
      return false;
    }
    GolemTaskBoard board = board(home.world().uid());
    Optional<GolemTask> claimed = board.claim(seeker(handle.entity().location()),
        candidate -> candidate == busy || !sameJob(last, candidate) || skips(candidate, nowMillis), nowMillis, context.work().get().leaseMillis());
    if (claimed.isEmpty()) {
      return false;
    }
    GolemTask task = claimed.get();
    NodeKey key = task.nodeKey();
    GolemCoreType consumer = GolemTaskMatch.consumer(nodeType, cores()).orElse(handle.core());
    GolemActResult result = actOnce(behavior, task, board.node(key).orElse(null), consumer, nowMillis);
    if (result == GolemActResult.DONE) {
      board.complete(task, handle.id());
    } else {
      board.release(task, handle.id());
    }
    if (result == GolemActResult.DONE || result == GolemActResult.WORKING) {
      markWorked(key, nowMillis);
      return true;
    }
    return false;
  }

  private boolean repeatCore(GolemTask last, GolemTask busy, long nowMillis) {
    for (GolemCoreType core : cores()) {
      GolemCoreBehavior behavior = GolemCoreBehaviors.of(core).orElse(null);
      if (behavior == null) {
        continue;
      }
      Optional<GolemTask> found;
      try {
        found = behavior.seek(new GolemContext(this, new GolemContext.Scope(behavior.policy(), null, null, core, nowMillis)));
      } catch (RuntimeException failure) {
        logFailure(null, failure);
        continue;
      }
      GolemTask task = found.orElse(null);
      if (task == null || task == busy || !sameJob(last, task) || !reserveCoreJob(task, nowMillis)) {
        continue;
      }
      GolemActResult result = actOnce(behavior, task, null, core, nowMillis);
      board(task.target().world()).releaseReservation(task, handle.id());
      return result == GolemActResult.DONE || result == GolemActResult.WORKING;
    }
    return false;
  }

  private GolemActResult actOnce(GolemActor actor, GolemTask task, NodeRecord record, GolemCoreType core, long nowMillis) {
    try {
      return Objects.requireNonNull(actor.act(new GolemContext(this, new GolemContext.Scope(actor.policy(), task, record, core, nowMillis)), task),
          "GolemActor.act returned null");
    } catch (RuntimeException failure) {
      logFailure(task, failure);
      return GolemActResult.FAILED;
    }
  }

  private static boolean sameJob(GolemTask last, GolemTask candidate) {
    return last.type().equals(candidate.type()) && last.nodeType().equals(candidate.nodeType());
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

  private GolemBrainState deliver(long nowMillis, Loc here, GolemWorkSettings settings) {
    status(GolemWorkStatus.DELIVERING);
    WorldRef world = here.world();
    GolemAnchor chest = delivery.chest();
    if (!world.uid().equals(chest.world())) {
      finishDelivery();
      return null;
    }
    Loc target = new Loc(world, chest.x() + 0.5D, chest.y() + 0.5D, chest.z() + 0.5D);
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
    BlockRef block = world.blockAt(chest.x(), chest.y(), chest.z());
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

  private void depositInto(WorldRef world, BlockRef block, long nowMillis) {
    NodeKey key = delivery.deposit();
    NodeBehavior behavior = NodeBehaviors.of(NodeType.DEPOSIT).orElse(null);
    GolemTaskBoard board = board(world.uid());
    NodeRecord record = board.node(key).orElse(null);
    if (behavior == null || record == null || !context.platform().owns(block.location())) {
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

  private void homeInto(BlockRef block, long nowMillis) {
    GolemContext golem = new GolemContext(this, new GolemContext.Scope(context.platform().bindingPolicy(), null, null, handle.core(), nowMillis));
    Id container = block.type();
    InventoryRef inventory = golem.container(block).orElse(null);
    if (inventory != null && golem.deliver(inventory, item -> !NodeContainers.nests(container, item)) > 0) {
      golem.visit(block);
    }
  }

  private boolean readyTools(Job current, NodeRecord record, long nowMillis) {
    GolemContext golem = new GolemContext(this, new GolemContext.Scope(current.actor.policy(), current.task, record, current.core, nowMillis));
    GolemToolPlan plan;
    try {
      plan = Objects.requireNonNull(current.actor.tools(golem, current.task), "GolemActor.tools returned null");
    } catch (RuntimeException failure) {
      logFailure(current.task, failure);
      giveUp(nowMillis, false);
      return false;
    }
    ItemRef tool = held.item().orElse(null);
    GolemToolRules.Choice choice = GolemToolRules.choose(plan, tool, need -> fetchable(need, nowMillis));
    if (!plan.needs().isEmpty()) {
      handle.setToolNote(choice.note());
    }
    switch (choice.kind()) {
      case HELD -> current.tool = tool;
      case FREE -> current.tool = null;
      case FETCH -> {
        fetch = new Fetch(handle.state().homeChest(), plan.needs().get(choice.need()));
        dropJob();
        return false;
      }
      case MISSING -> {
        status(GolemWorkStatus.NEEDS_TOOL);
        toolBackoff.put(current.task.key(), nowMillis + TOOL_MISS_MILLIS);
        toolChest = handle.state().homeChest();
        toolMissing = true;
        dropJob();
        return false;
      }
    }
    return true;
  }

  private boolean fetchable(GolemToolNeed need, long nowMillis) {
    if (handle.body() == GolemBody.BONE) {
      return false;
    }
    GolemAnchor chest = handle.state().homeChest();
    WorldRef world = home == null ? null : home.world();
    Long missedUntil = toolMisses.get(need.id());
    if (chest == null || world == null || !world.uid().equals(chest.world()) || (missedUntil != null && missedUntil > nowMillis)
        || containerBackoff.containsKey(chest)) {
      return false;
    }
    Loc at = new Loc(world, chest.x(), chest.y(), chest.z());
    if (!context.platform().owns(at)) {
      return true;
    }
    Optional<InventoryRef> inventory = containerContext(nowMillis).container(world.blockAt(chest.x(), chest.y(), chest.z()));
    if (inventory.isPresent() && GolemToolRules.slot(slots(inventory.get()), need) >= 0) {
      return true;
    }
    missTool(need, nowMillis);
    return false;
  }

  private GolemBrainState fetchTool(long nowMillis, Loc here, GolemWorkSettings settings) {
    status(GolemWorkStatus.FETCHING_TOOL);
    WorldRef world = here.world();
    GolemAnchor chest = fetch.chest();
    if (chest == null || !world.uid().equals(chest.world())) {
      missTool(fetch.need(), nowMillis);
      finishFetch();
      return null;
    }
    Loc target = new Loc(world, chest.x() + 0.5D, chest.y() + 0.5D, chest.z() + 0.5D);
    double distance = here.distance(target);
    if (distance > REACH) {
      arrived = false;
      if (stuck.track(nowMillis, distance, settings.stuckMillis()) == GolemStuckWatch.Progress.STUCK) {
        containerBackoff.put(chest, nowMillis + BACKOFF_MILLIS);
        missTool(fetch.need(), nowMillis);
        stuckReleases++;
        finishFetch();
        controller.stop();
        pauseIfTrapped(nowMillis, settings);
        return null;
      }
      controller.moveTo(target, MOVE_SPEED);
      return GolemBrainState.DELIVER;
    }
    BlockRef block = world.blockAt(chest.x(), chest.y(), chest.z());
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
    takeTool(block, fetch.need(), nowMillis);
    finishFetch();
    nextSeekAt = nowMillis;
    return GolemBrainState.DELIVER;
  }

  private void takeTool(BlockRef block, GolemToolNeed need, long nowMillis) {
    GolemContext golem = containerContext(nowMillis);
    InventoryRef inventory = golem.container(block).orElse(null);
    int slot = inventory == null ? -1 : GolemToolRules.slot(slots(inventory), need);
    if (slot < 0) {
      missTool(need, nowMillis);
      return;
    }
    ItemRef taken = inventory.item(slot).copy();
    inventory.setItem(slot, held.item().orElse(null));
    if (!held.hold(taken)) {
      inventory.setItem(slot, taken);
      return;
    }
    golem.visit(block);
    toolMisses.remove(need.id());
  }

  private void missTool(GolemToolNeed need, long nowMillis) {
    toolMisses.put(need.id(), nowMillis + TOOL_MISS_MILLIS);
    toolChest = handle.state().homeChest();
  }

  private static ItemRef[] slots(InventoryRef inventory) {
    return inventory.contents().toArray(ItemRef[]::new);
  }

  private GolemContext containerContext(long nowMillis) {
    return new GolemContext(this, new GolemContext.Scope(context.platform().bindingPolicy(), null, null, handle.core(), nowMillis));
  }

  private boolean route(long nowMillis, Loc here) {
    nextRouteAt = nowMillis + REROUTE_INTERVAL_MILLIS;
    List<ItemRef> items = cargo.items();
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

  private boolean routeByDefault(Loc here, List<ItemRef> items) {
    WorldRef world = home.world();
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

  private boolean routeByNode(long nowMillis, List<ItemRef> items) {
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

  private List<GolemDeliveryRoute.DepositOption> depositOptions(WorldRef world, Loc here, Loc home, List<ItemRef> items) {
    NodeBehavior behavior = NodeBehaviors.of(NodeType.DEPOSIT).orElse(null);
    if (behavior == null) {
      return List.of();
    }
    Adaptation<?> policy = behavior.policy();
    int channels = runes().channels(handle.state().channels());
    List<NodeIndex.Entry> near = context.platform().nodesNear(world.uid(), home.x(), home.y(), home.z(), workRadius());
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

  private GolemDeliveryRoute.DepositOption depositOption(WorldRef world, Loc here, NodeKey key, NodeRecord record, Adaptation<?> policy,
                                                         List<ItemRef> items) {
    double distanceSquared = key.distanceSquared(here.x(), here.y(), here.z());
    List<ItemRef> accepted = new ArrayList<>(items.size());
    for (ItemRef item : items) {
      if (DepositBehavior.accepts(record, item)) {
        accepted.add(item);
      }
    }
    Loc at = new Loc(world, key.x(), key.y(), key.z());
    if (!context.platform().owns(at)) {
      return new GolemDeliveryRoute.DepositOption(key, record.priority(), distanceSquared, false, !accepted.isEmpty(), false, false);
    }
    BlockRef block = world.blockAt(key.x(), key.y(), key.z());
    boolean accessible = policy != null && policy.canAccessChest(owner, at);
    boolean active = NodeRules.active(record, block);
    boolean room = accessible && active && !accepted.isEmpty()
        && NodeContainers.inventory(block).map(inventory -> NodeContainers.fits(block.type(), inventory, accepted)).orElse(false);
    return new GolemDeliveryRoute.DepositOption(key, record.priority(), distanceSquared, active, !accepted.isEmpty(), room, accessible);
  }

  private GolemDeliveryRoute.HomeOption homeOption(WorldRef world, List<ItemRef> items) {
    GolemAnchor chest = handle.state().homeChest();
    if (chest == null || !chest.world().equals(world.uid()) || containerBackoff.containsKey(chest)) {
      return GolemDeliveryRoute.HomeOption.NONE;
    }
    Loc at = new Loc(world, chest.x(), chest.y(), chest.z());
    Adaptation<?> policy = context.platform().bindingPolicy();
    if (policy == null || !context.platform().owns(at)) {
      return new GolemDeliveryRoute.HomeOption(chest, false, false);
    }
    boolean accessible = policy.canAccessChest(owner, at);
    BlockRef block = world.blockAt(chest.x(), chest.y(), chest.z());
    boolean room = accessible && NodeContainers.inventory(block)
        .map(inventory -> NodeContainers.fits(block.type(), inventory, items)).orElse(false);
    return new GolemDeliveryRoute.HomeOption(chest, room, accessible);
  }

  private boolean nodeActive(WorldRef world, NodeKey key, NodeRecord record) {
    if (record.redstone() == RedstoneMode.IGNORE || !context.platform().owns(new Loc(world, key.x(), key.y(), key.z()))) {
      return true;
    }
    return NodeRules.active(record, world.blockAt(key.x(), key.y(), key.z()));
  }

  private boolean actorStillRegistered(Job current) {
    return current.source != Source.BOARD || NodeBehaviors.of(current.task.nodeType().orElseThrow()).orElse(null) == current.actor;
  }

  private void start(Job next) {
    if (next.node != null) {
      next.record = board(next.node.key().world()).node(next.node.key()).orElse(null);
    }
    job = next;
    arrived = false;
    stuck.reset();
  }

  private boolean validNode(Job current) {
    if (current.node == null) {
      return true;
    }
    NodeRecord record = board(current.node.key().world()).node(current.node.key()).orElse(null);
    return record != null && Objects.equals(current.record, record) && record.owner().equals(handle.owner())
        && GolemTaskMatch.channel(record.channel(), runes().channels(handle.state().channels()))
        && GolemTaskMatch.core(record.type(), cores())
        && inWorkRadius(current.task.target().world(), current.task.target().x(), current.task.target().y(), current.task.target().z());
  }

  void status(GolemWorkStatus next) {
    handle.setWorkStatus(next);

  }

  private void arrive() {
    arrived = true;
    stuck.reset();
    stuckReleases = 0;
    controller.stop();
  }

  private void keepLease(Job current, long nowMillis, GolemWorkSettings settings) {
    if (validNode(current) && current.source == Source.BOARD && current.task.leaseRemaining(nowMillis) < settings.leaseMillis() / 2L) {
      renewBoard(current, nowMillis, settings);
    }
  }

  private void renewBoard(Job current, long nowMillis, GolemWorkSettings settings) {
    if (validNode(current) && current.source == Source.BOARD) {
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
    pausedUntil = resumeAt;
    status(GolemWorkStatus.STUCK_PAUSE);
    nextSeekAt = Math.max(nextSeekAt, resumeAt);
    nextRouteAt = Math.max(nextRouteAt, resumeAt);
  }

  private void finishDelivery() {
    delivery = null;
    arrived = false;
    stuck.reset();
  }

  private void finishFetch() {
    fetch = null;
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
    GolemAnchor chest = handle.state().homeChest();
    if (!Objects.equals(chest, toolChest)) {
      toolChest = chest;
      toolMisses.clear();
      toolBackoff.clear();
    }
    if (!toolMisses.isEmpty()) {
      toolMisses.values().removeIf(until -> until <= nowMillis);
    }
    if (!toolBackoff.isEmpty()) {
      toolBackoff.values().removeIf(until -> until <= nowMillis);
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
    AdaptLog.warn("Bound golem " + handle.id() + " failed to " + what + "; the golem keeps working.", failure);
  }

  private void logTickFailure(GolemCoreType core, RuntimeException failure) {
    if (tickFailureLogged) {
      return;
    }
    tickFailureLogged = true;
    AdaptLog.warn("Bound golem " + handle.id() + " failed to run its " + core.id() + " core tick; the golem keeps working.", failure);
  }

  private enum Source {
    BOARD,
    ROUTE,
    CORE
  }

  private record Delivery(GolemDeliveryRoute.Kind kind, NodeKey deposit, GolemAnchor chest) {
  }

  private record Fetch(GolemAnchor chest, GolemToolNeed need) {
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
    private boolean toolsChecked;
    private ItemRef tool;
    private NodeRecord record;
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
