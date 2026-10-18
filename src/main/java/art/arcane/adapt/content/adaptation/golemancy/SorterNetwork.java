package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

final class SorterNetwork {
  private static final double CENTER = 0.5D;
  private static final double NODE_SLACK = 2D;

  private final GolemSorterCore core;
  private final SorterSpace space;
  private final SorterIndex index;
  private final NodeTagLookup tags;
  private final Function<ItemStack, String> variants;

  SorterNetwork(GolemSorterCore core, SorterSpace space, SorterIndex index, NodeTagLookup tags, Function<ItemStack, String> variants) {
    this.core = Objects.requireNonNull(core);
    this.space = Objects.requireNonNull(space);
    this.index = Objects.requireNonNull(index);
    this.tags = Objects.requireNonNull(tags);
    this.variants = Objects.requireNonNull(variants);
  }

  Optional<SorterSurvey> survey(SorterAccess access, World world, NodeKey input, NodeRecord record, int level, long nowMillis) {
    GolemSorterCore.Config config = core.config();
    Block inputBlock = world.getBlockAt(input.x(), input.y(), input.z());
    Optional<Inventory> inputInventory = access.owns(inputBlock) ? access.container(inputBlock) : Optional.empty();
    if (inputInventory.isEmpty()) {
      return Optional.empty();
    }
    ContainerId inputId = ContainerId.of(inputBlock, inputInventory.get());
    SorterDestination inputDestination = new SorterDestination(inputId, inputBlock, stock(level, inputId, inputInventory.get(), nowMillis),
        SorterDestination.Kind.INPUT, null, 0D);
    int radius = SorterRules.radius(record, SorterRules.destinationRadius(config.destinationRadiusByLevel, level));
    Set<ContainerId> excluded = new HashSet<>();
    excluded.add(inputId);
    Map<ContainerId, NodeRecord> filters = new HashMap<>();
    classifyNodes(access, world, input, record, level, radius, excluded, filters);
    List<SorterDestination> destinations = new ArrayList<>();
    for (Block block : space.containers(world, input.x(), input.y(), input.z(), radius)) {
      if (!access.owns(block)) {
        continue;
      }
      Optional<Inventory> inventory = access.container(block);
      if (inventory.isEmpty()) {
        continue;
      }
      ContainerId id = ContainerId.of(block, inventory.get());
      if (!excluded.add(id)) {
        continue;
      }
      NodeRecord filter = filters.get(id);
      SorterDestination.Kind kind = filter == null ? SorterDestination.Kind.PLAIN
          : SorterRules.overflow(filter) ? SorterDestination.Kind.OVERFLOW : SorterDestination.Kind.FILTER;
      double distance = id.distanceSquared(input.x() + CENTER, input.y() + CENTER, input.z() + CENTER);
      destinations.add(new SorterDestination(id, block, stock(level, id, inventory.get(), nowMillis), kind, filter, distance));
    }
    SorterRules.Match mode = SorterRules.match(record, config, level);
    Map<Material, String> groups = mode == SorterRules.Match.EXACT ? Map.of() : core.groups();
    return Optional.of(new SorterSurvey(new SorterMatcher(mode, groups, tags, variants), config, level, inputDestination, destinations));
  }

  private void classifyNodes(SorterAccess access, World world, NodeKey input, NodeRecord record, int level, int radius,
                             Set<ContainerId> excluded, Map<ContainerId, NodeRecord> filters) {
    boolean filtering = SorterRules.unlocked(level, core.config().filterLevel);
    List<NodeIndex.Entry> nearby = space.nodes(input.world(), input.x() + CENTER, input.y() + CENTER, input.z() + CENTER, radius + NODE_SLACK);
    for (NodeIndex.Entry entry : nearby) {
      NodeRecord node = entry.record();
      if (node.type() != NodeType.INPUT && node.type() != NodeType.FILTER) {
        continue;
      }
      NodeKey key = entry.key();
      Block anchor = world.getBlockAt(key.x(), key.y(), key.z());
      if (!access.owns(anchor)) {
        continue;
      }
      Optional<Inventory> inventory = NodeContainers.inventory(anchor);
      if (inventory.isEmpty()) {
        continue;
      }
      ContainerId id = ContainerId.of(anchor, inventory.get());
      if (node.type() == NodeType.FILTER && filtering && serves(node, record) && NodeRules.active(node, anchor)) {
        filters.putIfAbsent(id, node);
      } else {
        excluded.add(id);
      }
    }
    for (Map.Entry<ContainerId, NodeRecord> filter : List.copyOf(filters.entrySet())) {
      if (excluded.contains(filter.getKey())) {
        filters.remove(filter.getKey());
      }
    }
  }

  private SorterStock stock(int level, ContainerId id, Inventory inventory, long nowMillis) {
    if (!SorterRules.unlocked(level, core.config().indexLevel)) {
      return SorterStock.read(inventory, nowMillis);
    }
    Optional<SorterStock> cached = index.fresh(id, nowMillis);
    if (cached.isPresent()) {
      return cached.get();
    }
    SorterStock read = SorterStock.read(inventory, nowMillis);
    index.put(id, read);
    return read;
  }

  private static boolean serves(NodeRecord filter, NodeRecord input) {
    return filter.owner().equals(input.owner()) && (filter.channel() == null || filter.channel() == input.channel());
  }
}
