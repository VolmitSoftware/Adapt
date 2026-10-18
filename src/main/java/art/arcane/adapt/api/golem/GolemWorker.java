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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
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
  private final Map<GolemTask, Long> taskBackoff = new HashMap<>();
  private final Map<GolemState.Anchor, Long> containerBackoff = new HashMap<>();
  private Player owner;
  private GolemTask task;
  private Delivery delivery;
  private boolean arrived;
  private long nextActAt;
  private long nextSeekAt;
  private long nextRouteAt;
  private long nextSignalAt;
  private boolean failureLogged;

  GolemWorker(GolemHandle handle, GolemController controller, GolemBrain.Context context) {
    this.handle = Objects.requireNonNull(handle);
    this.controller = Objects.requireNonNull(controller);
    this.context = Objects.requireNonNull(context);
    this.cargo = new GolemCargo(handle);
  }

  GolemBrainState pulse(long nowMillis, Location here, Player currentOwner, Location home, GolemBrainSettings brain) {
    owner = currentOwner;
    if (!canWork(here, home, brain)) {
      abandon();
      return null;
    }
    expireBackoff(nowMillis);
    GolemWorkSettings settings = context.work().get();
    if (task != null) {
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
    boolean full = carried > 0 && carried >= GolemCapacity.carry(handle);
    if (!full && nowMillis >= nextSeekAt) {
      nextSeekAt = nowMillis + SEEK_INTERVAL_MILLIS;
      if (claim(nowMillis, here, home, settings)) {
        return pursue(nowMillis, here, settings);
      }
    }
    if (carried > 0 && nowMillis >= nextRouteAt && route(nowMillis, here, home, settings)) {
      return deliver(nowMillis, here, settings);
    }
    if (full) {
      signalFull(nowMillis);
    }
    return null;
  }

  void abandon() {
    if (task != null) {
      context.boards().apply(task.node().world()).release(task, handle.id());
    }
    task = null;
    delivery = null;
    arrived = false;
    stuck.reset();
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

  boolean renew(GolemTask leased, long nowMillis) {
    return context.boards().apply(leased.node().world()).renew(leased, handle.id(), nowMillis, context.work().get().leaseMillis());
  }

  private boolean canWork(Location here, Location home, GolemBrainSettings brain) {
    if (owner == null || home == null || here.getWorld() == null || !GolemWorkGate.canWork(handle)
        || !GolemancyRuntime.settings().allows(handle.body(), handle.core())) {
      return false;
    }
    return here.distanceSquared(home) <= brain.leashRadius() * brain.leashRadius();
  }

  private GolemBrainState pursue(long nowMillis, Location here, GolemWorkSettings settings) {
    GolemTaskBoard board = context.boards().apply(task.node().world());
    UUID golemId = handle.id();
    if (!task.heldBy(golemId, nowMillis)) {
      dropTask();
      return null;
    }
    NodeRecord record = board.node(task.node()).orElse(null);
    if (record == null) {
      board.withdraw(task);
      dropTask();
      return null;
    }
    World world = here.getWorld();
    if (!world.getUID().equals(task.node().world())) {
      releaseTask(board, nowMillis);
      return null;
    }
    Location target = context.platform().targetLocation(task.target(), world);
    double distance = here.distance(target);
    if (distance > REACH) {
      arrived = false;
      GolemStuckWatch.Progress progress = stuck.track(nowMillis, distance, settings.stuckMillis());
      if (progress == GolemStuckWatch.Progress.STUCK) {
        releaseTask(board, nowMillis);
        return null;
      }
      if (progress == GolemStuckWatch.Progress.IMPROVED) {
        board.renew(task, golemId, nowMillis, settings.leaseMillis());
      }
      controller.moveTo(target, MOVE_SPEED);
      return GolemBrainState.MOVE;
    }
    if (!arrived) {
      arrived = true;
      controller.stop();
      nextActAt = nowMillis;
    }
    if (nowMillis < nextActAt) {
      return GolemBrainState.ACT;
    }
    NodeBehavior behavior = NodeBehaviors.of(task.nodeType()).orElse(null);
    if (behavior == null || !context.platform().owns(target)) {
      releaseTask(board, nowMillis);
      return GolemBrainState.ACT;
    }
    act(board, behavior, record, nowMillis, settings);
    int carried = cargo.count();
    if (task != null && carried > 0 && carried >= GolemCapacity.carry(handle)) {
      board.release(task, golemId);
      dropTask();
    }
    return GolemBrainState.ACT;
  }

  private void act(GolemTaskBoard board, NodeBehavior behavior, NodeRecord record, long nowMillis, GolemWorkSettings settings) {
    GolemTask current = task;
    GolemContext golem = new GolemContext(this, behavior.policy(), current, record, nowMillis);
    NodeBehavior.Result result;
    try {
      result = Objects.requireNonNull(behavior.act(golem, current), "NodeBehavior.act returned null");
    } catch (RuntimeException failure) {
      logFailure(current, failure);
      releaseTask(board, nowMillis);
      return;
    }
    switch (result) {
      case WORKING -> {
        board.renew(current, handle.id(), nowMillis, settings.leaseMillis());
        board.markWorked(current.node(), nowMillis);
        nextActAt = nowMillis + intervalMillis(behavior.actIntervalTicks(golem, current));
      }
      case DONE -> {
        board.complete(current, handle.id());
        board.markWorked(current.node(), nowMillis);
        handle.addTasks(1L);
        dropTask();
      }
      case FAILED -> {
        board.withdraw(current);
        dropTask();
      }
      case BLOCKED -> {
        board.markBlocked(current.node(), nowMillis);
        releaseTask(board, nowMillis);
      }
    }
  }

  private boolean claim(long nowMillis, Location here, Location home, GolemWorkSettings settings) {
    GolemTaskBoard board = context.boards().apply(home.getWorld().getUID());
    GolemState state = handle.state();
    GolemTaskBoard.Seeker seeker = new GolemTaskBoard.Seeker(handle.id(), handle.owner(), state.cores(), handle.body(), state.channels(),
        home.getX(), home.getY(), home.getZ(), settings.baseWorkRadius(), here.getX(), here.getY(), here.getZ());
    Optional<GolemTask> claimed = board.claim(seeker, taskBackoff::containsKey, nowMillis, settings.leaseMillis());
    if (claimed.isEmpty()) {
      return false;
    }
    task = claimed.get();
    arrived = false;
    stuck.reset();
    return true;
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
        finishDelivery();
        controller.stop();
        return null;
      }
      controller.moveTo(target, MOVE_SPEED);
      return GolemBrainState.DELIVER;
    }
    Block block = world.getBlockAt(chest.x(), chest.y(), chest.z());
    if (!arrived) {
      arrived = true;
      controller.stop();
      long interval = intervalMillis(NodeBehavior.DEFAULT_ACT_INTERVAL_TICKS);
      controller.interact(block, (int) Math.max(1L, interval / TICK_MILLIS));
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
    GolemTaskBoard board = context.boards().apply(world.getUID());
    NodeRecord record = board.node(key).orElse(null);
    if (behavior == null || record == null || !context.platform().owns(block.getLocation())) {
      return;
    }
    GolemTask drop = GolemTask.builder(key, NodeType.DEPOSIT, DELIVER_TYPE, GolemTarget.block(block)).build();
    GolemContext golem = new GolemContext(this, behavior.policy(), drop, record, nowMillis);
    NodeBehavior.Result result;
    try {
      result = Objects.requireNonNull(behavior.act(golem, drop), "NodeBehavior.act returned null");
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
    GolemContext golem = new GolemContext(this, context.platform().bindingPolicy(), null, null, nowMillis);
    golem.container(block).ifPresent(inventory -> golem.deliver(inventory, item -> true));
  }

  private boolean route(long nowMillis, Location here, Location home, GolemWorkSettings settings) {
    nextRouteAt = nowMillis + REROUTE_INTERVAL_MILLIS;
    List<ItemStack> items = cargo.items();
    if (items.isEmpty()) {
      return false;
    }
    World world = home.getWorld();
    GolemDeliveryRoute.Route route = GolemDeliveryRoute.choose(depositOptions(world, here, home, items, settings), homeOption(world, items));
    delivery = switch (route.kind()) {
      case DEPOSIT -> new Delivery(GolemDeliveryRoute.Kind.DEPOSIT, route.deposit(), route.deposit().anchor());
      case HOME_CHEST -> new Delivery(GolemDeliveryRoute.Kind.HOME_CHEST, null, route.chest());
      case HOLD -> null;
    };
    arrived = false;
    stuck.reset();
    return delivery != null;
  }

  private List<GolemDeliveryRoute.DepositOption> depositOptions(World world, Location here, Location home, List<ItemStack> items,
                                                                GolemWorkSettings settings) {
    NodeBehavior behavior = NodeBehaviors.of(NodeType.DEPOSIT).orElse(null);
    if (behavior == null) {
      return List.of();
    }
    Adaptation<?> policy = behavior.policy();
    int channels = handle.state().channels();
    List<NodeIndex.Entry> near = context.platform().nodesNear(world.getUID(), home.getX(), home.getY(), home.getZ(), settings.baseWorkRadius());
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
    boolean active = DepositBehavior.active(record, block);
    boolean room = accessible && active && !accepted.isEmpty()
        && NodeContainers.inventory(block).map(inventory -> NodeContainers.fits(inventory, accepted)).orElse(false);
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
    boolean room = accessible && NodeContainers.inventory(world.getBlockAt(chest.x(), chest.y(), chest.z()))
        .map(inventory -> NodeContainers.fits(inventory, items)).orElse(false);
    return new GolemDeliveryRoute.HomeOption(chest, room, accessible);
  }

  private void signalFull(long nowMillis) {
    if (nowMillis < nextSignalAt) {
      return;
    }
    nextSignalAt = nowMillis + FULL_SIGNAL_MILLIS;
    context.platform().signalFull(handle.entity());
  }

  private void releaseTask(GolemTaskBoard board, long nowMillis) {
    board.release(task, handle.id());
    taskBackoff.put(task, nowMillis + BACKOFF_MILLIS);
    dropTask();
  }

  private void dropTask() {
    task = null;
    arrived = false;
    stuck.reset();
    controller.stop();
  }

  private void finishDelivery() {
    delivery = null;
    arrived = false;
    stuck.reset();
  }

  private void expireBackoff(long nowMillis) {
    if (!taskBackoff.isEmpty()) {
      taskBackoff.entrySet().removeIf(entry -> entry.getValue() <= nowMillis || entry.getKey().isClosed());
    }
    if (!containerBackoff.isEmpty()) {
      containerBackoff.values().removeIf(until -> until <= nowMillis);
    }
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
    Adapt.warn("Bound golem " + handle.id() + " failed to work " + failed + "; the task is released and the golem keeps working.", failure);
  }

  private record Delivery(GolemDeliveryRoute.Kind kind, NodeKey deposit, GolemState.Anchor chest) {
  }
}
