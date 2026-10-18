package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.volmlib.util.entity.StackExclusion;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class GolemRegistry extends TickedObject {
  private static final long TICK_INTERVAL_MILLIS = 1_000L;
  private static final long PRUNE_INTERVAL_MILLIS = 30_000L;
  private static final NamespacedKey BOUND_ONCE_KEY = new NamespacedKey("adapt", "golem_bound_once");

  private final GolemPlatform platform;
  private final Supplier<GolemSettings> settings;
  private final GolemLifecycle lifecycle;
  private final Object lock = new Object();
  private final Map<UUID, GolemHandle> handles = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> entityByGolem = new ConcurrentHashMap<>();
  private final Map<UUID, Set<UUID>> entitiesByOwner = new ConcurrentHashMap<>();
  private final Set<UUID> pendingOwners = ConcurrentHashMap.newKeySet();
  private final Set<UUID> awake = new LinkedHashSet<>();
  private final Set<UUID> capQueue = new LinkedHashSet<>();
  private final GolemMissingWatch missingWatch = new GolemMissingWatch();
  private final Set<UUID> mismatchWarned = ConcurrentHashMap.newKeySet();
  private long nextPruneAt;

  GolemRegistry(GolemPlatform platform, Supplier<GolemSettings> settings, GolemLifecycle lifecycle) {
    super("golemancy", "golem-registry", TICK_INTERVAL_MILLIS);
    this.platform = Objects.requireNonNull(platform);
    this.settings = Objects.requireNonNull(settings);
    this.lifecycle = Objects.requireNonNull(lifecycle);
  }

  public Optional<GolemHandle> handle(Entity entity) {
    if (entity == null) {
      return Optional.empty();
    }
    GolemHandle handle = handles.get(entity.getUniqueId());
    return handle == null || handle.isRetired() ? Optional.empty() : Optional.of(handle);
  }

  public Collection<GolemHandle> loadedOf(UUID owner) {
    Set<UUID> entityIds = owner == null ? null : entitiesByOwner.get(owner);
    if (entityIds == null) {
      return List.of();
    }
    List<GolemHandle> loaded = new ArrayList<>(entityIds.size());
    for (UUID entityId : entityIds) {
      GolemHandle handle = handles.get(entityId);
      if (handle != null && !handle.isRetired()) {
        loaded.add(handle);
      }
    }
    return List.copyOf(loaded);
  }

  public Collection<GolemHandle> loaded() {
    List<GolemHandle> loaded = new ArrayList<>(handles.size());
    for (GolemHandle handle : handles.values()) {
      if (!handle.isRetired()) {
        loaded.add(handle);
      }
    }
    return List.copyOf(loaded);
  }

  public boolean isBound(Entity entity) {
    return GolemState.has(entity);
  }

  public Optional<UUID> ownerOf(Entity entity) {
    Optional<GolemHandle> handle = handle(entity);
    if (handle.isPresent()) {
      return Optional.of(handle.get().owner());
    }
    return GolemState.read(entity).map(GolemState::owner);
  }

  public int activeCount() {
    synchronized (lock) {
      return awake.size();
    }
  }

  public Optional<BindBlock> bindBlock(Player owner, Mob golem, GolemBody body, GolemCoreType core) {
    if (GolemState.has(golem)) {
      return Optional.of(BindBlock.ALREADY_BOUND);
    }
    if (GolemBody.of(golem).orElse(null) != body) {
      return Optional.of(BindBlock.BODY_MISMATCH);
    }
    GolemSettings current = settings.get();
    if (!current.allows(body, core)) {
      return Optional.of(BindBlock.CORE_NOT_ALLOWED);
    }
    Optional<GolemRoster> roster = platform.roster(owner.getUniqueId());
    if (roster.isEmpty()) {
      return Optional.of(BindBlock.OWNER_DATA_UNAVAILABLE);
    }
    if (roster.get().golemCount() >= current.hardCapPerPlayer()) {
      return Optional.of(BindBlock.PLAYER_CAP);
    }
    if (boundInChunk(golem) >= current.maxBoundGolemsPerChunk()) {
      return Optional.of(BindBlock.CHUNK_CAP);
    }
    return Optional.empty();
  }

  public GolemHandle bind(Player owner, Mob golem, GolemBody body, GolemCoreType core) {
    requireOwned(golem, "bind");
    Optional<BindBlock> block = bindBlock(owner, golem, body, core);
    if (block.isPresent()) {
      throw new IllegalStateException("Golem " + golem.getUniqueId() + " cannot be bound by " + owner.getUniqueId() + ": " + block.get());
    }
    UUID ownerId = owner.getUniqueId();
    GolemRoster roster = platform.roster(ownerId).orElseThrow();
    Location at = golem.getLocation();
    UUID worldId = Objects.requireNonNull(at.getWorld()).getUID();
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), ownerId, body, core, GolemState.Anchor.at(at)));
    state.setPreviouslyBound(golem.getPersistentDataContainer().has(BOUND_ONCE_KEY, PersistentDataType.BYTE));
    writeBound(golem, state, Component.text(boundName(owner, body)));
    roster.add(new GolemRoster.Binding(state.id(), body, core, worldId, chunkKey(at), platform.nowMillis()));
    platform.rosterChanged(ownerId);
    return adoptBound(golem, state);
  }

  public Optional<BindBlock> restoreBlock(Mob golem, GolemSnapshot snapshot) {
    if (GolemState.has(golem)) {
      return Optional.of(BindBlock.ALREADY_BOUND);
    }
    GolemBody body = snapshot.body();
    if (GolemBody.of(golem).orElse(null) != body) {
      return Optional.of(BindBlock.BODY_MISMATCH);
    }
    GolemSettings current = settings.get();
    for (GolemCoreType core : snapshot.cores()) {
      if (!current.allows(body, core)) {
        return Optional.of(BindBlock.CORE_NOT_ALLOWED);
      }
    }
    if (platform.roster(snapshot.owner()).isEmpty()) {
      return Optional.of(BindBlock.OWNER_DATA_UNAVAILABLE);
    }
    if (boundInChunk(golem) >= current.maxBoundGolemsPerChunk()) {
      return Optional.of(BindBlock.CHUNK_CAP);
    }
    return Optional.empty();
  }

  public Optional<GolemHandle> restore(Player owner, Mob golem, Restore request) {
    requireOwned(golem, "restore");
    GolemSnapshot snapshot = request.snapshot();
    PackedGolem item = request.item();
    UUID ownerId = owner.getUniqueId();
    if (!ownerId.equals(snapshot.owner()) || !ownerId.equals(item.owner()) || !snapshot.id().equals(item.golemId())) {
      throw new IllegalStateException("Golem " + snapshot.id() + " in item " + item.golemId() + " of " + item.owner() + " cannot be restored for " + ownerId);
    }
    Optional<BindBlock> block = restoreBlock(golem, snapshot);
    if (block.isPresent()) {
      throw new IllegalStateException("Golem " + snapshot.id() + " cannot be restored into " + golem.getUniqueId() + ": " + block.get());
    }
    GolemRoster roster = platform.roster(ownerId).orElseThrow();
    if (!matchesEntry(roster, snapshot) || !roster.unstow(snapshot.id(), request.from(), item.state())) {
      return Optional.empty();
    }
    platform.rosterChanged(ownerId);
    try {
      GolemState state = GolemState.restored(snapshot, GolemState.Anchor.at(golem.getLocation()));
      writeBound(golem, state, snapshot.name().orElseGet(() -> Component.text(boundName(owner, snapshot.body()))));
      return Optional.of(adoptBound(golem, state));
    } catch (RuntimeException failure) {
      undoRestore(golem, ownerId, request);
      throw failure;
    }
  }

  public GolemSnapshot snapshot(GolemHandle handle) {
    Mob golem = handle.entity();
    GolemSnapshot.Traits traits = new GolemSnapshot.Traits(GolemSnapshot.serializeName(PaperCompat.customName(golem)), golem.getHealth(),
        platform.patinaStage(golem), platform.waxed(golem));
    return GolemSnapshot.capture(handle.state(), traits, platform.nowMillis());
  }

  public boolean release(GolemHandle handle) {
    if (handle.isRetired()) {
      return false;
    }
    Mob golem = handle.entity();
    requireOwned(golem, "release");
    Optional<GolemRoster> roster = platform.roster(handle.owner());
    GolemRoster.Status status = roster.flatMap(known -> known.golem(handle.id())).map(GolemRoster.GolemEntry::status).orElse(null);
    if (!handle.isValidated() || status != GolemRoster.Status.ACTIVE) {
      return false;
    }
    GolemState state = handle.state();
    List<ItemStack> drops = new ArrayList<>(state.carriedItems());
    for (GolemCoreType core : state.cores()) {
      drops.add(GolemItems.core(core));
    }
    for (GolemRune rune : state.runes()) {
      drops.add(GolemItems.rune(rune));
    }
    if (state.masterwork()) {
      drops.add(GolemItems.masterworkHeart());
    }
    int essence = state.essencePoints();
    Location at = golem.getLocation();
    revert(handle);
    forgetRosterEntry(handle);
    World world = golem.getWorld();
    for (ItemStack drop : drops) {
      world.dropItemNaturally(at, drop);
    }
    if (essence > 0) {
      returnEssence(golem, world, at, essence, platform.player(handle.owner()));
    }
    return true;
  }

  public boolean releaseEntry(UUID owner, UUID golem) {
    Optional<GolemRoster> roster = platform.roster(owner);
    if (roster.isEmpty() || !(roster.get().remove(golem, GolemRoster.Status.MISSING) || roster.get().remove(golem, GolemRoster.Status.SHATTERED))) {
      return false;
    }
    missingWatch.forget(golem);
    platform.rosterChanged(owner);
    return true;
  }

  public int masterworks(UUID owner) {
    return platform.roster(owner).map(GolemRoster::masterworkCount).orElse(0);
  }

  public Designation designate(GolemHandle handle, int maxMasterworks) {
    requireOwned(handle.entity(), "designate");
    GolemState state = handle.state();
    if (handle.isRetired()) {
      return Designation.UNAVAILABLE;
    }
    if (state.body() == GolemBody.BONE) {
      return Designation.BODY;
    }
    if (state.masterwork()) {
      return Designation.ALREADY;
    }
    if (maxMasterworks <= 0) {
      return Designation.LOCKED;
    }
    Optional<GolemRoster> roster = activeRoster(handle);
    if (roster.isEmpty()) {
      return Designation.UNAVAILABLE;
    }
    if (roster.get().masterworkCount() >= maxMasterworks) {
      return Designation.CAP;
    }
    if (!roster.get().markMasterwork(handle.id(), true)) {
      return Designation.UNAVAILABLE;
    }
    platform.rosterChanged(handle.owner());
    handle.setMasterwork(true);
    GolemStats.recompute(handle);
    return Designation.DESIGNATED;
  }

  public Optional<List<ItemStack>> undesignate(GolemHandle handle, int keptRunes) {
    requireOwned(handle.entity(), "undesignate");
    GolemState state = handle.state();
    if (handle.isRetired() || !state.masterwork()) {
      return Optional.empty();
    }
    Optional<GolemRoster> roster = activeRoster(handle);
    if (roster.isEmpty() || !roster.get().markMasterwork(handle.id(), false)) {
      return Optional.empty();
    }
    platform.rosterChanged(handle.owner());
    List<GolemCoreType> cores = state.cores();
    List<GolemRune> runes = state.runes();
    int kept = Math.clamp(keptRunes, 0, runes.size());
    List<ItemStack> returned = new ArrayList<>(cores.size() + runes.size() - kept);
    returned.add(GolemItems.masterworkHeart());
    for (GolemCoreType core : cores.subList(1, cores.size())) {
      returned.add(GolemItems.core(core));
    }
    for (GolemRune rune : runes.subList(kept, runes.size())) {
      returned.add(GolemItems.rune(rune));
    }
    List<GolemCoreType> primary = List.of(state.primaryCore());
    state.setCores(primary);
    state.setRunes(runes.subList(0, kept));
    state.setMasterwork(false);
    state.setMode(GolemMode.keptOrDefault(primary, state.mode()));
    handle.flush();
    GolemStats.recompute(handle);
    return Optional.of(List.copyOf(returned));
  }

  public Optional<GolemMastery.CoreRefusal> addCore(GolemHandle handle, GolemCoreType core) {
    requireOwned(handle.entity(), "addCore");
    if (handle.isRetired() || activeRoster(handle).isEmpty()) {
      return Optional.of(GolemMastery.CoreRefusal.UNAVAILABLE);
    }
    GolemState state = handle.state();
    List<GolemCoreType> cores = state.cores();
    Optional<GolemMastery.CoreRefusal> refusal = GolemMastery.coreRefusal(cores, core, GolemMastery.perks(handle).dualCore(),
        settings.get().allows(state.body(), core), GolemMastery.coreUnlocked(handle.owner(), core));
    if (refusal.isPresent()) {
      return refusal;
    }
    handle.setCores(List.of(state.primaryCore(), core));
    return Optional.empty();
  }

  public boolean removeCore(GolemHandle handle, GolemCoreType core) {
    requireOwned(handle.entity(), "removeCore");
    GolemState state = handle.state();
    List<GolemCoreType> cores = state.cores();
    if (handle.isRetired() || cores.size() < GolemState.MAX_CORES || cores.getLast() != core) {
      return false;
    }
    List<GolemCoreType> primary = List.of(cores.getFirst());
    state.setCores(primary);
    state.setMode(GolemMode.keptOrDefault(primary, state.mode()));
    handle.flush();
    return true;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesLoadEvent e) {
    for (Entity entity : e.getEntities()) {
      if (entity instanceof Mob mob && adoptable(mob)) {
        adopt(mob);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent e) {
    for (Entity entity : e.getEntities()) {
      handle(entity).ifPresent(this::detach);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent e) {
    if (!missingWatch.hasExpectations()) {
      return;
    }
    Chunk chunk = e.getChunk();
    missingWatch.chunkLoaded(chunk.getWorld().getUID(), GolemRoster.chunkKey(chunk.getX(), chunk.getZ()), platform.nowMillis());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    handle(e.getEntity()).ifPresent(handle -> die(handle, e));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    joinOwner(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void on(PlayerQuitEvent e) {
    UUID owner = e.getPlayer().getUniqueId();
    pendingOwners.remove(owner);
    missingWatch.forgetOwner(owner);
    Optional<GolemRoster> roster = platform.roster(owner);
    long now = platform.nowMillis();
    for (GolemHandle handle : loadedOf(owner)) {
      handle.setValidated(false);
      GolemHandle.ChunkPosition position = platform.owns(handle.entity())
          ? handle.trackPosition(handle.entity().getLocation())
          : handle.position();
      if (roster.isPresent() && position != null) {
        roster.get().seen(handle.id(), position.world(), position.chunkKey(), now);
      }
      runOwned(handle, () -> evaluate(handle, false));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(WorldSaveEvent e) {
    UUID world = e.getWorld().getUID();
    for (GolemHandle handle : handles.values()) {
      GolemHandle.ChunkPosition position = handle.position();
      if (handle.isDirty() && position != null && world.equals(position.world())) {
        runOwned(handle, handle::flushIfDirty);
      }
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !handles.isEmpty() || !pendingOwners.isEmpty() || missingWatch.hasDeadlines();
  }

  @Override
  public void onTick() {
    long now = platform.nowMillis();
    retryPendingOwners();
    wakeQueued();
    markMissing(missingWatch.expired(now));
    if (now >= nextPruneAt) {
      nextPruneAt = now + PRUNE_INTERVAL_MILLIS;
      pruneInvalid();
    }
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    List<Listener> listeners = new ArrayList<>(3);
    listeners.add(new GolemGuardListener(platform::player, this::handle));
    if (PaperCompat.hasClass("com.destroystokyo.paper.event.entity.EntityAddToWorldEvent")) {
      listeners.add(new PaperEntityListener(this));
    }
    if (PaperCompat.hasClass(CopperTransportGuard.EVENT_CLASS)) {
      listeners.add(new CopperTransportGuard());
    }
    return listeners;
  }

  static boolean adoptable(Mob mob) {
    return GolemState.has(mob) || GolemRitualMarker.has(mob);
  }

  void adopt(Mob mob) {
    AttributeInstance scale = platform.scaleOf(mob);
    GolemRitualMarker.recover(mob, scale);
    if (handle(mob).isPresent()) {
      return;
    }
    Optional<GolemState> state = GolemState.read(mob);
    if (state.isEmpty()) {
      return;
    }
    if (GolemBody.of(mob).orElse(null) != state.get().body()) {
      if (mismatchWarned.add(mob.getUniqueId())) {
        Adapt.warn("Ignoring bound golem " + mob.getUniqueId() + " because its adapt:golem body " + state.get().body().id() + " does not match the entity.");
      }
      return;
    }
    if (scale != null) {
      GolemScale.clearStepModifier(scale);
    }
    GolemHandle handle = new GolemHandle(mob, state.get(), platform);
    if (index(handle)) {
      evaluate(handle, platform.isOnline(handle.owner()));
    }
  }

  void adoptLater(Entity entity) {
    if (entity instanceof Mob mob && GolemState.has(mob) && handle(mob).isEmpty()) {
      platform.runEntity(mob, () -> adopt(mob), 1);
    }
  }

  void detach(GolemHandle handle) {
    if (handle.isRetired()) {
      return;
    }
    handle.flush();
    rememberPosition(handle, true);
    unindex(handle);
  }

  void joinOnlineOwners() {
    for (UUID owner : platform.onlineOwners()) {
      joinOwner(owner);
    }
  }

  void enforceCap() {
    List<GolemHandle> excess = new ArrayList<>();
    synchronized (lock) {
      int over = awake.size() - settings.get().serverMaxActiveGolems();
      if (over <= 0) {
        return;
      }
      List<UUID> oldestFirst = new ArrayList<>(awake);
      for (int index = oldestFirst.size() - 1; index >= 0 && excess.size() < over; index--) {
        UUID entityId = oldestFirst.get(index);
        awake.remove(entityId);
        GolemHandle handle = handles.get(entityId);
        if (handle != null) {
          excess.add(handle);
        }
      }
    }
    for (GolemHandle handle : excess) {
      runOwned(handle, () -> applyDormancy(handle, DormancyReason.SERVER_CAP));
    }
  }

  boolean flushAll(long timeoutMillis) {
    List<CompletableFuture<Boolean>> completions = new ArrayList<>(handles.size());
    for (GolemHandle handle : List.copyOf(handles.values())) {
      CompletableFuture<Boolean> completion = new CompletableFuture<>();
      completions.add(completion);
      Runnable flush = () -> completion.complete(sleepForShutdown(handle));
      if (platform.owns(handle.entity())) {
        flush.run();
      } else if (!platform.runEntity(handle.entity(), flush, 0)) {
        completion.complete(false);
      }
    }
    boolean flushed = awaitFlushes(completions, timeoutMillis);
    synchronized (lock) {
      for (GolemHandle handle : handles.values()) {
        handle.retire();
      }
      handles.clear();
      entityByGolem.clear();
      entitiesByOwner.clear();
      awake.clear();
      capQueue.clear();
      pendingOwners.clear();
    }
    return flushed;
  }

  private boolean sleepForShutdown(GolemHandle handle) {
    try {
      rememberPosition(handle, false);
      putToSleep(handle);
      lifecycle.asleep(handle);
      lifecycle.detached(handle);
      return true;
    } catch (RuntimeException failure) {
      Adapt.warn("Failed to save bound golem " + handle.id() + " at shutdown.", failure);
      return false;
    }
  }

  private boolean awaitFlushes(List<CompletableFuture<Boolean>> completions, long timeoutMillis) {
    if (completions.isEmpty()) {
      return true;
    }
    try {
      CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new)).get(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      Adapt.warn("Interrupted while saving bound golems at shutdown.", interrupted);
      return false;
    } catch (ExecutionException | TimeoutException incomplete) {
      Adapt.warn("Bound golem saving did not complete before shutdown.", incomplete);
      return false;
    }
    for (CompletableFuture<Boolean> completion : completions) {
      if (!completion.join()) {
        return false;
      }
    }
    return true;
  }

  private void evaluate(GolemHandle handle, boolean ownerOnline) {
    if (handle.isRetired()) {
      return;
    }
    UUID owner = handle.owner();
    Optional<GolemRoster> roster = ownerOnline ? platform.roster(owner) : Optional.empty();
    GolemRoster.Status status = roster.flatMap(known -> known.golem(handle.id())).map(GolemRoster.GolemEntry::status).orElse(null);
    GolemValidation.Verdict verdict = GolemValidation.verdict(isDuplicate(handle), roster.isPresent(), status);
    switch (verdict) {
      case REVERT -> {
        revert(handle);
        return;
      }
      case KEEP, RESTORE -> {
        handle.setValidated(true);
        recordSighting(handle, roster.get());
        if (verdict == GolemValidation.Verdict.RESTORE) {
          platform.rosterChanged(owner);
        }
      }
      case WAIT -> {
        handle.setValidated(false);
        if (ownerOnline) {
          pendingOwners.add(owner);
        }
      }
    }
    boolean offlineGuard = GolemDormancy.offlineGuard(settings.get().guardWhileOwnerOffline(), handle.core());
    applyDormancy(handle, GolemDormancy.reason(ownerOnline, roster.isPresent(), offlineGuard, () -> claimAwake(handle)));
  }

  private void applyDormancy(GolemHandle handle, DormancyReason reason) {
    if (handle.isRetired()) {
      return;
    }
    handle.setDormancyReason(reason);
    synchronized (lock) {
      if (reason != DormancyReason.NONE) {
        awake.remove(handle.entityId());
      }
      if (reason == DormancyReason.SERVER_CAP) {
        capQueue.add(handle.entityId());
      } else {
        capQueue.remove(handle.entityId());
      }
    }
    switch (GolemDormancy.transition(!handle.state().dormant(), reason)) {
      case WAKE -> wake(handle);
      case SLEEP -> putToSleep(handle);
      case NONE -> {
      }
    }
    GolemColossus.reconcile(handle);
    if (reason == DormancyReason.NONE) {
      GolemStats.recompute(handle);
      handle.settleFullHealth();
      lifecycle.awake(handle);
    } else {
      lifecycle.asleep(handle);
    }
  }

  private void wake(GolemHandle handle) {
    Mob golem = handle.entity();
    golem.setAware(true);
    golem.setInvulnerable(false);
    handle.state().setDormant(false);
    handle.flush();
  }

  private void putToSleep(GolemHandle handle) {
    Mob golem = handle.entity();
    golem.setAware(false);
    golem.setTarget(null);
    PaperCompat.stopPathfinding(golem);
    golem.setInvulnerable(settings.get().dormantInvulnerable());
    handle.state().setDormant(true);
    handle.flush();
  }

  private boolean claimAwake(GolemHandle handle) {
    synchronized (lock) {
      UUID entityId = handle.entityId();
      if (handle.isRetired()) {
        return false;
      }
      if (awake.contains(entityId)) {
        return true;
      }
      if (awake.size() >= settings.get().serverMaxActiveGolems()) {
        return false;
      }
      if (!capQueue.isEmpty() && !capQueue.contains(entityId)) {
        return false;
      }
      capQueue.remove(entityId);
      awake.add(entityId);
      return true;
    }
  }

  private boolean isDuplicate(GolemHandle handle) {
    synchronized (lock) {
      UUID primary = entityByGolem.get(handle.id());
      if (primary == null || primary.equals(handle.entityId())) {
        entityByGolem.put(handle.id(), handle.entityId());
        return false;
      }
      GolemHandle other = handles.get(primary);
      if (other == null || other.isRetired() || !other.entity().isValid()) {
        entityByGolem.put(handle.id(), handle.entityId());
        return false;
      }
      return true;
    }
  }

  private boolean index(GolemHandle handle) {
    synchronized (lock) {
      GolemHandle previous = handles.get(handle.entityId());
      if (previous != null && !previous.isRetired()) {
        return false;
      }
      handles.put(handle.entityId(), handle);
      entityByGolem.putIfAbsent(handle.id(), handle.entityId());
      entitiesByOwner.computeIfAbsent(handle.owner(), owner -> ConcurrentHashMap.newKeySet()).add(handle.entityId());
    }
    missingWatch.forget(handle.id());
    return true;
  }

  private void unindex(GolemHandle handle) {
    boolean removed;
    synchronized (lock) {
      handle.retire();
      awake.remove(handle.entityId());
      capQueue.remove(handle.entityId());
      removed = handles.remove(handle.entityId(), handle);
      entityByGolem.remove(handle.id(), handle.entityId());
      Set<UUID> owned = entitiesByOwner.get(handle.owner());
      if (owned != null) {
        owned.remove(handle.entityId());
        if (owned.isEmpty()) {
          entitiesByOwner.remove(handle.owner(), owned);
        }
      }
    }
    if (removed) {
      lifecycle.detached(handle);
    }
  }

  private void revert(GolemHandle handle) {
    Mob golem = handle.entity();
    double pool = handle.hostileDamagePool();
    unindex(handle);
    GolemState.clear(golem);
    HostileDamagePool.putUnbound(golem, pool);
    AttributeInstance scale = platform.scaleOf(golem);
    if (scale != null) {
      GolemScale.clearModifier(scale);
      GolemScale.clearStepModifier(scale);
    }
    GolemStats.clear(handle);
    PaperCompat.customName(golem, null);
    StackExclusion.include(golem);
    golem.setAware(true);
    golem.setInvulnerable(false);
  }

  private void die(GolemHandle handle, EntityDeathEvent e) {
    boolean validated = handle.isValidated();
    unindex(handle);
    if (!validated) {
      return;
    }
    GolemSettings current = settings.get();
    GolemState state = handle.state();
    List<ItemStack> drops = e.getDrops();
    drops.addAll(state.carriedItems());
    if (state.essencePoints() > 0) {
      e.setDroppedExp((int) Math.min(Integer.MAX_VALUE, (long) e.getDroppedExp() + state.essencePoints()));
    }
    if (GolemMend.shatters(handle) && shatter(handle)) {
      return;
    }
    GolemDeathDrops.Roll roll = GolemDeathDrops.roll(state.cores(), state.runes(), current.deathDropCoreChance(), current.deathDropRuneChance(),
        () -> ThreadLocalRandom.current().nextDouble());
    for (GolemCoreType core : roll.cores()) {
      drops.add(GolemItems.core(core));
    }
    for (GolemRune rune : roll.runes()) {
      drops.add(GolemItems.rune(rune));
    }
    if (GolemDeathDrops.heart(state.masterwork(), GolemMastery.heartDropChance(), () -> ThreadLocalRandom.current().nextDouble())) {
      drops.add(GolemItems.masterworkHeart());
    }
    forgetRosterEntry(handle);
  }

  private boolean shatter(GolemHandle handle) {
    UUID owner = handle.owner();
    Optional<GolemRoster> roster = platform.roster(owner);
    if (roster.isEmpty()) {
      return false;
    }
    String itemState = snapshot(handle).withoutCargo().toJson();
    if (!roster.get().stow(handle.id(), GolemRoster.Status.SHATTERED, itemState)) {
      return false;
    }
    missingWatch.forget(handle.id());
    platform.rosterChanged(owner);
    Location at = handle.entity().getLocation();
    platform.dropOwnerBound(at, GolemItems.shatteredCore(new PackedGolem(handle.id(), owner, itemState)), owner);
    GolemMend.shattered(handle, at);
    return true;
  }

  private void undoRestore(Mob golem, UUID owner, Restore request) {
    Optional<GolemHandle> adopted = handle(golem);
    if (adopted.isPresent()) {
      revert(adopted.get());
    } else {
      GolemState.clear(golem);
    }
    Optional<GolemRoster> roster = platform.roster(owner);
    if (roster.isPresent() && roster.get().stow(request.snapshot().id(), request.from(), request.item().state())) {
      platform.rosterChanged(owner);
    }
  }

  private void writeBound(Mob golem, GolemState state, Component name) {
    state.setHostileDamagePool(HostileDamagePool.takeUnbound(golem));
    state.write(golem);
    golem.getPersistentDataContainer().set(BOUND_ONCE_KEY, PersistentDataType.BYTE, (byte) 1);
    StackExclusion.exclude(golem);
    golem.setPersistent(true);
    golem.setRemoveWhenFarAway(false);
    PaperCompat.customName(golem, name);
    AttributeInstance scale = platform.scaleOf(golem);
    if (scale != null) {
      GolemScale.setModifier(scale, GolemScale.modifierAmount(settings.get().bodyScale(state.body())));
    }
  }

  private GolemHandle adoptBound(Mob golem, GolemState state) {
    GolemHandle handle = new GolemHandle(golem, state, platform);
    index(handle);
    evaluate(handle, true);
    return handle;
  }

  private void returnEssence(Mob golem, World world, Location at, int points, Player owner) {
    AtomicBoolean settled = new AtomicBoolean();
    Runnable drop = () -> {
      if (settled.compareAndSet(false, true)) {
        world.spawn(at, ExperienceOrb.class, orb -> orb.setExperience(points));
      }
    };
    Runnable give = () -> {
      if (settled.compareAndSet(false, true)) {
        owner.giveExp(points);
      }
    };
    AtomicBoolean fellBack = new AtomicBoolean();
    Runnable dropAtLastLocation = () -> {
      if (fellBack.compareAndSet(false, true) && !platform.runAt(at, drop)) {
        Adapt.warn("Released golem " + golem.getUniqueId() + " could not drop its " + points + " experience points of essence at " + at + "; the essence was lost.");
      }
    };
    Runnable ownerGone = () -> {
      if (!platform.runEntity(golem, drop, dropAtLastLocation)) {
        dropAtLastLocation.run();
      }
    };
    if (owner == null || !owner.isOnline() || !platform.runEntity(owner, give, ownerGone)) {
      drop.run();
    }
  }

  private void forgetRosterEntry(GolemHandle handle) {
    missingWatch.forget(handle.id());
    UUID owner = handle.owner();
    Optional<GolemRoster> roster = platform.roster(owner);
    if (roster.isPresent() && roster.get().remove(handle.id())) {
      platform.rosterChanged(owner);
    }
  }

  private void joinOwner(UUID owner) {
    Optional<GolemRoster> roster = platform.roster(owner);
    if (roster.isEmpty()) {
      pendingOwners.add(owner);
      return;
    }
    pendingOwners.remove(owner);
    for (GolemHandle handle : loadedOf(owner)) {
      runOwned(handle, () -> evaluate(handle, true));
    }
    expectUnloaded(owner, roster.get());
  }

  private void expectUnloaded(UUID owner, GolemRoster roster) {
    long now = platform.nowMillis();
    Set<GolemHandle.ChunkPosition> swept = new HashSet<>();
    for (GolemRoster.GolemEntry entry : roster.golems()) {
      if (entry.status() != GolemRoster.Status.ACTIVE || entry.world() == null || entityByGolem.containsKey(entry.id())) {
        continue;
      }
      missingWatch.expect(new GolemMissingWatch.Expected(owner, entry.id(), entry.world(), entry.chunkKey()));
      if (!platform.isChunkLoaded(entry.world(), entry.chunkKey())) {
        continue;
      }
      missingWatch.chunkLoaded(entry.world(), entry.chunkKey(), now);
      if (swept.add(new GolemHandle.ChunkPosition(entry.world(), entry.chunkKey()))) {
        platform.sweepChunk(entry.world(), entry.chunkKey(), this::adopt);
      }
    }
  }

  private void retryPendingOwners() {
    for (UUID owner : List.copyOf(pendingOwners)) {
      if (!platform.isOnline(owner)) {
        pendingOwners.remove(owner);
      } else if (platform.roster(owner).isPresent()) {
        joinOwner(owner);
      }
    }
  }

  private void wakeQueued() {
    List<GolemHandle> next;
    synchronized (lock) {
      int room = settings.get().serverMaxActiveGolems() - awake.size();
      if (room <= 0 || capQueue.isEmpty()) {
        return;
      }
      next = new ArrayList<>(Math.min(room, capQueue.size()));
      Iterator<UUID> queued = capQueue.iterator();
      while (queued.hasNext() && next.size() < room) {
        GolemHandle handle = handles.get(queued.next());
        if (handle == null || handle.isRetired()) {
          queued.remove();
          continue;
        }
        next.add(handle);
      }
    }
    for (GolemHandle handle : next) {
      runOwned(handle, () -> evaluate(handle, platform.isOnline(handle.owner())));
    }
  }

  private void pruneInvalid() {
    for (GolemHandle handle : List.copyOf(handles.values())) {
      if (!handle.entity().isValid()) {
        unindex(handle);
      }
    }
  }

  private void markMissing(List<GolemMissingWatch.Expected> expired) {
    for (GolemMissingWatch.Expected expected : expired) {
      if (entityByGolem.containsKey(expected.golem()) || !platform.isOnline(expected.owner())) {
        continue;
      }
      Optional<GolemRoster> roster = platform.roster(expected.owner());
      if (roster.isEmpty()) {
        continue;
      }
      Optional<GolemRoster.GolemEntry> entry = roster.get().golem(expected.golem());
      if (entry.isEmpty() || entry.get().chunkKey() != expected.chunkKey() || !expected.world().equals(entry.get().world())) {
        continue;
      }
      if (roster.get().transition(expected.golem(), GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING)) {
        platform.rosterChanged(expected.owner());
      }
    }
  }

  private void recordSighting(GolemHandle handle, GolemRoster roster) {
    GolemHandle.ChunkPosition position = handle.trackPosition(handle.entity().getLocation());
    if (position != null) {
      roster.seen(handle.id(), position.world(), position.chunkKey(), platform.nowMillis());
    }
    if (roster.markMasterwork(handle.id(), handle.state().masterwork())) {
      platform.rosterChanged(handle.owner());
    }
  }

  private Optional<GolemRoster> activeRoster(GolemHandle handle) {
    if (!handle.isValidated()) {
      return Optional.empty();
    }
    return platform.roster(handle.owner())
        .filter(roster -> roster.golem(handle.id()).map(GolemRoster.GolemEntry::status).orElse(null) == GolemRoster.Status.ACTIVE);
  }

  private void rememberPosition(GolemHandle handle, boolean expectReturn) {
    UUID owner = handle.owner();
    if (!platform.isOnline(owner)) {
      return;
    }
    Optional<GolemRoster> roster = platform.roster(owner);
    GolemHandle.ChunkPosition position = handle.trackPosition(handle.entity().getLocation());
    if (roster.isEmpty() || position == null) {
      return;
    }
    if (roster.get().seen(handle.id(), position.world(), position.chunkKey(), platform.nowMillis()) && expectReturn) {
      missingWatch.expect(new GolemMissingWatch.Expected(owner, handle.id(), position.world(), position.chunkKey()));
    }
  }

  private void runOwned(GolemHandle handle, Runnable task) {
    Mob golem = handle.entity();
    if (platform.owns(golem)) {
      task.run();
      return;
    }
    platform.runEntity(golem, () -> {
      if (!handle.isRetired()) {
        task.run();
      }
    }, 0);
  }

  private void requireOwned(Mob golem, String operation) {
    if (!platform.owns(golem)) {
      throw new IllegalStateException("Golem " + operation + " for " + golem.getUniqueId() + " must run on the golem's owning thread.");
    }
  }

  private int boundInChunk(Mob golem) {
    int bound = 0;
    for (Entity entity : golem.getLocation().getChunk().getEntities()) {
      if (entity != golem && GolemState.has(entity)) {
        bound++;
      }
    }
    return bound;
  }

  private static boolean matchesEntry(GolemRoster roster, GolemSnapshot snapshot) {
    return roster.golem(snapshot.id())
        .filter(entry -> entry.body() == snapshot.body() && entry.core() == snapshot.primaryCore())
        .isPresent();
  }

  private static long chunkKey(Location location) {
    return GolemRoster.chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }

  private static String boundName(Player owner, GolemBody body) {
    return AdaptLanguage.text(GolemancyMessages.GOLEM_NAME,
        untrusted("owner", owner.getName()),
        trusted("body", AdaptLanguage.text(body.displayName())));
  }

  public record Restore(PackedGolem item, GolemSnapshot snapshot, GolemRoster.Status from) {
    public Restore {
      Objects.requireNonNull(item);
      Objects.requireNonNull(snapshot);
      Objects.requireNonNull(from);
    }
  }

  public enum Designation {
    DESIGNATED,
    LOCKED,
    CAP,
    BODY,
    ALREADY,
    UNAVAILABLE
  }

  public enum BindBlock {
    ALREADY_BOUND,
    BODY_MISMATCH,
    CORE_NOT_ALLOWED,
    OWNER_DATA_UNAVAILABLE,
    PLAYER_CAP,
    CHUNK_CAP
  }

  static final class PaperEntityListener implements Listener {
    private final GolemRegistry registry;

    private PaperEntityListener(GolemRegistry registry) {
      this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityAddToWorldEvent e) {
      registry.adoptLater(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityRemoveFromWorldEvent e) {
      registry.handle(e.getEntity()).ifPresent(registry::detach);
    }
  }
}
