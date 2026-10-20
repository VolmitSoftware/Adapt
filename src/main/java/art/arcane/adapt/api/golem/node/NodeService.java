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
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.entity.Animals;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class NodeService extends TickedObject {
  private static final double DROP_FACE_DISTANCE = 0.45D;
  private static final int RECONCILE_DELAY_TICKS = 40;
  private static final int RECONCILE_ATTEMPTS = 5;
  private static final int SAVE_ATTEMPTS = 20;
  private static final int SAVE_RETRY_TICKS = 2;
  private static final String RETURN_STATE = "adapt:return";
  private static final String RETURN_PENDING = "pending";
  private static final String RETURN_DELIVERING = "delivering";

  private final NodePlatform platform;
  private final Supplier<NodeSettings> settings;
  private final Supplier<RevealSettings> revealSettings;
  private final NodeIndex index = new NodeIndex();
  private final NodeRenderer renderer;
  private final Map<NodeKey, ScanState> scans = new ConcurrentHashMap<>();
  private final AtomicLong scanTokens = new AtomicLong();
  private final Map<ChunkRef, LoadRetry> unavailableLoads = new ConcurrentHashMap<>();
  private final Map<NodeKey, PendingReturn> pendingReturns = new ConcurrentHashMap<>();
  private volatile boolean stopped;
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

  @Override
  public boolean hasTickDemand() {
    if (!unavailableLoads.isEmpty()) {
      return true;
    }
    for (PendingReturn pending : pendingReturns.values()) {
      if (!pending.started) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void onTick() {
    long now = platform.nowMillis();
    for (Map.Entry<ChunkRef, LoadRetry> entry : unavailableLoads.entrySet()) {
      ChunkRef chunk = entry.getKey();
      LoadRetry retry = entry.getValue();
      if (!index.isCurrent(chunk.world(), chunk.chunk(), retry.generation)) {
        unavailableLoads.remove(chunk, retry);
      } else if (retry.nextRetry <= now) {
        retry.nextRetry = Long.MAX_VALUE;
        readLoad(retry.world, GolemRoster.chunkX(chunk.chunk()), GolemRoster.chunkZ(chunk.chunk()), retry.generation, RECONCILE_ATTEMPTS, 0);
      }
    }
    for (Map.Entry<NodeKey, PendingReturn> entry : pendingReturns.entrySet()) {
      PendingReturn pending = entry.getValue();
      World world = platform.world(entry.getKey().world());
      if (world != null && !pending.started && pending.retryAt <= now && platform.chunkLoaded(world, entry.getKey().chunkX(), entry.getKey().chunkZ())) {
        resumeReturn(world, entry.getKey(), pending);
      }
    }
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
        NodeRules.faceAllowed(type, face),
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
    NodeRecord record = NodeRecord.builder(ownerId, type, face).area(NodeCuboid.symmetric(radius, radius, radius)).build();
    platform.write(world, key.x(), key.y(), key.z(), faces.with(record));
    index.put(key, record);
    roster.get().removeNode(key.anchor(), face);
    roster.get().addNode(GolemRoster.NodeEntry.at(key.anchor(), face, type));
    platform.rosterChanged(ownerId);
    try {
      track(world, key, record);
    } catch (RuntimeException failure) {
      Adapt.warn("Golemancy node " + key + " was stored but could not be shown or scheduled; it appears when its chunk reloads.", failure);
    }
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
    if (!release(world, key, record)) {
      return false;
    }
    PendingReturn pending = new PendingReturn(record, owner);
    pendingReturns.put(key, pending);
    resumeReturn(world, key, pending);
    return true;
  }

  public List<ReturnRecord> unresolvedReturns() {
    List<ReturnRecord> returns = new ArrayList<>();
    for (Map.Entry<NodeKey, PendingReturn> entry : pendingReturns.entrySet()) {
      PendingReturn pending = entry.getValue();
      if (pending.started) {
        returns.add(new ReturnRecord(entry.getKey(), pending.record.owner(), pending.record.type()));
      }
    }
    return List.copyOf(returns);
  }

  public static String returnId(NodeKey key) {
    return key.world() + ":" + key.x() + ":" + key.y() + ":" + key.z() + ":" + key.face().name();
  }

  public boolean resolveReturn(UUID owner, NodeKey key, boolean delivered) {
    World world = platform.world(key.world());
    if (world == null || !platform.owns(key.center(world))) {
      return false;
    }
    NodeFaces faces = platform.read(world, key.x(), key.y(), key.z());
    NodeRecord record = faces.get(key.face());
    if (record == null || !record.owner().equals(owner) || !RETURN_DELIVERING.equals(record.data().get(RETURN_STATE))) {
      return false;
    }
    PendingReturn previous = pendingReturns.get(key);
    if (previous != null && previous.running) {
      return false;
    }
    if (delivered) {
      platform.write(world, key.x(), key.y(), key.z(), faces.without(key.face()));
      pendingReturns.remove(key);
      saveReturnCleanup(world, key, SAVE_ATTEMPTS);
    } else {
      NodeRecord restored = returnState(record, RETURN_PENDING);
      platform.write(world, key.x(), key.y(), key.z(), faces.with(restored));
      PendingReturn pending = new PendingReturn(restored, platform.player(owner));
      pendingReturns.put(key, pending);
      resumeReturn(world, key, pending);
    }
    return true;
  }

  public Optional<NodeRecord> update(UUID owner, NodeKey key, UnaryOperator<NodeRecord> change) {
    World world = platform.world(key.world());
    if (world == null || !platform.owns(key.center(world))) {
      return Optional.empty();
    }
    NodeRecord current = index.get(key).orElse(null);
    if (current == null || !current.owner().equals(owner)) {
      return Optional.empty();
    }
    NodeRecord next = change.apply(current);
    if (!next.owner().equals(owner) || next.type() != current.type() || next.face() != current.face()) {
      return Optional.empty();
    }
    if (next.equals(current)) {
      return Optional.of(current);
    }
    NodeFaces faces = platform.read(world, key.x(), key.y(), key.z());
    NodeRecord stored = faces.get(key.face());
    if (stored == null || !stored.owner().equals(owner) || stored.type() != current.type()) {
      return Optional.empty();
    }
    platform.write(world, key.x(), key.y(), key.z(), faces.with(next));
    index.put(key, next);
    renderer.refresh(key, next);
    ScanState previous = scans.get(key);
    if (previous != null) {
      ScanState state = new ScanState(scanTokens.incrementAndGet());
      for (int known : previous.discovery.knownIndices()) {
        int[] at = NodeArea.position(key, current, known);
        state.discovery.remember(areaIndex(key, next, at[0], at[1], at[2]), true);
      }
      scans.put(key, state);
      NodeScanHook hook = scanHook;
      if (hook != null) {
        hook.invalidated(key);
      }
      scheduleScan(world, key, state.token, 1);
    }
    return Optional.of(next);
  }

  public void status(NodeKey key, NodeStatus status) {
    NodeRecord record = index.get(key).orElse(null);
    ScanState state = scans.get(key);
    World world = platform.world(key.world());
    if (stopped || record == null || state == null || world == null) {
      return;
    }
    Runnable update = () -> {
      if (stopped || !current(key, state, record)) {
        return;
      }
      try {
        renderer.status(key, record, status);
      } catch (RuntimeException failure) {
        if (state.statusFailureLogged.compareAndSet(false, true)) {
          Adapt.warn("Could not refresh Golemancy node status at " + key + "; its work checks continue.", failure);
        }
      }
    };
    Location anchor = key.center(world);
    if (platform.owns(anchor)) {
      update.run();
    } else {
      platform.runAt(anchor, update, 1);
    }
  }

  public void shutdown() {
    stopped = true;
    unregister();
    scans.clear();
    renderer.shutdown();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent e) {
    Chunk chunk = e.getChunk();
    if (e.isNewChunk()) {
      UUID worldId = chunk.getWorld().getUID();
      long chunkKey = GolemRoster.chunkKey(chunk.getX(), chunk.getZ());
      index.completeLoad(worldId, chunkKey, index.beginLoad(worldId, chunkKey), Map.of());
      return;
    }
    load(chunk.getWorld(), chunk.getX(), chunk.getZ());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkUnloadEvent e) {
    Chunk chunk = e.getChunk();
    forget(index.unload(chunk.getWorld().getUID(), GolemRoster.chunkKey(chunk.getX(), chunk.getZ())));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(WorldUnloadEvent e) {
    UUID world = e.getWorld().getUID();
    forget(index.unloadWorld(world));
    NodeScanHook hook = scanHook;
    if (hook != null) {
      hook.worldUnloaded(world);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    reconcileLater(e.getPlayer().getUniqueId(), RECONCILE_ATTEMPTS);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockGrowEvent event) {
    hint(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    hint(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent event) {
    hint(event.getBlockPlaced());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockFadeEvent event) {
    hint(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockFormEvent event) {
    hint(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(StructureGrowEvent event) {
    hint(event.getLocation().getBlock());
    int count = 0;
    for (BlockState block : event.getBlocks()) {
      if (count++ >= 64) {
        break;
      }
      hint(block.getBlock());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ItemSpawnEvent event) {
    hintQuery(event.getLocation(), NodeType.COLLECTION);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ItemMergeEvent event) {
    hintQuery(event.getEntity().getLocation(), NodeType.COLLECTION);
    hintQuery(event.getTarget().getLocation(), NodeType.COLLECTION);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ItemDespawnEvent event) {
    hintQuery(event.getEntity().getLocation(), NodeType.COLLECTION);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityPickupItemEvent event) {
    hintQuery(event.getItem().getLocation(), NodeType.COLLECTION);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(CreatureSpawnEvent event) {
    if (event.getEntity() instanceof Animals) {
      hintQuery(event.getLocation(), NodeType.PASTURE);
    }
  }

  public boolean queryDue(NodeKey key, long nowMillis) {
    ScanState state = scans.get(key);
    return state == null || state.discovery.queryDue(nowMillis);
  }

  public void emptyQuery(NodeKey key, NodeRecord record, long nowMillis) {
    ScanState state = scans.get(key);
    if (state != null && index.get(key).orElse(null) == record) {
      state.discovery.queryResult(false, nowMillis);
    }
  }

  private void hintQuery(Location at, NodeType type) {
    World world = at.getWorld();
    if (world == null) {
      return;
    }
    for (NodeIndex.Entry entry : index.near(world.getUID(), at.getX(), at.getY(), at.getZ(), 32D)) {
      ScanState state = scans.get(entry.key());
      if (state != null && entry.record().type() == type
          && NodeArea.covers(entry.key(), entry.record(), at.getX(), at.getY(), at.getZ())) {
        state.discovery.wakeQuery();
      }
    }
  }

  private void hint(Block block) {
    UUID world = block.getWorld().getUID();
    int x = block.getX();
    int y = block.getY();
    int z = block.getZ();
    for (NodeIndex.Entry entry : index.near(world, x + 0.5D, y + 0.5D, z + 0.5D, 32D)) {
      ScanState state = scans.get(entry.key());
      if (state == null || !NodeWork.discovery(entry.record().type())) {
        continue;
      }
      hint(state, entry, x, y, z);
      hint(state, entry, x, y - 1, z);
      hint(state, entry, x, y + 1, z);
      hint(state, entry, x - 1, y, z);
      hint(state, entry, x + 1, y, z);
      hint(state, entry, x, y, z - 1);
      hint(state, entry, x, y, z + 1);
    }
  }

  private static void hint(ScanState state, NodeIndex.Entry entry, int x, int y, int z) {
    state.discovery.dirty(areaIndex(entry.key(), entry.record(), x, y, z));
  }

  private static int areaIndex(NodeKey key, NodeRecord record, int x, int y, int z) {
    if (!NodeArea.covers(key, record, x + 0.5D, y + 0.5D, z + 0.5D)) {
      return -1;
    }
    NodeCuboid area = record.area();
    int[] center = NodeArea.center(key);
    int offsetX = x - center[0] - area.minX();
    int offsetY = y - center[1] - area.minY();
    int offsetZ = z - center[2] - area.minZ();
    return offsetX + area.spanX() * (offsetZ + area.spanZ() * offsetY);
  }

  private void load(World world, int chunkX, int chunkZ) {
    UUID worldId = world.getUID();
    long chunkKey = GolemRoster.chunkKey(chunkX, chunkZ);
    long generation = index.beginLoad(worldId, chunkKey);
    unavailableLoads.remove(new ChunkRef(worldId, chunkKey));
    readLoad(world, chunkX, chunkZ, generation, RECONCILE_ATTEMPTS, 0);
  }

  private void readLoad(World world, int chunkX, int chunkZ, long generation, int attemptsLeft, int delayTicks) {
    UUID worldId = world.getUID();
    long chunkKey = GolemRoster.chunkKey(chunkX, chunkZ);
    platform.async(() -> {
      if (stopped || !index.isCurrent(worldId, chunkKey, generation)) {
        return;
      }
      Map<NodeKey, NodeRecord> stored;
      try {
        stored = platform.readChunk(world, chunkX, chunkZ);
      } catch (RuntimeException failure) {
        ChunkRef chunk = new ChunkRef(worldId, chunkKey);
        LoadRetry retry = new LoadRetry(world, generation);
        LoadRetry existing = unavailableLoads.putIfAbsent(chunk, retry);
        if (existing == null) {
          Adapt.warn("Could not read Golemancy nodes for chunk " + chunkX + ", " + chunkZ + " in " + world.getName() + "; its index remains unavailable while reads retry.", failure);
        }
        if (attemptsLeft > 1) {
          readLoad(world, chunkX, chunkZ, generation, attemptsLeft - 1, RECONCILE_DELAY_TICKS);
        } else {
          (existing == null ? retry : existing).nextRetry = platform.nowMillis() + 10_000L;
        }
        return;
      }
      unavailableLoads.computeIfPresent(new ChunkRef(worldId, chunkKey), (ignored, retry) -> retry.generation == generation ? null : retry);
      if (stored.isEmpty()) {
        index.completeLoad(worldId, chunkKey, generation, Map.of());
        return;
      }
      Map<NodeKey, NodeRecord> nodes = stored;
      Location center = new Location(world, (chunkX << 4) + 8D, world.getMinHeight(), (chunkZ << 4) + 8D);
      if (!platform.runAt(center, () -> finishLoad(world, chunkX, chunkZ, generation, nodes), 0)) {
        if (index.isCurrent(worldId, chunkKey, generation)) {
          LoadRetry retry = new LoadRetry(world, generation);
          retry.nextRetry = platform.nowMillis() + 5_000L;
          unavailableLoads.put(new ChunkRef(worldId, chunkKey), retry);
        }
      }
    }, delayTicks);
  }

  private void finishLoad(World world, int chunkX, int chunkZ, long generation, Map<NodeKey, NodeRecord> stored) {
    UUID worldId = world.getUID();
    long chunkKey = GolemRoster.chunkKey(chunkX, chunkZ);
    if (!index.isCurrent(worldId, chunkKey, generation)) {
      return;
    }
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      index.unload(worldId, chunkKey, generation);
      return;
    }
    Map<NodeKey, NodeRecord> active = new HashMap<>();
    for (Map.Entry<NodeKey, NodeRecord> entry : stored.entrySet()) {
      if (!entry.getValue().data().containsKey(RETURN_STATE)) {
        active.put(entry.getKey(), entry.getValue());
      }
    }
    if (!index.completeLoad(worldId, chunkKey, generation, active)) {
      return;
    }
    for (Map.Entry<NodeKey, NodeRecord> entry : stored.entrySet()) {
      if (entry.getValue().data().containsKey(RETURN_STATE)) {
        if (RETURN_PENDING.equals(entry.getValue().data().get(RETURN_STATE))) {
          PendingReturn pending = pendingReturns.computeIfAbsent(entry.getKey(), ignored -> new PendingReturn(entry.getValue(), platform.player(entry.getValue().owner())));
          resumeReturn(world, entry.getKey(), pending);
        } else if (RETURN_DELIVERING.equals(entry.getValue().data().get(RETURN_STATE))) {
          pendingReturns.computeIfAbsent(entry.getKey(), ignored -> {
            PendingReturn pending = new PendingReturn(entry.getValue(), platform.player(entry.getValue().owner()));
            pending.started = true;
            warnUnresolvedReturn(entry.getKey());
            return pending;
          });
        }
        continue;
      }
      try {
        track(world, entry.getKey(), entry.getValue());
      } catch (RuntimeException failure) {
        Adapt.warn("Golemancy node " + entry.getKey() + " loaded but could not be shown or scheduled; it appears when its chunk reloads.", failure);
      }
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
      scans.computeIfPresent(key, (ignored, state) -> state.token == token ? null : state);
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
    NodeScanHook hook = scanHook;
    NodeSettings current = settings.get();
    if (hook == null || !active(world, key, record)) {
      if (hook != null) {
        hook.inactive(key);
      }
      scheduleScan(world, key, token, current.scanIntervalTicks());
      return;
    }
    if (!NodeWork.discovery(record.type())) {
      NodeScanCursor.Window window = state.cursor.next(current.blocksPerScan(), NodeArea.volume(record));
      hook.scan(new NodeScanPass(world, key, record, window, current.maxOpenTasksPerNode(), state.discovery,
          () -> current(key, state, record)));
      scheduleScan(world, key, token, current.scanIntervalTicks());
      return;
    }
    NodeScanCursor.Window window = state.discovery.next(current.blocksPerScan(), NodeArea.volume(record));
    Map<Long, List<Integer>> chunks = new LinkedHashMap<>(4);
    for (int areaIndex : window.indices()) {
      int[] position = NodeArea.position(key, record, areaIndex);
      long chunk = GolemRoster.chunkKey(position[0] >> 4, position[2] >> 4);
      chunks.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(areaIndex);
    }
    scanBatch(world, key, state, record, current, new ArrayList<>(chunks.values()), 0);
  }

  private void scanBatch(World world, NodeKey key, ScanState state, NodeRecord record, NodeSettings current,
                         List<List<Integer>> batches, int batch) {
    if (!current(key, state, record)) {
      return;
    }
    if (batch >= batches.size()) {
      scheduleScan(world, key, state.token, current.scanIntervalTicks());
      return;
    }
    List<Integer> indices = batches.get(batch);
    int[] first = NodeArea.position(key, record, indices.getFirst());
    Runnable next = () -> scanBatch(world, key, state, record, current, batches, batch + 1);
    if (!platform.chunkLoaded(world, first[0] >> 4, first[2] >> 4)) {
      next.run();
      return;
    }
    Location at = new Location(world, first[0], first[1], first[2]);
    Runnable scan = () -> {
      try {
        NodeScanHook hook = scanHook;
        if (hook != null && current(key, state, record) && platform.chunkLoaded(world, first[0] >> 4, first[2] >> 4)) {
          int[] positions = new int[indices.size()];
          for (int index = 0; index < positions.length; index++) {
            positions[index] = indices.get(index);
          }
          hook.scan(new NodeScanPass(world, key, record, new NodeScanCursor.Window(positions, positions.length),
              current.maxOpenTasksPerNode(), state.discovery, () -> current(key, state, record)));
        }
      } catch (RuntimeException failure) {
        if (state.scanFailureLogged.compareAndSet(false, true)) {
          Adapt.warn("Could not finish a Golemancy node work check at " + key + "; its remaining checks continue.", failure);
        }
      } finally {
        next.run();
      }
    };
    if (platform.owns(at)) {
      scan.run();
    } else if (!platform.runAt(at, scan, 1)) {
      next.run();
    }
  }

  private boolean current(NodeKey key, ScanState state, NodeRecord record) {
    return scans.get(key) == state && index.get(key).orElse(null) == record;
  }

  private boolean active(World world, NodeKey key, NodeRecord record) {
    return record.redstone() == RedstoneMode.IGNORE || NodeRules.active(record.redstone(), platform.powered(world, key.x(), key.y(), key.z()));
  }

  private boolean release(World world, NodeKey key, NodeRecord record) {
    NodeFaces faces = platform.read(world, key.x(), key.y(), key.z());
    NodeRecord stored = faces.get(key.face());
    boolean taken = stored != null && stored.owner().equals(record.owner()) && stored.type() == record.type();
    if (taken) {
      platform.write(world, key.x(), key.y(), key.z(), faces.with(returnState(stored, RETURN_PENDING)));
    }
    index.remove(key);
    scans.remove(key);
    if (scanHook != null) {
      scanHook.invalidated(key);
    }
    renderer.despawn(key);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.nodeGone(key));
    UUID owner = record.owner();
    platform.roster(owner).ifPresent(roster -> {
      if (roster.removeNode(key.anchor(), key.face())) {
        platform.rosterChanged(owner);
      }
    });
    return taken;
  }

  private void resumeReturn(World world, NodeKey key, PendingReturn pending) {
    synchronized (pending) {
      if (pending.running || pending.started || stopped) {
        return;
      }
      pending.running = true;
    }
    saveThen(world, key, pending, () -> claimReturn(world, key, pending), SAVE_ATTEMPTS, 0);
  }

  private void claimReturn(World world, NodeKey key, PendingReturn pending) {
    if (!returnWorldAvailable(world)) {
      pending.running = false;
      return;
    }
    if (!platform.chunkLoaded(world, key.x() >> 4, key.z() >> 4)) {
      pending.running = false;
      return;
    }
    NodeFaces faces = platform.read(world, key.x(), key.y(), key.z());
    NodeRecord stored = faces.get(key.face());
    if (stored == null || !stored.owner().equals(pending.record.owner()) || !RETURN_PENDING.equals(stored.data().get(RETURN_STATE))) {
      pending.running = false;
      return;
    }
    platform.write(world, key.x(), key.y(), key.z(), faces.with(returnState(stored, RETURN_DELIVERING)));
    saveThen(world, key, pending, () -> deliverReturn(world, key, pending), SAVE_ATTEMPTS, 0);
  }

  private void deliverReturn(World world, NodeKey key, PendingReturn pending) {
    synchronized (pending) {
      if (pending.started) {
        return;
      }
      pending.started = true;
    }
    try {
      giveBack(pending.player, world, key, pending.record.type());
      NodeFaces faces = platform.read(world, key.x(), key.y(), key.z());
      platform.write(world, key.x(), key.y(), key.z(), faces.without(key.face()));
      pendingReturns.remove(key, pending);
      saveReturnCleanup(world, key, SAVE_ATTEMPTS);
    } catch (RuntimeException failure) {
      Adapt.warn("The return of Golemancy node " + key + " started but could not be confirmed; its stored delivery record is retained for review.", failure);
      warnUnresolvedReturn(key);
    } finally {
      pending.running = false;
    }
  }

  private void saveReturnCleanup(World world, NodeKey key, int attemptsLeft) {
    platform.async(() -> {
      if (!returnWorldAvailable(world)) {
        return;
      }
      try {
        if (platform.savePlate(world, key.x(), key.z())) {
          return;
        }
      } catch (RuntimeException failure) {
        if (attemptsLeft == SAVE_ATTEMPTS) {
          Adapt.warn("Could not save the completed Golemancy return " + key + ".", failure);
        }
      }
      if (attemptsLeft > 1 && !stopped) {
        saveReturnCleanup(world, key, attemptsLeft - 1);
      }
    }, SAVE_RETRY_TICKS);
  }

  private void saveThen(World world, NodeKey key, PendingReturn pending, Runnable handOut, int attemptsLeft, int delayTicks) {
    platform.async(() -> {
      if (!returnWorldAvailable(world)) {
        pending.running = false;
        return;
      }
      boolean saved = false;
      try {
        saved = platform.savePlate(world, key.x(), key.z());
      } catch (RuntimeException failure) {
        if (!pending.warned && returnWorldAvailable(world)) {
          pending.warned = true;
          Adapt.warn("Could not save the pending return of Golemancy node " + key + "; its item remains recoverable in storage.", failure);
        }
      }
      if (!saved && attemptsLeft > 1 && !stopped) {
        saveThen(world, key, pending, handOut, attemptsLeft - 1, SAVE_RETRY_TICKS);
        return;
      }
      if (!saved || stopped) {
        restorePendingReturn(world, key, pending);
        pending.retryAt = platform.nowMillis() + 5_000L;
        pending.running = false;
        return;
      }
      Runnable delivery = () -> {
        try {
          handOut.run();
        } catch (RuntimeException failure) {
          pending.running = false;
          if (!pending.started) {
            restorePendingReturn(world, key, pending);
          }
          Adapt.warn("Could not resume the pending Golemancy return " + key + "; its stored record is retained.", failure);
        }
      };
      if (!platform.runAt(key.center(world), delivery, 0)) {
        pending.running = false;
        restorePendingReturn(world, key, pending);
      }
    }, delayTicks);
  }

  private void restorePendingReturn(World world, NodeKey key, PendingReturn pending) {
    if (!returnWorldAvailable(world)) {
      return;
    }
    try {
      NodeFaces faces = platform.read(world, key.x(), key.y(), key.z());
      NodeRecord stored = faces.get(key.face());
      if (stored != null && stored.owner().equals(pending.record.owner()) && RETURN_DELIVERING.equals(stored.data().get(RETURN_STATE))) {
        platform.write(world, key.x(), key.y(), key.z(), faces.with(returnState(stored, RETURN_PENDING)));
        platform.async(() -> platform.savePlate(world, key.x(), key.z()), 0);
      }
    } catch (RuntimeException failure) {
      Adapt.warn("Could not restore the unstarted return of Golemancy node " + key + "; its stored record is retained.", failure);
    }
  }

  private boolean returnWorldAvailable(World world) {
    return !stopped && platform.world(world.getUID()) == world;
  }

  private static void warnUnresolvedReturn(NodeKey key) {
    Adapt.warn("Golemancy return " + returnId(key) + " needs an operator delivery check. Use /golem returns id=" + returnId(key)
        + " outcome=delivered to clear a confirmed delivery, or outcome=missing only after confirming no item was delivered.");
  }

  private static NodeRecord returnState(NodeRecord record, String state) {
    Map<String, String> data = new LinkedHashMap<>(record.data());
    data.put(RETURN_STATE, state);
    return record.toBuilder().data(data).build();
  }

  private void giveBack(Player owner, World world, NodeKey key, NodeType type) {
    ItemStack item = GolemItems.node(type);
    if (owner == null || !owner.isOnline() || !platform.owns(owner)) {
      world.dropItemNaturally(dropLocation(world, key), item);
      return;
    }
    for (ItemStack leftover : owner.getInventory().addItem(item).values()) {
      owner.getWorld().dropItemNaturally(owner.getLocation(), leftover);
    }
    owner.saveData();
  }

  private static Location dropLocation(World world, NodeKey key) {
    BlockFace face = key.face();
    return key.center(world).add(face.getModX() * DROP_FACE_DISTANCE, face.getModY() * DROP_FACE_DISTANCE, face.getModZ() * DROP_FACE_DISTANCE);
  }

  private void forget(List<NodeKey> keys) {
    for (NodeKey key : keys) {
      scans.remove(key);
      if (scanHook != null) {
        scanHook.invalidated(key);
      }
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
    for (Map.Entry<NodeKey, PendingReturn> entry : pendingReturns.entrySet()) {
      World world = platform.world(entry.getKey().world());
      if (world != null && !entry.getValue().running && !entry.getValue().started && entry.getValue().record.owner().equals(owner)) {
        restorePendingReturn(world, entry.getKey(), entry.getValue());
        resumeReturn(world, entry.getKey(), entry.getValue());
      }
    }
    boolean changed = false;
    for (GolemRoster.NodeEntry entry : roster.get().nodes()) {
      World world = platform.world(entry.block().world());
      if (world == null) {
        continue;
      }
      NodeRecord record;
      try {
        record = platform.read(world, entry.block().x(), entry.block().y(), entry.block().z()).get(entry.face());
      } catch (RuntimeException failure) {
        Adapt.warn("Could not reconcile Golemancy node " + entry.block() + "; its roster entry is retained.", failure);
        continue;
      }
      if (record == null || !record.owner().equals(owner) || record.type() != entry.type()) {
        changed |= roster.get().removeNode(entry.block(), entry.face());
      }
    }
    if (changed) {
      platform.rosterChanged(owner);
    }
  }

  private record ChunkRef(UUID world, long chunk) {
  }

  private static final class LoadRetry {
    private final World world;
    private final long generation;
    private volatile long nextRetry = Long.MAX_VALUE;

    private LoadRetry(World world, long generation) {
      this.world = world;
      this.generation = generation;
    }
  }

  public record ReturnRecord(NodeKey key, UUID owner, NodeType type) {
  }

  private static final class PendingReturn {
    private final NodeRecord record;
    private final Player player;
    private volatile boolean running;
    private volatile boolean started;
    private volatile boolean warned;
    private volatile long retryAt;

    private PendingReturn(NodeRecord record, Player player) {
      this.record = record;
      this.player = player;
    }
  }

  private static final class ScanState {
    private final long token;
    private final NodeScanCursor cursor = new NodeScanCursor();
    private final NodeDiscovery discovery = new NodeDiscovery();
    private final AtomicBoolean statusFailureLogged = new AtomicBoolean();
    private final AtomicBoolean scanFailureLogged = new AtomicBoolean();

    private ScanState(long token) {
      this.token = token;
    }
  }
}
