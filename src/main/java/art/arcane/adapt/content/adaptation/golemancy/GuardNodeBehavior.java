package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskMatch;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.ScanBudget;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class GuardNodeBehavior implements NodeBehavior {
  static final long NODE_CACHE_MILLIS = 1_500L;

  private final Adaptation<?> policy;
  private final Map<UUID, GuardedNodes> guarded = new ConcurrentHashMap<>();

  GuardNodeBehavior(Adaptation<?> policy) {
    this.policy = Objects.requireNonNull(policy);
  }

  static boolean guards(NodeRecord record, UUID owner, int channels, List<GolemCoreType> cores) {
    return record.type() == NodeType.GUARD
        && record.owner().equals(owner)
        && GolemTaskMatch.channel(record.channel(), channels)
        && GolemTaskMatch.core(NodeType.GUARD, cores);
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

  List<NodeIndex.Entry> nodesFor(GolemHandle golem, World world, Location home, long nowMillis) {
    GolemState state = golem.state();
    GolemState.Anchor anchor = state.home();
    int channels = state.channels();
    GuardedNodes cached = guarded.get(golem.id());
    if (cached != null && nowMillis < cached.until() && cached.channels() == channels && Objects.equals(cached.home(), anchor)) {
      return cached.nodes();
    }
    List<NodeIndex.Entry> nodes = lookup(golem, state, world, home);
    guarded.put(golem.id(), new GuardedNodes(anchor, channels, nodes, nowMillis + NODE_CACHE_MILLIS));
    return nodes;
  }

  void engaged(NodeKey key, long nowMillis) {
    GolemancyRuntime.boards().ifPresent(boards -> boards.board(key.world()).markWorked(key, nowMillis));
  }

  boolean pending() {
    return !guarded.isEmpty();
  }

  void sweep(long nowMillis) {
    guarded.values().removeIf(nodes -> nowMillis >= nodes.until());
  }

  void forget() {
    guarded.clear();
  }

  private static List<NodeIndex.Entry> lookup(GolemHandle golem, GolemState state, World world, Location home) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      return List.of();
    }
    GolemRuneEffects runes = GolemRunes.effects(golem);
    double radius = GolemancyRuntime.workSettings().baseWorkRadius() + runes.radiusBonus();
    List<NodeIndex.Entry> near = service.get().index().near(world.getUID(), home.getX(), home.getY(), home.getZ(), radius);
    if (near.isEmpty()) {
      return List.of();
    }
    int channels = runes.channels(state.channels());
    List<GolemCoreType> cores = GolemMastery.cores(golem);
    UUID owner = golem.owner();
    List<NodeIndex.Entry> found = new ArrayList<>(near.size());
    for (NodeIndex.Entry entry : near) {
      if (guards(entry.record(), owner, channels, cores) && GolemancySupport.active(world, entry.key(), entry.record())) {
        found.add(entry);
      }
    }
    return List.copyOf(found);
  }

  private record GuardedNodes(GolemState.Anchor home, int channels, List<NodeIndex.Entry> nodes, long until) {
  }
}
