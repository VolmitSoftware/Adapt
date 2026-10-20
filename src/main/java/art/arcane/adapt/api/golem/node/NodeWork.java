package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskBoard;
import art.arcane.adapt.api.golem.GolemTaskBoards;
import art.arcane.adapt.api.golem.NodeType;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class NodeWork implements NodeScanHook {
  private static final Set<NodeType> FAILURES_LOGGED = ConcurrentHashMap.newKeySet();

  private final NodePlatform platform;
  private final GolemTaskBoards boards;
  private final BiConsumer<NodeKey, NodeStatus> status;

  NodeWork(NodePlatform platform, GolemTaskBoards boards, BiConsumer<NodeKey, NodeStatus> status) {
    this.platform = Objects.requireNonNull(platform);
    this.boards = Objects.requireNonNull(boards);
    this.status = Objects.requireNonNull(status);
  }

  public static NodeWork create(GolemTaskBoards boards, NodeService nodes) {
    return new NodeWork(new BukkitNodePlatform(), boards, nodes::status);
  }

  @Override
  public void scan(NodeScanPass pass) {
    if (!pass.current().getAsBoolean()) {
      return;
    }
    NodeRecord record = pass.record();
    NodeBehavior behavior = NodeBehaviors.of(record.type()).orElse(null);
    if (behavior == null) {
      return;
    }
    NodeKey key = pass.key();
    GolemTaskBoard board = boards.board(key.world());
    long now = platform.nowMillis();
    board.purgeIfDue(now);
    NodeScanCursor.Window window = pass.window();
    ScanBudget budget = new ScanBudget(window, pass.maxOpenTasks() - board.openCount(key));
    NodeContext context = new NodeContext(platform, pass, board, budget, behavior.policy(), now);
    if (NodeRules.needsContainer(record.type()) && NodeRules.lacksContainer(record.type(), context.anchor().getType())) {
      withdrawAll(board, key);
      status.accept(key, NodeStatus.NO_CONTAINER);
      return;
    }
    try {
      behavior.scan(context, budget);
    } catch (RuntimeException failure) {
      if (FAILURES_LOGGED.add(record.type())) {
        Adapt.warn("Golemancy " + record.type().id() + " node scan failed at " + key + "; the node keeps scanning.", failure);
      }
    }
    if (pass.current().getAsBoolean()) {
      NodeStatus reported = context.status().orElseGet(() -> board.status(key, now));
      status.accept(key, reported == NodeStatus.IDLE && board.openCount(key) > 0 ? NodeStatus.WAITING : reported);
    }
  }

  @Override
  public void inactive(NodeKey key) {
    withdrawAll(boards.board(key.world()), key);
    status.accept(key, NodeStatus.PAUSED);
  }

  @Override
  public void invalidated(NodeKey key) {
    withdrawAll(boards.board(key.world()), key);
  }

  static boolean discovery(NodeType type) {
    return type == NodeType.HARVEST || type == NodeType.BREAKER || type == NodeType.LUMBER;
  }

  @Override
  public void worldUnloaded(UUID world) {
    boards.forget(world);
  }

  private static void withdrawAll(GolemTaskBoard board, NodeKey key) {
    for (GolemTask task : board.tasks(key)) {
      board.withdraw(task);
    }
  }
}
