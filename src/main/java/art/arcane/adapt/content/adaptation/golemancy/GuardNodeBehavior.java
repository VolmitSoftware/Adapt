package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskMatch;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class GuardNodeBehavior implements NodeBehavior {
  private final Adaptation<?> policy;

  GuardNodeBehavior(Adaptation<?> policy) {
    this.policy = Objects.requireNonNull(policy);
  }

  static boolean guards(NodeRecord record, UUID owner, int channels, List<GolemCoreType> cores) {
    return record.type() == NodeType.GUARD
        && record.owner().equals(owner)
        && GolemTaskMatch.channel(record.channel(), channels)
        && GolemTaskMatch.core(NodeType.GUARD, cores);
  }

  static boolean covers(NodeKey key, NodeRecord record, double x, double y, double z) {
    int[] center = NodeArea.center(key);
    return within(x, center[0], record.radiusX()) && within(y, center[1], record.radiusY()) && within(z, center[2], record.radiusZ());
  }

  @Override
  public Adaptation<?> policy() {
    return policy;
  }

  @Override
  public void scan(NodeContext node, ScanBudget budget) {
  }

  @Override
  public GolemActResult act(GolemContext golem, GolemTask task) {
    return GolemActResult.FAILED;
  }

  List<NodeIndex.Entry> nodesFor(GolemHandle golem, World world, Location home) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      return List.of();
    }
    double radius = GolemancyRuntime.workSettings().baseWorkRadius();
    List<NodeIndex.Entry> near = service.get().index().near(world.getUID(), home.getX(), home.getY(), home.getZ(), radius);
    if (near.isEmpty()) {
      return List.of();
    }
    GolemState state = golem.state();
    int channels = state.channels();
    List<GolemCoreType> cores = state.cores();
    UUID owner = golem.owner();
    List<NodeIndex.Entry> guarded = new ArrayList<>(near.size());
    for (NodeIndex.Entry entry : near) {
      if (guards(entry.record(), owner, channels, cores) && active(world, entry.key(), entry.record())) {
        guarded.add(entry);
      }
    }
    return guarded;
  }

  void engaged(NodeKey key, long nowMillis) {
    GolemancyRuntime.boards().ifPresent(boards -> boards.board(key.world()).markWorked(key, nowMillis));
  }

  private static boolean active(World world, NodeKey key, NodeRecord record) {
    if (record.redstone() == RedstoneMode.IGNORE || !J.isOwnedByCurrentRegion(new Location(world, key.x(), key.y(), key.z()))) {
      return true;
    }
    return NodeRules.active(record, world.getBlockAt(key.x(), key.y(), key.z()));
  }

  private static boolean within(double coordinate, int center, int radius) {
    int block = (int) Math.floor(coordinate);
    return block >= center - radius && block <= center + radius;
  }
}
