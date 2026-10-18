package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class GolemTaskBoards {
  private final Function<NodeKey, Optional<NodeRecord>> nodes;
  private final Map<UUID, GolemTaskBoard> boards = new ConcurrentHashMap<>();

  public GolemTaskBoards(Function<NodeKey, Optional<NodeRecord>> nodes) {
    this.nodes = Objects.requireNonNull(nodes);
  }

  public GolemTaskBoard board(UUID world) {
    return boards.computeIfAbsent(Objects.requireNonNull(world), id -> new GolemTaskBoard(id, nodes));
  }

  public void forget(UUID world) {
    boards.remove(world);
  }
}
