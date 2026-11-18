package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.data.BlockStorePort;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class LiveNodePlatform implements NodePlatform {
  @Override
  public Optional<GolemRoster> roster(UUID owner) {
    AdaptPlayer player = onlinePlayer(owner);
    return player == null ? Optional.empty() : Optional.of(player.getData().getGolemancy());
  }

  @Override
  public void rosterChanged(UUID owner) {
    AdaptPlayer player = onlinePlayer(owner);
    if (player != null) {
      player.requestSave();
    }
  }

  @Override
  public boolean isOnline(UUID owner) {
    return player(owner) != null;
  }

  @Override
  public NodeFaces read(WorldRef world, int x, int y, int z) {
    NodeFaces faces = BlockStorePort.current().get(world, x, y, z, NodeCodec.SLICE);
    return faces == null ? NodeFaces.EMPTY : faces;
  }

  @Override
  public void write(WorldRef world, int x, int y, int z, NodeFaces faces) {
    if (faces.isEmpty()) {
      BlockStorePort.current().remove(world, x, y, z, NodeCodec.SLICE);
    } else {
      BlockStorePort.current().set(world, x, y, z, NodeCodec.SLICE, faces);
    }
  }

  @Override
  public Map<NodeKey, NodeRecord> readChunk(WorldRef world, int chunkX, int chunkZ) {
    UUID worldId = world.uid();
    Map<NodeKey, NodeRecord> nodes = new HashMap<>();
    BlockStorePort.current().iterateChunk(world, chunkX, chunkZ, NodeCodec.SLICE, (int x, int y, int z, NodeFaces faces) -> {
      for (NodeRecord record : faces.records()) {
        nodes.put(new NodeKey(worldId, x, y, z, record.face()), record);
      }
    });
    return nodes;
  }

  @Override
  public boolean savePlate(WorldRef world, int x, int z) {
    return BlockStorePort.current().savePlate(world, x, z);
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
    return delayTicks <= 0 ? Tasks.runAt(location, task) : Tasks.runAt(location, task, delayTicks);
  }

  @Override
  public void async(Runnable task, int delayTicks) {
    if (delayTicks <= 0) {
      Tasks.a(task);
    } else {
      Tasks.a(task, delayTicks);
    }
  }

  @Override
  public boolean owns(Loc location) {
    return Tasks.isOwnedByCurrentRegion(location);
  }

  @Override
  public boolean owns(EntityRef entity) {
    return Tasks.isOwnedByCurrentRegion(entity);
  }

  @Override
  public WorldRef world(UUID id) {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getPlatform().host().world(id);
  }

  @Override
  public ItemRef icon(NodeType type) {
    return AdaptCore.platform().host().items().create(NodeIcons.icon(type));
  }

  @Override
  public PlayerRef player(UUID owner) {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getPlatform().host().player(owner);
  }

  @Override
  public long nowMillis() {
    return System.currentTimeMillis();
  }

  private static AdaptPlayer onlinePlayer(UUID owner) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    return server == null ? null : server.getOnlineAdaptPlayer(owner);
  }
}
