package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.api.golem.item.DormantGolem;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.event.world.WorldLifecycleEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.data.StackExclusion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class GolemRegistry extends TickedObject {
  private static final long TICK_INTERVAL_MILLIS = 1_000L;
  private static final long PRUNE_INTERVAL_MILLIS = 30_000L;
  private static final DataKey<String> PACKING_TOKEN_KEY = DataKey.of(Id.of("adapt", "golem_packing_token"), DataCodecs.STRING);
  private static final DataKey<Byte> PACKING_KEY = DataKey.of(Id.of("adapt", "golem_packing"), DataCodecs.BYTE);
  private static final DataKey<Byte> BOUND_ONCE_KEY = DataKey.of(Id.of("adapt", "golem_bound_once"), DataCodecs.BYTE);

  private final GolemPlatform platform;
  private final Supplier<GolemSettings> settings;
  private final GolemLifecycle lifecycle;
  private final Object lock = new Object();
  private final Map<UUID, GolemHandle> handles = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> entityByGolem = new ConcurrentHashMap<>();
  private final Map<UUID, Set<UUID>> entitiesByOwner = new ConcurrentHashMap<>();
  private final Set<UUID> pendingPackOwners = ConcurrentHashMap.newKeySet();
  private final Set<UUID> deliveringPacked = ConcurrentHashMap.newKeySet();
  private final Map<UUID, MobRef> packingBodies = new ConcurrentHashMap<>();
  private final Set<UUID> packingRecoveryScheduled = ConcurrentHashMap.newKeySet();
  private final Map<UUID, PackCompletion> packingCompletions = new ConcurrentHashMap<>();
  private final Set<UUID> packingInFlight = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingOwners = ConcurrentHashMap.newKeySet();
  private final Set<UUID> awake = new LinkedHashSet<>();
  private final Set<UUID> capQueue = new LinkedHashSet<>();
  private final GolemMissingWatch missingWatch = new GolemMissingWatch();
  private final Set<UUID> mismatchWarned = ConcurrentHashMap.newKeySet();
  private final Set<UUID> invalidOnce = ConcurrentHashMap.newKeySet();
  private long nextPruneAt;

  GolemRegistry(GolemPlatform platform, Supplier<GolemSettings> settings, GolemLifecycle lifecycle) {
    super("golemancy", "golem-registry", TICK_INTERVAL_MILLIS);
    this.platform = Objects.requireNonNull(platform);
    this.settings = Objects.requireNonNull(settings);
    this.lifecycle = Objects.requireNonNull(lifecycle);
  }

  public Optional<GolemHandle> handle(EntityRef entity) {
    if (entity == null) {
      return Optional.empty();
    }
    GolemHandle handle = handles.get(entity.uuid());
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
    return Collections.unmodifiableList(loaded);
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

  public Optional<UUID> ownerOf(EntityRef entity) {
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

  public Optional<BindBlock> bindBlock(PlayerRef owner, MobRef golem, GolemBody body, GolemCoreType core) {
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
    Optional<GolemRoster> roster = platform.roster(owner.uuid());
    if (roster.isEmpty()) {
      return Optional.of(BindBlock.OWNER_DATA_UNAVAILABLE);
    }
    if (roster.get().golemCount() >= current.hardCapPerPlayer()) {
      return Optional.of(BindBlock.PLAYER_CAP);
    }
    if (boundInChunk(golem.location(), golem) >= current.maxBoundGolemsPerChunk()) {
      return Optional.of(BindBlock.CHUNK_CAP);
    }
    return Optional.empty();
  }

  public GolemHandle bind(PlayerRef owner, MobRef golem, GolemBody body, GolemCoreType core) {
    requireOwned(golem, "bind");
    Optional<BindBlock> block = bindBlock(owner, golem, body, core);
    if (block.isPresent()) {
      throw new IllegalStateException("Golem " + golem.uuid() + " cannot be bound by " + owner.uuid() + ": " + block.get());
    }
    UUID ownerId = owner.uuid();
    GolemRoster roster = platform.roster(ownerId).orElseThrow();
    Loc at = golem.location();
    UUID worldId = Objects.requireNonNull(at.world()).uid();
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), ownerId, body, core, GolemAnchor.at(at)));
    state.setPreviouslyBound(golem.data().has(BOUND_ONCE_KEY));
    writeBound(golem, state, GolemSnapshot.textName(boundName(owner, body)));
    roster.add(new GolemRoster.Binding(state.id(), body, core, worldId, chunkKey(at), platform.nowMillis()));
    platform.rosterChanged(ownerId);
    if (body == GolemBody.BONE) {
      platform.upkeep(ownerId, upkept(roster));
    }
    return adoptBound(golem, state);
  }

  public Optional<BindBlock> restoreBlock(MobRef golem, GolemSnapshot snapshot) {
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
    if (boundInChunk(golem.location(), golem) >= current.maxBoundGolemsPerChunk()) {
      return Optional.of(BindBlock.CHUNK_CAP);
    }
    return Optional.empty();
  }

  public Optional<BindBlock> awakenBlock(Loc at, GolemSnapshot snapshot) {
    GolemSettings current = settings.get();
    for (GolemCoreType core : snapshot.cores()) {
      if (!current.allows(snapshot.body(), core)) {
        return Optional.of(BindBlock.CORE_NOT_ALLOWED);
      }
    }
    if (platform.roster(snapshot.owner()).isEmpty()) {
      return Optional.of(BindBlock.OWNER_DATA_UNAVAILABLE);
    }
    if (boundInChunk(at, null) >= current.maxBoundGolemsPerChunk()) {
      return Optional.of(BindBlock.CHUNK_CAP);
    }
    if (activeCount() >= current.serverMaxActiveGolems()) {
      return Optional.of(BindBlock.SERVER_CAP);
    }
    return Optional.empty();
  }

  public Optional<GolemHandle> restore(PlayerRef owner, MobRef golem, Restore request) {
    requireOwned(golem, "restore");
    UUID ownerId = owner.uuid();
    GolemSnapshot snapshot = restorable(ownerId, request);
    Optional<BindBlock> block = restoreBlock(golem, snapshot);
    if (block.isPresent()) {
      throw new IllegalStateException("Golem " + snapshot.id() + " cannot be restored into " + golem.uuid() + ": " + block.get());
    }
    if (!claim(ownerId, snapshot, request)) {
      return Optional.empty();
    }
    try {
      platform.rosterChanged(ownerId);
      GolemState state = GolemState.restored(snapshot, GolemAnchor.at(golem.location()), sightingOf(ownerId, snapshot.id()));
      writeBound(golem, state, restoredName(owner, snapshot));
      return Optional.of(adoptBound(golem, state));
    } catch (Throwable failure) {
      try {
        undoRestore(golem, ownerId, request);
      } catch (Throwable undo) {
        failure.addSuppressed(undo);
      }
      throw failure;
    }
  }

  public Optional<GolemHandle> awaken(PlayerRef owner, Loc at, Restore request, Consumer<MobRef> prepare) {
    if (!platform.owns(at)) {
      throw new IllegalStateException("Golem awaken at " + at + " must run on the thread that owns that spot.");
    }
    UUID ownerId = owner.uuid();
    GolemSnapshot snapshot = restorable(ownerId, request);
    Optional<BindBlock> block = awakenBlock(at, snapshot);
    if (block.isPresent()) {
      throw new IllegalStateException("Golem " + snapshot.id() + " cannot awaken at " + at + ": " + block.get());
    }
    if (!claim(ownerId, snapshot, request)) {
      return Optional.empty();
    }
    AtomicReference<MobRef> spawned = new AtomicReference<>();
    try {
      platform.rosterChanged(ownerId);
      GolemState state = GolemState.restored(snapshot, GolemAnchor.at(at), sightingOf(ownerId, snapshot.id()));
      String name = restoredName(owner, snapshot);
      MobRef golem = platform.spawn(at, snapshot.body().entityType(), body -> {
        spawned.set(body);
        writeBound(body, state, name);
        prepare.accept(body);
      });
      if (!golem.isValid()) {
        throw new IllegalStateException("The body of golem " + snapshot.id() + " was refused when it spawned at " + at);
      }
      return Optional.of(adoptBound(golem, state));
    } catch (Throwable failure) {
      try {
        undoAwaken(spawned.get(), ownerId, request);
      } catch (Throwable undo) {
        failure.addSuppressed(undo);
      }
      throw failure;
    }
  }

  public CompletableFuture<Optional<Packing>> pack(GolemHandle handle) {
    if (handle.isRetired()) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    MobRef golem = handle.entity();
    requireOwned(golem, "pack");
    UUID owner = handle.owner();
    Optional<GolemRoster> roster = platform.roster(owner);
    GolemRoster.Status status = roster.flatMap(known -> known.golem(handle.id())).map(GolemRoster.GolemEntry::status).orElse(null);
    if (!handle.isValidated() || status != GolemRoster.Status.ACTIVE
        || roster.get().golem(handle.id()).map(GolemRoster.GolemEntry::revoked).orElse(true)) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    Loc at = golem.location();
    GolemAttribute maximum = platform.attribute(golem, GolemStat.MAX_HEALTH);
    double maxHealth = maximum == null ? golem.health() : maximum.value();
    GolemRoster known = roster.get();
    GolemSnapshot snapshot;
    PackedGolem packed;
    ItemRef item;
    boolean stowed = false;
    PackingBody body = new PackingBody(golem.isInvulnerable(), golem.isAware(), golem.canPickupItems());
    packingInFlight.add(handle.id());
    packingBodies.put(handle.id(), golem);
    try {
      if (!handle.flush()) {
        throw new IllegalStateException("Could not persist golem state before packing " + handle.id());
      }
      golem.data().set(PACKING_KEY, body.flags());
      golem.setInvulnerable(true);
      golem.setAware(false);
      golem.setCanPickupItems(false);
      golem.setTarget(null);
      unindex(handle);
      snapshot = snapshot(handle);
      packed = new PackedGolem(handle.id(), owner, snapshot.toPackedJson(UUID.randomUUID()));
      golem.data().set(PACKING_TOKEN_KEY, GolemRoster.digest(packed.state()));
      item = GolemItems.dormantGolem(new DormantGolem(packed, GolemItems.dormantBlock(snapshot.body(), snapshot.copperStage(), snapshot.waxed()),
          snapshot.cores(), snapshot.runes(), snapshot.health(), maxHealth));
      stowed = known.stow(handle.id(), GolemRoster.Status.PACKED, packed.state());
      if (stowed) {
        missingWatch.forget(handle.id());
        pendingPackOwners.add(owner);
        platform.rosterChanged(owner);
      }
    } catch (Throwable failure) {
      try {
        restorePackingBody(golem, body);
        forgetPacking(handle.id());
        undoPack(golem, owner, known, handle.id(), stowed);
      } catch (Throwable undo) {
        failure.addSuppressed(undo);
      }
      forgetPacking(handle.id());
      throw failure;
    }
    if (!stowed) {
      forgetPacking(handle.id());
      adopt(golem);
      restorePackingBody(golem, body);
      return CompletableFuture.completedFuture(Optional.empty());
    }
    Packing packing = new Packing(packed, snapshot, item, at);
    CompletableFuture<Optional<Packing>> completion = new CompletableFuture<>();
    CompletableFuture<Void> persisted;
    try {
      persisted = platform.checkpoint(owner);
    } catch (RuntimeException failure) {
      finishPack(golem, known, packing, body, completion, failure);
      return completion;
    }
    persisted.whenComplete((ignored, failure) -> {
      PackCompletion pending = new PackCompletion(known, packing, body, completion, failure, new AtomicBoolean());
      packingCompletions.put(handle.id(), pending);
      schedulePackCompletion(handle.id(), pending);
    });
    return completion;
  }

  private void schedulePackCompletion(UUID id, PackCompletion pending) {
    MobRef golem = packingBodies.get(id);
    if (golem == null || !pending.scheduled().compareAndSet(false, true)) {
      return;
    }
    Runnable finish = () -> finishPack(golem, pending.roster(), pending.packing(), pending.body(), pending.completion(), pending.failure());
    Runnable refused = () -> {
      pending.scheduled().set(false);
      if (pending.failure() != null) {
        try {
          if (pending.roster().transition(id, GolemRoster.Status.PACKED, GolemRoster.Status.ACTIVE)) {
            platform.rosterChanged(pending.packing().packed().owner());
          }
        } finally {
          pending.completion().completeExceptionally(pending.failure());
        }
      } else {
        pending.completion().complete(Optional.empty());
      }
    };
    if (!platform.runEntity(golem, finish, refused)) {
      refused.run();
    }
  }

  private void forgetPacking(UUID id) {
    packingRecoveryScheduled.remove(id);
    packingCompletions.remove(id);
    packingBodies.remove(id);
    packingInFlight.remove(id);
  }

  private void finishPack(MobRef golem, GolemRoster roster, Packing packing, PackingBody body,
                          CompletableFuture<Optional<Packing>> completion, Throwable failure) {
    String marker = golem.data().get(PACKING_TOKEN_KEY);
    if (!GolemRoster.digest(packing.packed().state()).equals(marker)) {
      forgetPacking(packing.packed().golemId());
      completion.complete(Optional.empty());
      return;
    }
    if (failure != null) {
      try {
        restorePackingBody(golem, body);
        forgetPacking(packing.packed().golemId());
        undoPack(golem, packing.packed().owner(), roster, packing.packed().golemId(), true);
      } catch (Throwable undo) {
        failure.addSuppressed(undo);
      }
      forgetPacking(packing.packed().golemId());
      completion.completeExceptionally(failure);
      return;
    }
    try {
      golem.remove();
      forgetPacking(packing.packed().golemId());
      completion.complete(Optional.of(packing));
    } catch (Throwable removal) {
      if (golem.isValid()) {
        try {
          restorePackingBody(golem, body);
          forgetPacking(packing.packed().golemId());
          undoPack(golem, packing.packed().owner(), roster, packing.packed().golemId(), true);
        } catch (Throwable undo) {
          removal.addSuppressed(undo);
        }
      }
      forgetPacking(packing.packed().golemId());
      completion.completeExceptionally(removal);
    }
  }

  private static void restorePackingBody(MobRef golem, PackingBody body) {
    golem.data().remove(PACKING_KEY);
    golem.data().remove(PACKING_TOKEN_KEY);
    golem.setInvulnerable(body.invulnerable());
    golem.setAware(body.aware());
    golem.setCanPickupItems(body.pickup());
  }

  public void deliverPacked(PlayerRef player, PackedGolem packed, ItemRef item) {
    Optional<GolemRoster> known = platform.roster(packed.owner());
    if (known.flatMap(roster -> roster.golem(packed.golemId())).flatMap(GolemRoster.GolemEntry::pendingPackedState)
        .filter(packed.state()::equals).isEmpty() || !deliveringPacked.add(packed.golemId())) {
      return;
    }
    Runnable retired = () -> deliveringPacked.remove(packed.golemId());
    boolean accepted = platform.runEntity(player, () -> deliverPackedOwned(player, packed, item), retired);
    if (!accepted) {
      retired.run();
    }
  }

  private void deliverPackedOwned(PlayerRef player, PackedGolem packed, ItemRef item) {
    GolemRoster roster = platform.roster(packed.owner()).orElse(null);
    if (roster == null || roster.golem(packed.golemId()).flatMap(GolemRoster.GolemEntry::pendingPackedState).filter(packed.state()::equals).isEmpty()) {
      deliveringPacked.remove(packed.golemId());
      return;
    }
    try {
      List<ItemRef> leftovers = player.inventory().addItem(item);
      for (ItemRef leftover : leftovers) {
        platform.dropOwnerBound(player.location(), leftover, packed.owner());
      }
      player.saveData();
      if (roster.completePackedDelivery(packed.golemId(), packed.state())) {
        platform.rosterChanged(packed.owner());
      }
      if (!leftovers.isEmpty()) {
        AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(GolemancyMessages.STASIS_DROPPED));
      } else {
        GolemSnapshot.fromJson(packed.state()).ifPresent(snapshot -> AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(GolemancyMessages.STASIS_PACKED,
            trusted("body", AdaptLanguage.text(snapshot.body().displayName())))));
      }
    } catch (RuntimeException failure) {
      AdaptLog.warn("Could not confirm packed golem delivery " + packed.golemId() + "; the same packed identity remains recoverable.", failure);
    } finally {
      deliveringPacked.remove(packed.golemId());
    }
  }

  private void recoverPacked(UUID owner, GolemRoster roster) {
    PlayerRef player = platform.player(owner);
    if (player == null) {
      return;
    }
    boolean pending = false;
    for (GolemRoster.GolemEntry entry : roster.golems()) {
      pending |= entry.pendingPackedState().isPresent();
      if (packingBodies.containsKey(entry.id())) {
        continue;
      }
      UUID entity = entityByGolem.get(entry.id());
      GolemHandle loaded = entity == null ? null : handles.get(entity);
      if (entry.status() == GolemRoster.Status.PACKED && loaded != null) {
        runOwned(loaded, () -> evaluate(loaded, true));
        continue;
      }
      entry.pendingPackedState().ifPresent(state -> GolemSnapshot.fromJson(state).ifPresent(snapshot -> {
        PackedGolem packed = new PackedGolem(entry.id(), owner, state);
        ItemRef item = GolemItems.dormantGolem(new DormantGolem(packed, GolemItems.dormantBlock(snapshot.body(), snapshot.copperStage(), snapshot.waxed()),
            snapshot.cores(), snapshot.runes(), snapshot.health(), snapshot.maxHealthMark()));
        deliverPacked(player, packed, item);
      }));
    }
    if (pending) {
      pendingPackOwners.add(owner);
    } else {
      pendingPackOwners.remove(owner);
    }
  }

  public GolemSnapshot snapshot(GolemHandle handle) {
    MobRef golem = handle.entity();
    GolemSnapshot.Traits traits = new GolemSnapshot.Traits(handle.state().customName(), golem.health(),
        platform.patinaStage(golem), platform.waxed(golem), !GolemBody.IRON.entityType().equals(golem.type()) || golem.isPlayerCreated());
    return GolemSnapshot.capture(handle.state(), traits, platform.nowMillis());
  }

  public void rename(GolemHandle handle, String name) {
    requireOwned(handle.entity(), "rename");
    if (handle.isRetired()) {
      return;
    }
    handle.setName(name);
    display(handle.entity(), name);
  }

  public void showNames() {
    for (GolemHandle handle : List.copyOf(handles.values())) {
      if (!handle.isRetired()) {
        runOwned(handle, () -> display(handle.entity(), handle.state().name().orElse(null)));
      }
    }
  }

  public boolean release(GolemHandle handle) {
    if (handle.isRetired()) {
      return false;
    }
    MobRef golem = handle.entity();
    requireOwned(golem, "release");
    Optional<GolemRoster> roster = platform.roster(handle.owner());
    GolemRoster.Status status = roster.flatMap(known -> known.golem(handle.id())).map(GolemRoster.GolemEntry::status).orElse(null);
    if (!handle.isValidated() || status != GolemRoster.Status.ACTIVE
        || roster.get().golem(handle.id()).map(GolemRoster.GolemEntry::revoked).orElse(true)) {
      return false;
    }
    releaseContents(handle, false);
    return true;
  }

  public void revokeBones(UUID owner) {
    Optional<GolemRoster> known = platform.roster(owner);
    if (known.isEmpty()) {
      return;
    }
    GolemRoster roster = known.get();
    if (roster.revokeBones()) {
      platform.rosterChanged(owner);
    }
    platform.upkeep(owner, upkept(roster));
    for (GolemHandle handle : loadedOf(owner)) {
      if (handle.body() == GolemBody.BONE) {
        runOwned(handle, () -> evaluate(handle, true));
      }
    }
    PlayerRef player = platform.player(owner);
    if (player != null) {
      platform.runEntity(player, () -> dissolveCarried(player), () -> {
      });
    }
  }

  public boolean dissolveRevoked(PackedGolem packed, Loc at) {
    if (!platform.owns(at)) {
      throw new IllegalStateException("Dissolving a packed golem must run on the thread that owns its drop location.");
    }
    GolemSnapshot snapshot = GolemSnapshot.fromJson(packed.state()).orElse(null);
    Optional<GolemRoster> roster = platform.roster(packed.owner());
    if (snapshot == null || snapshot.body() != GolemBody.BONE || !snapshot.id().equals(packed.golemId())
        || !snapshot.owner().equals(packed.owner()) || roster.isEmpty()
        || !roster.get().claimRevokedItem(packed.golemId(), packed.state())) {
      return false;
    }
    platform.rosterChanged(packed.owner());
    List<ItemRef> drops = new ArrayList<>();
    for (GolemCoreType core : snapshot.cores()) {
      drops.add(GolemItems.core(core));
    }
    for (GolemRune rune : snapshot.runes()) {
      drops.add(GolemItems.rune(rune));
    }
    if (snapshot.masterwork()) {
      drops.add(GolemItems.masterworkHeart());
    }
    for (String encoded : snapshot.carried()) {
      decodeDrop(snapshot.id(), encoded).ifPresent(drops::add);
    }
    for (String encoded : snapshot.equipment().values()) {
      decodeDrop(snapshot.id(), encoded).ifPresent(drops::add);
    }
    decodeDrop(snapshot.id(), snapshot.held()).ifPresent(drops::add);
    WorldRef world = Objects.requireNonNull(at.world());
    for (ItemRef drop : drops) {
      world.dropItemNaturally(at.position(), drop);
    }
    int essence = (int) snapshot.essence();
    if (essence > 0) {
      world.spawnExperience(at.position(), essence);
    }
    return true;
  }

  public boolean releaseEntry(UUID owner, UUID golem) {
    Optional<GolemRoster> roster = platform.roster(owner);
    if (roster.isEmpty() || !(roster.get().remove(golem, GolemRoster.Status.MISSING) || roster.get().remove(golem, GolemRoster.Status.SHATTERED)
        || roster.get().remove(golem, GolemRoster.Status.PACKED))) {
      return false;
    }
    missingWatch.forget(golem);
    platform.rosterChanged(owner);
    platform.upkeep(owner, upkept(roster.get()));
    return true;
  }

  public CompletableFuture<AdminRelease> releaseFor(UUID owner, UUID golem) {
    GolemHandle loaded = loadedHandle(golem);
    if (loaded != null && loaded.owner().equals(owner)) {
      CompletableFuture<AdminRelease> released = new CompletableFuture<>();
      platform.runEntity(loaded.entity(), () -> released.complete(release(loaded) ? AdminRelease.RELEASED : AdminRelease.REFUSED),
          () -> released.complete(AdminRelease.REFUSED));
      return released;
    }
    GolemRoster.Status status = platform.roster(owner).flatMap(roster -> roster.golem(golem)).map(GolemRoster.GolemEntry::status).orElse(null);
    if (status == GolemRoster.Status.ACTIVE) {
      return CompletableFuture.completedFuture(AdminRelease.ACTIVE_UNLOADED);
    }
    return CompletableFuture.completedFuture(releaseEntry(owner, golem) ? AdminRelease.RELEASED : AdminRelease.REFUSED);
  }

  public int wake(UUID owner) {
    if (owner == null || !platform.isOnline(owner)) {
      return 0;
    }
    int loaded = loadedOf(owner).size();
    joinOwner(owner);
    return loaded;
  }

  public int masterworks(UUID owner) {
    return platform.roster(owner).map(GolemRoster::masterworkCount).orElse(0);
  }

  /** Twin-cored golems of {@code owner}, loaded or not. */
  public int twins(UUID owner) {
    return platform.roster(owner).map(GolemRoster::twinCount).orElse(0);
  }

  /** Whether the golem holds its second core as a twin core. */
  public boolean twin(GolemHandle handle) {
    return platform.roster(handle.owner()).flatMap((GolemRoster roster) -> roster.golem(handle.id()))
        .map(GolemRoster.GolemEntry::twin).orElse(false);
  }

  /** The golem's place among its owner's twins, or -1 when it holds no twin core. */
  public int twinRank(GolemHandle handle) {
    return platform.roster(handle.owner()).map((GolemRoster roster) -> roster.twinRank(handle.id())).orElse(-1);
  }

  /** Whether a twin core can go into the golem: its owner has a twin left, or it holds one already. */
  public boolean twinOpen(GolemHandle handle) {
    int max = GolemMastery.maxTwins(handle.owner(), handle);
    return max > 0 && (twin(handle) || twins(handle.owner()) < max);
  }

  private void clearTwin(GolemHandle handle) {
    if (platform.roster(handle.owner()).map((GolemRoster roster) -> roster.markTwin(handle.id(), false)).orElse(false)) {
      platform.rosterChanged(handle.owner());
    }
  }

  public Designation designate(GolemHandle handle, int maxMasterworks) {
    requireOwned(handle.entity(), "designate");
    GolemState state = handle.state();
    if (handle.isRetired()) {
      return Designation.UNAVAILABLE;
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
    switch (roster.get().markMasterworkWithin(handle.id(), maxMasterworks)) {
      case FULL -> {
        return Designation.CAP;
      }
      case UNAVAILABLE -> {
        return Designation.UNAVAILABLE;
      }
      case MARKED -> platform.rosterChanged(handle.owner());
    }
    handle.setMasterwork(true);
    GolemStats.recompute(handle);
    return Designation.DESIGNATED;
  }

  public Optional<List<ItemRef>> undesignate(GolemHandle handle, int keptRunes) {
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
    List<ItemRef> returned = new ArrayList<>(cores.size() + runes.size() - kept);
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
    clearTwin(handle);
    GolemStats.recompute(handle);
    return Optional.of(List.copyOf(returned));
  }

  /** What slotting a core did: its refusal, if any, and whether it spent the Masterwork Heart offered for it. */
  public record CoreSlotting(Optional<GolemMastery.CoreRefusal> refusal, boolean heartSpent) {
    private static CoreSlotting refused(GolemMastery.CoreRefusal refusal) {
      return new CoreSlotting(Optional.of(refusal), false);
    }
  }

  /** Slots {@code core} as the golem's second core; {@code heart} offers a Masterwork Heart for a twin past the free ones. */
  public CoreSlotting addCore(GolemHandle handle, GolemCoreType core, boolean heart) {
    requireOwned(handle.entity(), "addCore");
    if (handle.isRetired() || activeRoster(handle).isEmpty()) {
      return CoreSlotting.refused(GolemMastery.CoreRefusal.UNAVAILABLE);
    }
    GolemState state = handle.state();
    List<GolemCoreType> cores = state.cores();
    boolean dualCore = GolemMastery.perks(handle).dualCore();
    boolean twin = !dualCore && twinOpen(handle);
    Optional<GolemMastery.CoreRefusal> refusal = GolemMastery.coreRefusal(cores, core, dualCore || twin,
        settings.get().allows(state.body(), core), GolemMastery.coreUnlocked(handle.owner(), core));
    if (refusal.isPresent()) {
      return new CoreSlotting(refusal, false);
    }
    boolean heartSpent = false;
    if (twin) {
      Optional<GolemRoster> roster = activeRoster(handle);
      GolemRoster.TwinMark mark = roster.isEmpty() ? GolemRoster.TwinMark.UNAVAILABLE : roster.get().markTwinWithin(handle.id(),
          GolemMastery.maxTwins(handle.owner(), handle), GolemMastery.freeTwins(), heart);
      switch (mark) {
        case FULL, UNAVAILABLE -> {
          return CoreSlotting.refused(GolemMastery.CoreRefusal.NO_SLOT);
        }
        case HEART_NEEDED -> {
          return CoreSlotting.refused(GolemMastery.CoreRefusal.HEART);
        }
        case MARKED, PAID -> heartSpent = mark == GolemRoster.TwinMark.PAID;
      }
      platform.rosterChanged(handle.owner());
    }
    handle.setCores(List.of(state.primaryCore(), core));
    return new CoreSlotting(Optional.empty(), heartSpent);
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
    clearTwin(handle);
    return true;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEntitiesEvent e) {
    if (e.phase() == ChunkEntitiesEvent.Phase.LOAD) {
      for (EntityRef entity : e.entities()) {
        if (entity instanceof MobRef mob && adoptable(mob)) {
          adopt(mob);
        }
      }
      return;
    }
    for (EntityRef entity : e.entities()) {
      handle(entity).ifPresent(this::unload);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEvent e) {
    if (e.phase() != ChunkEvent.Phase.LOAD || !missingWatch.hasExpectations()) {
      return;
    }
    missingWatch.chunkLoaded(e.world().uid(), GolemRoster.chunkKey(e.chunk().x(), e.chunk().z()), platform.nowMillis());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDeathEvent e) {
    handle(e.entity()).ifPresent(handle -> die(handle, e));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    joinOwner(e.player().uuid());
  }

  @Handle(priority = EventPriority.NORMAL)
  public void on(PlayerQuitEvent e) {
    UUID owner = e.player().uuid();
    pendingOwners.remove(owner);
    missingWatch.forgetOwner(owner);
    platform.upkeep(owner, Set.of());
    Optional<GolemRoster> roster = platform.roster(owner);
    long now = platform.nowMillis();
    for (GolemHandle handle : loadedOf(owner)) {
      handle.setValidated(false);
      GolemHandle.ChunkPosition position = platform.owns(handle.entity())
          ? handle.trackPosition(handle.entity().location())
          : handle.position();
      if (roster.isPresent() && position != null) {
        roster.get().seen(handle.id(), position.world(), position.chunkKey(), now);
      }
      runOwned(handle, () -> evaluate(handle, false));
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(WorldLifecycleEvent e) {
    if (e.phase() != WorldLifecycleEvent.Phase.SAVE) {
      return;
    }
    UUID world = e.world().uid();
    for (GolemHandle handle : handles.values()) {
      GolemHandle.ChunkPosition position = handle.position();
      if (handle.isDirty() && position != null && world.equals(position.world())) {
        runOwned(handle, handle::flushIfDirty);
      }
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !handles.isEmpty() || !pendingOwners.isEmpty() || !pendingPackOwners.isEmpty() || !packingBodies.isEmpty() || missingWatch.hasDeadlines();
  }

  @Override
  public void onTick() {
    long now = platform.nowMillis();
    retryPendingOwners();
    retryPackingBodies();
    for (UUID owner : List.copyOf(pendingPackOwners)) {
      if (platform.isOnline(owner)) {
        platform.roster(owner).ifPresent(roster -> recoverPacked(owner, roster));
      }
    }
    wakeQueued();
    markMissing(missingWatch.expired(now));
    if (now >= nextPruneAt) {
      nextPruneAt = now + PRUNE_INTERVAL_MILLIS;
      pruneInvalid();
    }
  }

  @Override
  protected List<?> createCompanionListeners() {
    return List.of(new GolemGuardListener(platform::player, this::handle), new GolemEquipmentListener(this::handle),
        new GolemEquipmentListener.PaperChanges(this::handle), new PaperEntityListener(this), new CopperBrainGuard());
  }

  static boolean adoptable(MobRef mob) {
    return GolemState.has(mob) || GolemRitualMarker.has(mob);
  }

  static Set<UUID> upkept(GolemRoster roster) {
    Set<UUID> bones = new HashSet<>();
    for (GolemRoster.GolemEntry entry : roster.golems()) {
      if (entry.body() == GolemBody.BONE && (entry.status() == GolemRoster.Status.ACTIVE || entry.status() == GolemRoster.Status.MISSING
          || entry.status() == GolemRoster.Status.PACKED)) {
        bones.add(entry.id());
      }
    }
    return bones;
  }

  void adopt(MobRef mob) {
    Optional<GolemState> packedState = GolemState.read(mob);
    if (packedState.isPresent() && mob.data().has(PACKING_KEY)) {
      MobRef previous = packingBodies.putIfAbsent(packedState.get().id(), mob);
      if (previous != null && !previous.uuid().equals(mob.uuid())) {
        mob.remove();
        return;
      }
      packingBodies.put(packedState.get().id(), mob);
      if (!recoverPackingBody(mob, packedState.get())) {
        return;
      }
    }
    GolemAttribute scale = platform.scaleOf(mob);
    GolemRitualMarker.recover(mob, scale);
    if (handle(mob).isPresent()) {
      return;
    }
    Optional<GolemState> state = packedState;
    if (state.isEmpty()) {
      return;
    }
    if (GolemBody.of(mob).orElse(null) != state.get().body()) {
      if (mismatchWarned.add(mob.uuid())) {
        AdaptLog.warn("Ignoring bound golem " + mob.uuid() + " because its adapt:golem body " + state.get().body().id() + " does not match the entity.");
      }
      return;
    }
    if (scale != null) {
      GolemScale.clearStepModifier(scale);
    }
    GolemHandle handle = new GolemHandle(mob, state.get(), platform);
    if (index(handle)) {
      display(mob, handle.state().name().orElse(null));
      evaluate(handle, platform.isOnline(handle.owner()));
    }
  }

  private boolean recoverPackingBody(MobRef mob, GolemState state) {
    if (packingInFlight.contains(state.id())) {
      PackCompletion pending = packingCompletions.get(state.id());
      if (pending != null) {
        schedulePackCompletion(state.id(), pending);
      }
      return false;
    }
    Optional<GolemRoster> roster = platform.roster(state.owner());
    if (roster.isEmpty() || !platform.ownerReady(state.owner())) {
      return false;
    }
    GolemRoster.GolemEntry entry = roster.get().golem(state.id()).orElse(null);
    if (entry == null || entry.status() != GolemRoster.Status.ACTIVE && entry.status() != GolemRoster.Status.MISSING || entry.sighting() > state.sighting()) {
      mob.remove();
      forgetPacking(state.id());
      return false;
    }
    Byte flags = mob.data().get(PACKING_KEY);
    restorePackingBody(mob, PackingBody.fromFlags(flags == null ? (byte) 0 : flags));
    forgetPacking(state.id());
    return true;
  }

  private void retryPackingBodies() {
    for (Map.Entry<UUID, MobRef> entry : packingBodies.entrySet()) {
      PackCompletion pending = packingCompletions.get(entry.getKey());
      if (pending != null) {
        schedulePackCompletion(entry.getKey(), pending);
      } else if (!packingInFlight.contains(entry.getKey()) && packingRecoveryScheduled.add(entry.getKey())) {
        Runnable retired = () -> packingRecoveryScheduled.remove(entry.getKey());
        if (!platform.runEntity(entry.getValue(), () -> {
          try {
            adopt(entry.getValue());
          } finally {
            retired.run();
          }
        }, retired)) {
          retired.run();
        }
      }
    }
  }

  void adoptLater(EntityRef entity) {
    if (entity instanceof MobRef mob && GolemState.has(mob) && handle(mob).isEmpty()) {
      platform.runEntity(mob, () -> adopt(mob), 1);
    }
  }

  void unload(GolemHandle handle) {
    if (handle.isRetired()) {
      return;
    }
    OptionalLong next = nextSighting(handle);
    next.ifPresent(handle.state()::setSighting);
    if (detach(handle)) {
      commitSighting(handle, next);
    }
  }

  boolean detach(GolemHandle handle) {
    if (handle.isRetired()) {
      return false;
    }
    boolean written = handle.flush();
    rememberPosition(handle, true);
    unindex(handle);
    return written;
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
      } else if (!platform.runEntity(handle.entity(), flush, () -> completion.complete(true))) {
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
      OptionalLong next = nextSighting(handle);
      rememberPosition(handle, false);
      next.ifPresent(handle.state()::setSighting);
      if (putToSleep(handle)) {
        commitSighting(handle, next);
      }
      lifecycle.asleep(handle);
      lifecycle.detached(handle);
      return true;
    } catch (RuntimeException failure) {
      AdaptLog.warn("Failed to save bound golem " + handle.id() + " at shutdown.", failure);
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
      AdaptLog.warn("Interrupted while saving bound golems at shutdown.", interrupted);
      return false;
    } catch (ExecutionException | TimeoutException incomplete) {
      AdaptLog.warn("Bound golem saving did not complete before shutdown.", incomplete);
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
    Optional<GolemRoster.GolemEntry> entry = roster.flatMap(known -> known.golem(handle.id()));
    if (handle.body() == GolemBody.BONE && ownerOnline && !platform.ownerReady(owner)) {
      pendingOwners.add(owner);
      handle.setValidated(false);
      applyDormancy(handle, DormancyReason.OWNER_UNKNOWN);
      return;
    }
    if (handle.body() == GolemBody.BONE && entry.isPresent() && !entry.get().revoked()
        && platform.ownerReady(owner) && !platform.keepsBoneGolems(owner)) {
      revokeBones(owner);
      return;
    }
    if (entry.isPresent() && entry.get().revoked()) {
      boolean claimed = !isDuplicate(handle) && roster.get().claimRevokedBody(handle.id(), handle.state().sighting());
      if (claimed) {
        platform.rosterChanged(owner);
        releaseContents(handle, true);
      } else {
        invalidate(handle);
      }
      return;
    }
    GolemRoster.Status status = entry.map(GolemRoster.GolemEntry::status).orElse(null);
    boolean stale = entry.isPresent() && entry.get().outdates(handle.state().sighting());
    if (status == GolemRoster.Status.PACKED) {
      handle.entity().remove();
      GolemState.clear(handle.entity());
      unindex(handle);
      return;
    }
    GolemValidation.Verdict verdict = GolemValidation.verdict(isDuplicate(handle), roster.isPresent(), status, stale);
    switch (verdict) {
      case REVERT -> {
        invalidate(handle);
        if (stale) {
          expectMissing(owner, entry.get(), platform.nowMillis());
        }
        return;
      }
      case KEEP, RESTORE -> {
        handle.setValidated(true);
        recordSighting(handle, roster.get(), verdict == GolemValidation.Verdict.RESTORE);
      }
      case WAIT -> {
        handle.setValidated(false);
        if (ownerOnline) {
          pendingOwners.add(owner);
        }
      }
    }
    boolean offlineGuard = GolemDormancy.offlineGuard(settings.get().guardWhileOwnerOffline(), handle.core())
        && (handle.body() != GolemBody.BONE || handle.isValidated());
    applyDormancy(handle, GolemDormancy.reason(ownerOnline, roster.isPresent(), offlineGuard, () -> claimAwake(handle)));
  }

  private void applyDormancy(GolemHandle handle, DormancyReason reason) {
    if (handle.isRetired()) {
      return;
    }
    handle.setDormant(reason != DormancyReason.NONE);
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
    MobRef golem = handle.entity();
    golem.setAware(true);
    golem.setInvulnerable(false);
    handle.state().setDormant(false);
    handle.flush();
  }

  private boolean putToSleep(GolemHandle handle) {
    MobRef golem = handle.entity();
    golem.setAware(false);
    golem.setTarget(null);
    golem.stopPathing();
    golem.setInvulnerable(settings.get().dormantInvulnerable());
    handle.state().setDormant(true);
    return handle.flush();
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
    GolemHandle outdated;
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
      if (handle.state().sighting() <= other.state().sighting()) {
        return true;
      }
      entityByGolem.put(handle.id(), handle.entityId());
      other.setValidated(false);
      outdated = other;
    }
    runOwned(outdated, () -> invalidate(outdated));
    return false;
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
      invalidOnce.remove(handle.entityId());
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

  private void dissolveCarried(PlayerRef player) {
    InventoryRef inventory = player.inventory();
    if (inventory == null) {
      return;
    }
    boolean changed = false;
    for (int slot = 0; slot < inventory.size(); slot++) {
      ItemRef item = inventory.item(slot);
      Optional<PackedGolem> packed = GolemItems.packedGolemOf(item);
      if (packed.isPresent() && packed.get().owner().equals(player.uuid())
          && dissolveRevoked(packed.get(), player.location())) {
        inventory.clear(slot);
        changed = true;
      }
    }
    if (changed) {
      player.saveData();
    }
  }

  private Optional<ItemRef> decodeDrop(UUID id, String encoded) {
    if (encoded == null) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(platform.decodeItem(encoded));
    } catch (RuntimeException corrupt) {
      AdaptLog.warn("Golem " + id + " contains an unreadable item; it could not be returned.", corrupt);
      return Optional.empty();
    }
  }

  private void releaseContents(GolemHandle handle, boolean dropEquipment) {
    MobRef golem = handle.entity();
    GolemState state = handle.state();
    List<ItemRef> drops = new ArrayList<>(state.carriedItems());
    for (GolemCoreType core : state.cores()) {
      drops.add(GolemItems.core(core));
    }
    for (GolemRune rune : state.runes()) {
      drops.add(GolemItems.rune(rune));
    }
    if (state.masterwork()) {
      drops.add(GolemItems.masterworkHeart());
    }
    Optional<ItemRef> tool = heldTool(handle);
    List<ItemRef> worn = handle.equipmentItems();
    int essence = state.essencePoints();
    Loc at = golem.location();
    revert(handle);
    forgetRosterEntry(handle);
    if (handle.body() == GolemBody.BONE) {
      platform.crumble(golem);
    }
    WorldRef world = golem.world();
    for (ItemRef drop : drops) {
      world.dropItemNaturally(at.position(), drop);
    }
    for (ItemRef item : worn) {
      if (dropEquipment) {
        world.dropItemNaturally(at.position(), item);
      } else {
        platform.returnToOwner(handle.owner(), at, item);
      }
    }
    tool.ifPresent(item -> {
      if (dropEquipment) {
        world.dropItemNaturally(at.position(), item);
      } else {
        platform.returnToOwner(handle.owner(), at, item);
      }
    });
    if (essence > 0) {
      returnEssence(golem, world, at, essence, platform.player(handle.owner()));
    }
  }

  private Optional<ItemRef> heldTool(GolemHandle handle) {
    String held = handle.state().held();
    if (held == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(platform.decodeItem(held));
    } catch (RuntimeException corrupt) {
      AdaptLog.warn("Golem " + handle.id() + " holds an unreadable tool; it is not dropped.", corrupt);
      return Optional.empty();
    }
  }

  private void revert(GolemHandle handle) {
    MobRef golem = handle.entity();
    double pool = handle.hostileDamagePool();
    if (handle.state().held() != null) {
      GolemHeldTool.hide(golem);
    }
    unindex(handle);
    GolemState.clear(golem);
    HostileDamagePool.putUnbound(golem, pool);
    GolemAttribute scale = platform.scaleOf(golem);
    if (scale != null) {
      GolemScale.clearModifier(scale);
      GolemScale.clearStepModifier(scale);
    }
    GolemStats.clear(handle);
    golem.setCustomNameJson(null);
    StackExclusion.include(golem);
    golem.setAware(true);
    golem.setInvulnerable(false);
  }

  private void invalidate(GolemHandle handle) {
    revert(handle);
    if (handle.body() == GolemBody.BONE) {
      GolemEquipmentSlot.strip(handle.entity());
      platform.crumble(handle.entity());
    }
  }

  private void die(GolemHandle handle, EntityDeathEvent e) {
    boolean validated = handle.isValidated();
    unindex(handle);
    if (handle.body() == GolemBody.BONE) {
      e.drops().clear();
      e.setDroppedExp(0);
    }
    if (!validated) {
      return;
    }
    GolemSettings current = settings.get();
    GolemState state = handle.state();
    List<ItemRef> drops = e.drops();
    drops.addAll(state.carriedItems());
    heldTool(handle).ifPresent(drops::add);
    drops.addAll(handle.equipmentItems());
    if (state.essencePoints() > 0) {
      e.setDroppedExp((int) Math.min(Integer.MAX_VALUE, (long) e.droppedExp() + state.essencePoints()));
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
    Loc at = handle.entity().location();
    platform.dropOwnerBound(at, GolemItems.shatteredCore(new PackedGolem(handle.id(), owner, itemState)), owner);
    GolemMend.shattered(handle, at);
    return true;
  }

  private GolemSnapshot restorable(UUID owner, Restore request) {
    PackedGolem item = request.item();
    GolemSnapshot snapshot = GolemSnapshot.fromJson(item.state())
        .orElseThrow(() -> new IllegalStateException("Golem item " + item.golemId() + " of " + item.owner() + " holds no readable golem"));
    if (!owner.equals(snapshot.owner()) || !owner.equals(item.owner()) || !snapshot.id().equals(item.golemId())) {
      throw new IllegalStateException("Golem " + snapshot.id() + " in item " + item.golemId() + " of " + item.owner() + " cannot be restored for " + owner);
    }
    return request.from() == GolemRoster.Status.SHATTERED ? snapshot.withoutCargo() : snapshot;
  }

  private boolean claim(UUID owner, GolemSnapshot snapshot, Restore request) {
    GolemRoster roster = platform.roster(owner).orElseThrow();
    Optional<GolemRoster.GolemEntry> entry = roster.golem(snapshot.id());
    if (entry.isPresent() && (entry.get().body() != snapshot.body() || entry.get().core() != snapshot.primaryCore())) {
      throw new IllegalStateException("Golem " + snapshot.id() + " is a " + entry.get().body() + " " + entry.get().core() + " golem in the roster of "
          + owner + ", not the " + snapshot.body() + " " + snapshot.primaryCore() + " golem its item describes");
    }
    return roster.unstow(snapshot.id(), request.from(), request.item().state());
  }

  private String restoredName(PlayerRef owner, GolemSnapshot snapshot) {
    return snapshot.name().orElseGet(() -> GolemSnapshot.textName(boundName(owner, snapshot.body())));
  }

  private void undoRestore(MobRef golem, UUID owner, Restore request) {
    undoSteps(() -> {
      Optional<GolemHandle> adopted = handle(golem);
      if (adopted.isPresent()) {
        revert(adopted.get());
      } else {
        GolemState.clear(golem);
      }
    }, () -> restow(owner, request));
  }

  private void undoAwaken(MobRef golem, UUID owner, Restore request) {
    undoSteps(() -> {
      if (golem != null) {
        undoSteps(() -> handle(golem).ifPresent(this::unindex), golem::remove);
      }
    }, () -> restow(owner, request));
  }

  private void undoPack(MobRef golem, UUID owner, GolemRoster roster, UUID id, boolean stowed) {
    undoSteps(() -> {
      if (stowed && roster.transition(id, GolemRoster.Status.PACKED, GolemRoster.Status.ACTIVE)) {
        platform.rosterChanged(owner);
      }
    }, () -> adopt(golem));
  }

  private static void undoSteps(Runnable first, Runnable then) {
    RuntimeException failure = null;
    Error fatal = null;
    try {
      first.run();
    } catch (RuntimeException thrown) {
      failure = thrown;
    } catch (Error thrown) {
      fatal = thrown;
    }
    try {
      then.run();
    } catch (RuntimeException | Error second) {
      if (failure != null) {
        second.addSuppressed(failure);
      }
      if (fatal != null) {
        second.addSuppressed(fatal);
      }
      throw second;
    }
    if (fatal != null) {
      throw fatal;
    }
    if (failure != null) {
      throw failure;
    }
  }

  private void restow(UUID owner, Restore request) {
    PackedGolem item = request.item();
    Optional<GolemRoster> roster = platform.roster(owner);
    if (roster.isPresent() && roster.get().stow(item.golemId(), request.from(), item.state())) {
      platform.rosterChanged(owner);
    }
  }

  private void writeBound(MobRef golem, GolemState state, String name) {
    state.setHostileDamagePool(HostileDamagePool.takeUnbound(golem));
    state.setName(name);
    state.write(golem);
    golem.data().set(BOUND_ONCE_KEY, (byte) 1);
    StackExclusion.exclude(golem);
    golem.setPersistent(true);
    golem.setRemoveWhenFarAway(false);
    if (state.body() == GolemBody.BONE) {
      GolemEquipmentSlot.strip(golem);
      golem.setCanPickupItems(false);
      golem.setBurnsInDaylight(false);
      for (Map.Entry<GolemEquipmentSlot, String> entry : state.equipment().entrySet()) {
        golem.setEquipment(entry.getKey().slot(), platform.decodeItem(entry.getValue()));
        golem.setEquipmentDropChance(entry.getKey().slot(), 0F);
      }
    }
    display(golem, name);
    GolemAttribute scale = platform.scaleOf(golem);
    if (scale != null) {
      GolemScale.setModifier(scale, GolemScale.modifierAmount(settings.get().bodyScale(state.body())));
    }
  }

  private void display(MobRef golem, String name) {
    String shown = platform.nameMode().showsCustomName() ? name : null;
    if (!Objects.equals(golem.customNameJson(), shown)) {
      golem.setCustomNameJson(shown);
    }
  }

  private GolemHandle adoptBound(MobRef golem, GolemState state) {
    GolemHandle handle = new GolemHandle(golem, state, platform);
    index(handle);
    evaluate(handle, true);
    return handle;
  }

  private void returnEssence(MobRef golem, WorldRef world, Loc at, int points, PlayerRef owner) {
    AtomicBoolean settled = new AtomicBoolean();
    Runnable drop = () -> {
      if (settled.compareAndSet(false, true)) {
        world.spawnExperience(at.position(), points);
      }
    };
    Runnable give = () -> {
      if (settled.compareAndSet(false, true)) {
        AdaptPorts.get(ComponentPort.class, ComponentPort.NONE).awardExperience(owner, points);
      }
    };
    AtomicBoolean fellBack = new AtomicBoolean();
    Runnable dropAtLastLocation = () -> {
      if (fellBack.compareAndSet(false, true) && !platform.runAt(at, drop)) {
        AdaptLog.warn("Released golem " + golem.uuid() + " could not drop its " + points + " experience points of essence at " + at + "; the essence was lost.");
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
      if (handle.body() == GolemBody.BONE) {
        platform.upkeep(owner, upkept(roster.get()));
      }
    }
  }

  private void joinOwner(UUID owner) {
    Optional<GolemRoster> roster = platform.roster(owner);
    if (roster.isEmpty() || !platform.ownerReady(owner)) {
      pendingOwners.add(owner);
      return;
    }
    pendingOwners.remove(owner);
    recoverPacked(owner, roster.get());
    if (!platform.keepsBoneGolems(owner)) {
      revokeBones(owner);
    }
    platform.upkeep(owner, upkept(roster.get()));
    for (GolemHandle handle : loadedOf(owner)) {
      runOwned(handle, () -> evaluate(handle, true));
    }
    expectUnloaded(owner, roster.get());
  }

  private void expectUnloaded(UUID owner, GolemRoster roster) {
    long now = platform.nowMillis();
    Set<GolemHandle.ChunkPosition> swept = new HashSet<>();
    for (GolemRoster.GolemEntry entry : roster.golems()) {
      if (entityByGolem.containsKey(entry.id()) || !expectMissing(owner, entry, now)) {
        continue;
      }
      if (swept.add(new GolemHandle.ChunkPosition(entry.world(), entry.chunkKey()))) {
        platform.sweepChunk(entry.world(), entry.chunkKey(), this::adopt);
      }
    }
  }

  private boolean expectMissing(UUID owner, GolemRoster.GolemEntry entry, long now) {
    if (entry.status() != GolemRoster.Status.ACTIVE || entry.world() == null) {
      return false;
    }
    missingWatch.expect(new GolemMissingWatch.Expected(owner, entry.id(), entry.world(), entry.chunkKey()));
    if (!platform.isChunkLoaded(entry.world(), entry.chunkKey())) {
      return false;
    }
    missingWatch.chunkLoaded(entry.world(), entry.chunkKey(), now);
    return true;
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
      if (handle.entity().isValid()) {
        invalidOnce.remove(handle.entityId());
      } else if (!invalidOnce.add(handle.entityId())) {
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

  private void recordSighting(GolemHandle handle, GolemRoster roster, boolean restored) {
    GolemHandle.ChunkPosition position = handle.trackPosition(handle.entity().location());
    if (position != null) {
      roster.seen(handle.id(), position.world(), position.chunkKey(), platform.nowMillis());
    }
    boolean masterworkChanged = roster.markMasterwork(handle.id(), handle.state().masterwork());
    boolean sightingRaised = roster.raiseSighting(handle.id(), handle.state().sighting());
    if (restored || masterworkChanged || sightingRaised) {
      platform.rosterChanged(handle.owner());
    }
  }

  private OptionalLong nextSighting(GolemHandle handle) {
    if (handle.isRetired() || !handle.isValidated()) {
      return OptionalLong.empty();
    }
    Optional<GolemRoster.GolemEntry> entry = platform.roster(handle.owner()).flatMap(roster -> roster.golem(handle.id()));
    if (entry.isEmpty() || entry.get().status() != GolemRoster.Status.ACTIVE) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(Math.max(entry.get().sighting(), handle.state().sighting()) + 1L);
  }

  private void commitSighting(GolemHandle handle, OptionalLong next) {
    if (next.isEmpty()) {
      return;
    }
    Optional<GolemRoster> roster = platform.roster(handle.owner());
    if (roster.isPresent() && roster.get().raiseSighting(handle.id(), next.getAsLong())) {
      platform.rosterChanged(handle.owner());
    }
  }

  private long sightingOf(UUID owner, UUID golem) {
    return platform.roster(owner).flatMap(roster -> roster.golem(golem)).map(GolemRoster.GolemEntry::sighting)
        .orElseThrow(() -> new IllegalStateException("Golem " + golem + " has no roster entry for " + owner + " after its claim"));
  }

  private GolemHandle loadedHandle(UUID golem) {
    UUID entityId = golem == null ? null : entityByGolem.get(golem);
    GolemHandle handle = entityId == null ? null : handles.get(entityId);
    return handle == null || handle.isRetired() ? null : handle;
  }

  private Optional<GolemRoster> activeRoster(GolemHandle handle) {
    if (!handle.isValidated()) {
      return Optional.empty();
    }
    return platform.roster(handle.owner())
        .filter(roster -> roster.golem(handle.id()).filter(entry -> !entry.revoked() && entry.status() == GolemRoster.Status.ACTIVE).isPresent());
  }

  private void rememberPosition(GolemHandle handle, boolean expectReturn) {
    UUID owner = handle.owner();
    if (!platform.isOnline(owner)) {
      return;
    }
    Optional<GolemRoster> roster = platform.roster(owner);
    GolemHandle.ChunkPosition position = handle.trackPosition(handle.entity().location());
    if (roster.isEmpty() || position == null) {
      return;
    }
    if (roster.get().seen(handle.id(), position.world(), position.chunkKey(), platform.nowMillis()) && expectReturn) {
      missingWatch.expect(new GolemMissingWatch.Expected(owner, handle.id(), position.world(), position.chunkKey()));
    }
  }

  private void runOwned(GolemHandle handle, Runnable task) {
    MobRef golem = handle.entity();
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

  private void requireOwned(MobRef golem, String operation) {
    if (!platform.owns(golem)) {
      throw new IllegalStateException("Golem " + operation + " for " + golem.uuid() + " must run on the golem's owning thread.");
    }
  }

  private int boundInChunk(Loc at, EntityRef excluded) {
    WorldRef world = at.world();
    int chunkX = at.chunkX();
    int chunkZ = at.chunkZ();
    Box chunk = new Box(chunkX << 4, world.minHeight(), chunkZ << 4, (chunkX << 4) + 16D, world.maxHeight(), (chunkZ << 4) + 16D);
    UUID skipped = excluded == null ? null : excluded.uuid();
    int bound = 0;
    for (EntityRef entity : world.entitiesIn(chunk, null)) {
      if (!entity.uuid().equals(skipped) && GolemState.has(entity)) {
        bound++;
      }
    }
    return bound;
  }

  private static long chunkKey(Loc location) {
    return GolemRoster.chunkKey(location.blockX() >> 4, location.blockZ() >> 4);
  }

  private static String boundName(PlayerRef owner, GolemBody body) {
    return AdaptLanguage.text(GolemancyMessages.GOLEM_NAME,
        untrusted("owner", owner.name()),
        trusted("body", AdaptLanguage.text(body.displayName())));
  }

  public record Restore(PackedGolem item, GolemRoster.Status from) {
    public Restore {
      Objects.requireNonNull(item);
      Objects.requireNonNull(from);
    }
  }

  private record PackCompletion(GolemRoster roster, Packing packing, PackingBody body,
                                CompletableFuture<Optional<Packing>> completion, Throwable failure, AtomicBoolean scheduled) {
  }

  private record PackingBody(boolean invulnerable, boolean aware, boolean pickup) {
    private byte flags() {
      return (byte) ((invulnerable ? 1 : 0) | (aware ? 2 : 0) | (pickup ? 4 : 0));
    }

    private static PackingBody fromFlags(byte flags) {
      return new PackingBody((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
    }
  }

  public record Packing(PackedGolem packed, GolemSnapshot snapshot, ItemRef item, Loc at) {
    public Packing {
      Objects.requireNonNull(packed);
      Objects.requireNonNull(snapshot);
      Objects.requireNonNull(item);
      Objects.requireNonNull(at);
    }
  }

  public enum AdminRelease {
    RELEASED,
    ACTIVE_UNLOADED,
    REFUSED
  }

  public enum Designation {
    DESIGNATED,
    LOCKED,
    CAP,
    ALREADY,
    UNAVAILABLE
  }

  public enum BindBlock {
    ALREADY_BOUND,
    BODY_MISMATCH,
    CORE_NOT_ALLOWED,
    OWNER_DATA_UNAVAILABLE,
    PLAYER_CAP,
    CHUNK_CAP,
    SERVER_CAP
  }

  static final class PaperEntityListener {
    private final GolemRegistry registry;

    private PaperEntityListener(GolemRegistry registry) {
      this.registry = registry;
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(EntityWorldEvent e) {
      if (e.phase() == EntityWorldEvent.Phase.ADD) {
        registry.adoptLater(e.entity());
      } else {
        registry.handle(e.entity()).ifPresent(registry::detach);
      }
    }
  }
}
