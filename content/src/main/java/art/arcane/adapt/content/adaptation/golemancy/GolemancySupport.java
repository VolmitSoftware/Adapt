package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.item.ShapedRecipeParser;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRunes;
import art.arcane.adapt.content.golem.GolemTarget;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.GolemTaskMatch;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.item.ConfiguredShapedRecipe;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeArea;
import art.arcane.adapt.content.golem.node.NodeContainers;
import art.arcane.adapt.content.golem.node.NodeContext;
import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeRules;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.content.golem.node.RedstoneMode;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class GolemancySupport {
  private GolemancySupport() {
  }

  static void registerRecipe(SimpleAdaptation<?> adaptation, ShapedRecipeParser parser, String name, ConfiguredShapedRecipe spec, int level) {
    Id key = Id.adapt(name);
    GolemItems.recipeBook().register(parser, key, spec).ifPresent(recipe -> adaptation.registerRecipe(recipe.withRequiredLevel(level)));
    if (spec.isEnabled()) {
      GolemItems.craftCounter().track(key);
    }
  }

  static int ownerLevel(SimpleAdaptation<?> core, GolemContext golem) {
    PlayerRef owner = golem.owner();
    return owner == null ? 0 : core.ownerLevel(owner.uuid());
  }

  static void recordOwnerLevel(String adaptation, UUID owner, int level) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      return;
    }
    for (GolemHandle handle : registry.get().loadedOf(owner)) {
      AdaptApi.scheduler().runEntity(handle.entity(), () -> {
        if (!handle.isRetired()) {
          handle.recordOwnerLevel(adaptation, level);
        }
      });
    }
  }

  static Optional<InventoryRef> homeChest(GolemContext golem) {
    return homeChestBlock(golem).flatMap(golem::container);
  }

  static void visitHomeChest(GolemContext golem) {
    homeChestBlock(golem).ifPresent(golem::visit);
  }

  private static Optional<BlockRef> homeChestBlock(GolemContext golem) {
    GolemAnchor chest = golem.handle().state().homeChest();
    WorldRef world = golem.world();
    if (chest == null || !chest.world().equals(world.uid()) || !world.isChunkLoaded(chest.x() >> 4, chest.z() >> 4)
        || !golem.owns(new Loc(world, chest.x(), chest.y(), chest.z(), 0F, 0F))) {
      return Optional.empty();
    }
    return Optional.of(world.blockAt(chest.x(), chest.y(), chest.z()));
  }

  static List<NodeIndex.Entry> ownedNodes(GolemContext golem, WorldRef world, NodeType type) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      return new ArrayList<>();
    }
    Loc home = golem.home();
    List<NodeIndex.Entry> near = service.get().index().near(world.uid(), home.x(), home.y(), home.z(), golem.workRadius());
    GolemHandle handle = golem.handle();
    UUID owner = handle.owner();
    int channels = GolemRunes.channels(handle);
    List<NodeIndex.Entry> found = new ArrayList<>(near.size());
    for (NodeIndex.Entry entry : near) {
      NodeRecord record = entry.record();
      if (record.type() == type && record.owner().equals(owner) && GolemTaskMatch.channel(record.channel(), channels)
          && active(golem, world, entry.key(), record)) {
        found.add(entry);
      }
    }
    return found;
  }

  static boolean active(WorldRef world, NodeKey key, NodeRecord record) {
    if (record.redstone() == RedstoneMode.IGNORE || !world.isChunkLoaded(key.chunkX(), key.chunkZ())
        || !AdaptApi.scheduler().owns(new Loc(world, key.x(), key.y(), key.z(), 0F, 0F))) {
      return true;
    }
    return NodeRules.active(record, world.blockAt(key.x(), key.y(), key.z()));
  }

  static boolean covered(NodeRecord record, GolemTask task) {
    GolemTarget target = task.target();
    return task.node().filter(key -> NodeArea.covers(key, record, target.x(), target.y(), target.z())).isPresent();
  }

  static Map<Long, GolemTask> open(NodeContext node) {
    List<GolemTask> tasks = node.tasks();
    if (tasks.isEmpty()) {
      return Map.of();
    }
    NodeKey key = node.key();
    NodeRecord record = node.record();
    Map<Long, GolemTask> open = new HashMap<>(tasks.size() * 2);
    for (GolemTask task : tasks) {
      GolemTarget target = task.target();
      if (!NodeArea.covers(key, record, target.x(), target.y(), target.z())) {
        node.withdraw(task);
        continue;
      }
      open.put(NodeContainers.blockKey(target.blockX(), target.blockY(), target.blockZ()), task);
    }
    return open;
  }

  private static boolean active(GolemContext golem, WorldRef world, NodeKey key, NodeRecord record) {
    if (record.redstone() == RedstoneMode.IGNORE || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
      return true;
    }
    Loc anchor = new Loc(world, key.x(), key.y(), key.z(), 0F, 0F);
    return !golem.owns(anchor) || NodeRules.active(record, world.blockAt(key.x(), key.y(), key.z()));
  }
}
