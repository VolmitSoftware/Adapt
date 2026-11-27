package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.world.BlockStore;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class LiveNodePlatform implements NodePlatform {
  @Override
  public Optional<GolemRoster> roster(UUID owner) {
    return profile(owner).map((PlayerProfile player) -> player.section(GolemRoster.section()));
  }

  @Override
  public void rosterChanged(UUID owner) {
    profile(owner).ifPresent(PlayerProfile::requestSave);
  }

  @Override
  public boolean isOnline(UUID owner) {
    return player(owner) != null;
  }

  @Override
  public NodeFaces read(WorldRef world, int x, int y, int z) {
    NodeFaces faces = BlockStore.get(world, x, y, z, NodeCodec.slice());
    return faces == null ? NodeFaces.EMPTY : faces;
  }

  @Override
  public void write(WorldRef world, int x, int y, int z, NodeFaces faces) {
    if (faces.isEmpty()) {
      BlockStore.remove(world, x, y, z, NodeCodec.slice());
    } else {
      BlockStore.set(world, x, y, z, NodeCodec.slice(), faces);
    }
  }

  @Override
  public Map<NodeKey, NodeRecord> readChunk(WorldRef world, int chunkX, int chunkZ) {
    UUID worldId = world.uid();
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    BlockStore.forEach(world, chunkX, chunkZ, NodeCodec.slice(), (int x, int y, int z, NodeFaces faces) -> {
      for (NodeRecord record : faces.records()) {
        nodes.put(new NodeKey(worldId, x, y, z, record.face()), record);
      }
    });
    return nodes;
  }

  @Override
  public boolean savePlate(WorldRef world, int x, int z) {
    return BlockStore.save(world, x, z);
  }

  @Override
  public NodePower powered(WorldRef world, int x, int y, int z) {
    return NodeRules.power(world.blockAt(x, y, z));
  }

  @Override
  public boolean chunkLoaded(WorldRef world, int chunkX, int chunkZ) {
    return world.isChunkLoaded(chunkX, chunkZ);
  }

  @Override
  public boolean runAt(Loc location, Runnable task, int delayTicks) {
    return delayTicks <= 0 ? AdaptApi.scheduler().runAt(location, task) : AdaptApi.scheduler().runAt(location, task, delayTicks);
  }

  @Override
  public void async(Runnable task, int delayTicks) {
    if (delayTicks <= 0) {
      AdaptApi.scheduler().runAsync(task);
    } else {
      AdaptApi.scheduler().runAsync(task, delayTicks);
    }
  }

  @Override
  public boolean owns(Loc location) {
    return AdaptApi.scheduler().owns(location);
  }

  @Override
  public boolean owns(EntityRef entity) {
    return AdaptApi.scheduler().owns(entity);
  }

  @Override
  public WorldRef world(UUID id) {
    return !AdaptApi.isRunning() ? null : AdaptApi.platform().host().world(id);
  }

  @Override
  public ItemRef icon(NodeType type) {
    return AdaptApi.platform().host().items().create(NodeIcons.icon(type));
  }

  @Override
  public PlayerRef player(UUID owner) {
    return !AdaptApi.isRunning() ? null : AdaptApi.platform().host().player(owner);
  }

  @Override
  public long nowMillis() {
    return System.currentTimeMillis();
  }

  private static Optional<PlayerProfile> profile(UUID owner) {
    return AdaptApi.isRunning() ? AdaptApi.players().find(owner) : Optional.empty();
  }
}
