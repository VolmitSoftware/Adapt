package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.reveal.RevealSettings;
import art.arcane.adapt.api.tick.TickedObject;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class NodeService extends TickedObject {
  private static final double DROP_FACE_DISTANCE = 0.45D;
  private static final int RECONCILE_DELAY_TICKS = 40;
  private static final int RECONCILE_ATTEMPTS = 5;

  private final NodePlatform platform;
  private final Supplier<NodeSettings> settings;
  private final Supplier<RevealSettings> revealSettings;
  private final NodeIndex index = new NodeIndex();
  private final NodeRenderer renderer;
  private final Map<NodeKey, ScanState> scans = new ConcurrentHashMap<>();
  private final AtomicLong scanTokens = new AtomicLong();
  private volatile NodeScanHook scanHook;

  NodeService(NodePlatform platform, Supplier<NodeSettings> settings, Supplier<RevealSettings> revealSettings) {
    super("golemancy", "golem-nodes");
    this.platform = Objects.requireNonNull(platform);
    this.settings = Objects.requireNonNull(settings);
    this.revealSettings = Objects.requireNonNull(revealSettings);
    this.renderer = new NodeRenderer(platform::icon);
  }

  public static NodeService create(Supplier<NodeSettings> settings, Supplier<RevealSettings> revealSettings) {
    return new NodeService(new BukkitNodePlatform(), settings, revealSettings);
  }

  public NodeIndex index() {
    return index;
  }

  public NodeRenderer renderer() {
    return renderer;
  }

  public Optional<NodeRecord> node(NodeKey key) {
    return index.get(key);
  }

  public void setScanHook(NodeScanHook hook) {
    scanHook = hook;
  }

  public void indexLoadedChunks() {
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        load(world, chunk.getX(), chunk.getZ());
      }
    }
  }

  public NodePlacementVerdict place(Player owner, Block anchor, BlockFace face, NodeType type, NodeWeavingPolicy policy) {
    World world = anchor.getWorld();
    NodeKey key = NodeKey.of(anchor, face);
    UUID ownerId = owner.getUniqueId();
    Optional<GolemRoster> roster = platform.roster(ownerId);
    boolean chunkReady = index.isReady(key.world(), key.chunkKey()) && platform.owns(key.center(world));
    NodeFaces faces = roster.isPresent() && chunkReady ? platform.read(world, key.x(), key.y(), key.z()) : NodeFaces.EMPTY;
    int level = policy.level(owner);
    boolean needsContainer = NodeRules.needsContainer(type);
    Location anchorLocation = anchor.getLocation();
    NodePlacementCheck check = new NodePlacementCheck(
        roster.isPresent(),
        chunkReady,
        !faces.has(face),
        policy.canPlace(owner, anchorLocation, anchor.getRelative(face).getLocation()),
        needsContainer,
        NodeRules.isContainer(anchor.getType()),
        needsContainer && policy.canAccessContainer(owner, anchorLocation),
        index.count(key.world(), key.chunkKey()),
        settings.get().maxNodesPerChunk(),
        roster.map(GolemRoster::nodeCount).orElse(0),
        policy.nodeCap(level));
    NodePlacementVerdict verdict = NodeRules.placement(check);
    if (verdict != NodePlacementVerdict.READY) {
      return verdict;
    }
    int radius = Math.min(NodeRecord.DEFAULT_RADIUS, Math.max(0, policy.maxAreaRadius(level)));
    NodeRecord record = NodeRecord.builder(ownerId, type, face).radii(radius, radius, radius).build();
    platform.write(world, key.x(), key.y(), key.z(), faces.with(record));
    index.put(key, record);
    if (roster.get().addNode(GolemRoster.NodeEntry.at(key.anchor(), face, type))) {
      platform.rosterChanged(ownerId);
    }
    track(world, key, record);
    return verdict;
  }

  public boolean remove(Player owner, NodeKey key) {
    World world = platform.world(key.world());
    if (world == null || !platform.owns(key.center(world))) {
      return false;
    }
    NodeRecord record = index.get(key).orElse(null);
    if (record == null || !record.owner().equals(owner.getUniqueId())) {
      return false;
    }
    purge(world, key, record, false);
    for (ItemStack leftover : owner.getInventory().addItem(GolemItems.node(record.type())).values()) {
      owner.getWorld().dropItemNaturally(owner.getLocation(), leftover);
    }
    return true;
  }

  public void shutdown() {
    unregister();
    scans.clear();
    renderer.shutdown();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent e) {
    Chunk chunk = e.getChunk();
    load(chunk.getWorld(), chunk.getX(), chunk.getZ());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkUnloadEvent e) {
    Chunk chunk = e.getChunk();
    forget(index.unload(chunk.getWorld().getUID(), GolemRoster.chunkKey(chunk.getX(), chunk.getZ())));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(WorldUnloadEvent e) {
    forget(index.unloadWorld(e.getWorld().getUID()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    reconcileLater(e.getPlayer().getUniqueId(), RECONCILE_ATTEMPTS);
  }

  private void load(World world, int chunkX, int chunkZ) {
    UUID worldId = world.getUID();
    long chunkKey = GolemRoster.chunkKey(chunkX, chunkZ);
    long generation = index.beginLoad(worldId, chunkKey);
    platform.async(() -> {
      Map<NodeKey, NodeRecord> stored;
      try {
        stored = platform.readChunk(world, chunkX, chunkZ);
      } catch (RuntimeException failure) {
        Adapt.warn("Could not read Golemancy nodes for chunk " + chunkX + ", " + chunkZ + " in " + world.getName() + "; they stay hidden until the chunk reloads.", failure);
        stored = Map.of();
      }
      Map<NodeKey, NodeRecord> nodes = stored;
      Location center = new Location(world, (chunkX << 4) + 8D, world.getMinHeight(), (chunkZ << 4) + 8D);
      if (!platform.runAt(center, () -> finishLoad(world, chunkX, chunkZ, generation, nodes), 0)) {
        index.unload(worldId, chunkKey);
      }
    }, 0);
  }

  private void finishLoad(World world, int chunkX, int chunkZ, long generation, Map<NodeKey, NodeRecord> stored) {
    UUID worldId = world.getUID();
    long chunkKey = GolemRoster.chunkKey(chunkX, chunkZ);
    if (!index.isCurrent(worldId, chunkKey, generation)) {
      return;
    }
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      index.unload(worldId, chunkKey);
      return;
    }
    Map<NodeKey, NodeRecord> valid = new HashMap<>(stored.size() * 2);
    for (Map.Entry<NodeKey, NodeRecord> entry : stored.entrySet()) {
      NodeKey key = entry.getKey();
      NodeRecord record = entry.getValue();
      if (anchorValid(world, key, record)) {
        valid.put(key, record);
      } else {
        purge(world, key, record, true);
      }
    }
    if (!index.completeLoad(worldId, chunkKey, generation, valid)) {
      return;
    }
    for (Map.Entry<NodeKey, NodeRecord> entry : valid.entrySet()) {
      track(world, entry.getKey(), entry.getValue());
    }
  }

  private void track(World world, NodeKey key, NodeRecord record) {
    renderer.spawn(world, key, record, NodeStatus.IDLE, revealSettings.get().labelViewRange());
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.nodeSpawned(key));
    ScanState state = new ScanState(scanTokens.incrementAndGet());
    scans.put(key, state);
    int interval = settings.get().scanIntervalTicks();
    scheduleScan(world, key, state.token, 1 + Math.floorMod(key.hashCode(), interval));
  }

  private void scheduleScan(World world, NodeKey key, long token, int delayTicks) {
    if (!platform.runAt(key.center(world), () -> scan(world, key, token), delayTicks)) {
      scans.remove(key);
    }
  }

  private void scan(World world, NodeKey key, long token) {
    ScanState state = scans.get(key);
    if (state == null || state.token != token) {
      return;
    }
    NodeRecord record = index.get(key).orElse(null);
    if (record == null) {
      scans.remove(key, state);
      return;
    }
    if (!anchorValid(world, key, record)) {
      purge(world, key, record, true);
      return;
    }
    NodeScanHook hook = scanHook;
    NodeSettings current = settings.get();
    if (hook != null) {
      NodeScanCursor.Window window = state.cursor.next(current.blocksPerScan(), NodeArea.volume(record));
      hook.scan(new NodeScanPass(world, key, record, window, current.maxOpenTasksPerNode()));
    }
    scheduleScan(world, key, token, current.scanIntervalTicks());
  }

  private boolean anchorValid(World world, NodeKey key, NodeRecord record) {
    return NodeRules.validate(record.type(), world.getBlockAt(key.x(), key.y(), key.z()).getType()) == NodeValidation.KEEP;
  }

  private void purge(World world, NodeKey key, NodeRecord record, boolean drop) {
    NodeFaces faces = platform.read(world, key.x(), key.y(), key.z());
    NodeRecord stored = faces.get(key.face());
    if (stored != null && stored.owner().equals(record.owner())) {
      platform.write(world, key.x(), key.y(), key.z(), faces.without(key.face()));
    }
    index.remove(key);
    scans.remove(key);
    renderer.despawn(key);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.nodeGone(key));
    UUID owner = record.owner();
    platform.roster(owner).ifPresent(roster -> {
      if (roster.removeNode(key.anchor(), key.face())) {
        platform.rosterChanged(owner);
      }
    });
    if (drop) {
      BlockFace face = key.face();
      Location at = key.center(world).add(face.getModX() * DROP_FACE_DISTANCE, face.getModY() * DROP_FACE_DISTANCE, face.getModZ() * DROP_FACE_DISTANCE);
      world.dropItemNaturally(at, GolemItems.node(record.type()));
    }
  }

  private void forget(List<NodeKey> keys) {
    for (NodeKey key : keys) {
      scans.remove(key);
      renderer.despawn(key);
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.nodeGone(key));
    }
  }

  private void reconcileLater(UUID owner, int attemptsLeft) {
    platform.async(() -> reconcile(owner, attemptsLeft), RECONCILE_DELAY_TICKS);
  }

  private void reconcile(UUID owner, int attemptsLeft) {
    Optional<GolemRoster> roster = platform.roster(owner);
    if (roster.isEmpty()) {
      if (attemptsLeft > 1 && platform.isOnline(owner)) {
        reconcileLater(owner, attemptsLeft - 1);
      }
      return;
    }
    boolean changed = false;
    for (GolemRoster.NodeEntry entry : roster.get().nodes()) {
      World world = platform.world(entry.block().world());
      if (world == null) {
        continue;
      }
      NodeRecord record = platform.read(world, entry.block().x(), entry.block().y(), entry.block().z()).get(entry.face());
      if (record == null || !record.owner().equals(owner) || record.type() != entry.type()) {
        changed |= roster.get().removeNode(entry.block(), entry.face());
      }
    }
    if (changed) {
      platform.rosterChanged(owner);
    }
  }

  private static final class ScanState {
    private final long token;
    private final NodeScanCursor cursor = new NodeScanCursor();

    private ScanState(long token) {
      this.token = token;
    }
  }
}
