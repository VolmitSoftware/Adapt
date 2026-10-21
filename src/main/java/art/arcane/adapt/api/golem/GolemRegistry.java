package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.item.DormantGolem;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.volmlib.util.bukkit.BukkitExperience;
import art.arcane.volmlib.util.entity.StackExclusion;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.IronGolem;
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
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

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
  private static final NamespacedKey PACKING_TOKEN_KEY = new NamespacedKey("adapt", "golem_packing_token");
  private static final NamespacedKey PACKING_KEY = new NamespacedKey("adapt", "golem_packing");
  private static final NamespacedKey BOUND_ONCE_KEY = new NamespacedKey("adapt", "golem_bound_once");

  private final GolemPlatform platform;
  private final Supplier<GolemSettings> settings;
  private final GolemLifecycle lifecycle;
  private final Object lock = new Object();
  private final Map<UUID, GolemHandle> handles = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> entityByGolem = new ConcurrentHashMap<>();
  private final Map<UUID, Set<UUID>> entitiesByOwner = new ConcurrentHashMap<>();
  private final Set<UUID> pendingPackOwners = ConcurrentHashMap.newKeySet();
  private final Set<UUID> deliveringPacked = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Mob> packingBodies = new ConcurrentHashMap<>();
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
    if (boundInChunk(golem.getLocation(), golem) >= current.maxBoundGolemsPerChunk()) {
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
    if (body == GolemBody.BONE) {
      platform.upkeep(ownerId, upkept(roster));
    }
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
    if (boundInChunk(golem.getLocation(), golem) >= current.maxBoundGolemsPerChunk()) {
      return Optional.of(BindBlock.CHUNK_CAP);
    }
    return Optional.empty();
  }

  public Optional<BindBlock> awakenBlock(Location at, GolemSnapshot snapshot) {
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

  public Optional<GolemHandle> restore(Player owner, Mob golem, Restore request) {
    requireOwned(golem, "restore");
    UUID ownerId = owner.getUniqueId();
    GolemSnapshot snapshot = restorable(ownerId, request);
    Optional<BindBlock> block = restoreBlock(golem, snapshot);
    if (block.isPresent()) {
      throw new IllegalStateException("Golem " + snapshot.id() + " cannot be restored into " + golem.getUniqueId() + ": " + block.get());
    }
    if (!claim(ownerId, snapshot, request)) {
      return Optional.empty();
    }
    try {
      platform.rosterChanged(ownerId);
      GolemState state = GolemState.restored(snapshot, GolemState.Anchor.at(golem.getLocation()), sightingOf(ownerId, snapshot.id()));
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

  public Optional<GolemHandle> awaken(Player owner, Location at, Restore request, Consumer<Mob> prepare) {
    if (!platform.owns(at)) {
      throw new IllegalStateException("Golem awaken at " + at + " must run on the thread that owns that spot.");
    }
    UUID ownerId = owner.getUniqueId();
    GolemSnapshot snapshot = restorable(ownerId, request);
    Optional<BindBlock> block = awakenBlock(at, snapshot);
    if (block.isPresent()) {
      throw new IllegalStateException("Golem " + snapshot.id() + " cannot awaken at " + at + ": " + block.get());
    }
    if (!claim(ownerId, snapshot, request)) {
      return Optional.empty();
    }
    AtomicReference<Mob> spawned = new AtomicReference<>();
    try {
      platform.rosterChanged(ownerId);
      GolemState state = GolemState.restored(snapshot, GolemState.Anchor.at(at), sightingOf(ownerId, snapshot.id()));
      Component name = restoredName(owner, snapshot);
      Mob golem = platform.spawn(at, snapshot.body().entityClass(), body -> {
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
    Mob golem = handle.entity();
    requireOwned(golem, "pack");
    UUID owner = handle.owner();
    Optional<GolemRoster> roster = platform.roster(owner);
    GolemRoster.Status status = roster.flatMap(known -> known.golem(handle.id())).map(GolemRoster.GolemEntry::status).orElse(null);
    if (!handle.isValidated() || status != GolemRoster.Status.ACTIVE
        || roster.get().golem(handle.id()).map(GolemRoster.GolemEntry::revoked).orElse(true)) {
      return CompletableFuture.completedFuture(Optional.empty());
    }
    Location at = golem.getLocation();
    AttributeInstance maximum = platform.attribute(golem, GolemStat.MAX_HEALTH);
    double maxHealth = maximum == null ? golem.getHealth() : maximum.getValue();
    GolemRoster known = roster.get();
    GolemSnapshot snapshot;
    PackedGolem packed;
    ItemStack item;
    boolean stowed = false;
    PackingBody body = new PackingBody(golem.isInvulnerable(), golem.isAware(), golem.getCanPickupItems());
    packingInFlight.add(handle.id());
    packingBodies.put(handle.id(), golem);
    try {
      if (!handle.flush()) {
        throw new IllegalStateException("Could not persist golem state before packing " + handle.id());
      }
      golem.getPersistentDataContainer().set(PACKING_KEY, PersistentDataType.BYTE, body.flags());
      golem.setInvulnerable(true);
      golem.setAware(false);
      golem.setCanPickupItems(false);
      golem.setTarget(null);
      unindex(handle);
      snapshot = snapshot(handle);
      packed = new PackedGolem(handle.id(), owner, snapshot.toPackedJson(UUID.randomUUID()));
      golem.getPersistentDataContainer().set(PACKING_TOKEN_KEY, PersistentDataType.STRING, GolemRoster.digest(packed.state()));
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
    Mob golem = packingBodies.get(id);
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

  private void finishPack(Mob golem, GolemRoster roster, Packing packing, PackingBody body,
                          CompletableFuture<Optional<Packing>> completion, Throwable failure) {
    String marker = golem.getPersistentDataContainer().get(PACKING_TOKEN_KEY, PersistentDataType.STRING);
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

  private static void restorePackingBody(Mob golem, PackingBody body) {
    golem.getPersistentDataContainer().remove(PACKING_KEY);
    golem.getPersistentDataContainer().remove(PACKING_TOKEN_KEY);
    golem.setInvulnerable(body.invulnerable());
    golem.setAware(body.aware());
    golem.setCanPickupItems(body.pickup());
  }

  public void deliverPacked(Player player, PackedGolem packed, ItemStack item) {
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

  private void deliverPackedOwned(Player player, PackedGolem packed, ItemStack item) {
    GolemRoster roster = platform.roster(packed.owner()).orElse(null);
    if (roster == null || roster.golem(packed.golemId()).flatMap(GolemRoster.GolemEntry::pendingPackedState).filter(packed.state()::equals).isEmpty()) {
      deliveringPacked.remove(packed.golemId());
      return;
    }
    try {
      Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
      for (ItemStack leftover : leftovers.values()) {
        platform.dropOwnerBound(player.getLocation(), leftover, packed.owner());
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
      Adapt.warn("Could not confirm packed golem delivery " + packed.golemId() + "; the same packed identity remains recoverable.", failure);
    } finally {
      deliveringPacked.remove(packed.golemId());
    }
  }

  private void recoverPacked(UUID owner, GolemRoster roster) {
    Player player = platform.player(owner);
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
        ItemStack item = GolemItems.dormantGolem(new DormantGolem(packed, GolemItems.dormantBlock(snapshot.body(), snapshot.copperStage(), snapshot.waxed()),
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
    Mob golem = handle.entity();
    GolemSnapshot.Traits traits = new GolemSnapshot.Traits(handle.state().customName(), golem.getHealth(),
        platform.patinaStage(golem), platform.waxed(golem), !(golem instanceof IronGolem iron) || iron.isPlayerCreated());
    return GolemSnapshot.capture(handle.state(), traits, platform.nowMillis());
  }

  public void rename(GolemHandle handle, Component name) {
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
    Mob golem = handle.entity();
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
    Player player = platform.player(owner);
    if (player != null) {
      platform.runEntity(player, () -> dissolveCarried(player), () -> {
      });
    }
  }

  public boolean dissolveRevoked(PackedGolem packed, Location at) {
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
    List<ItemStack> drops = new ArrayList<>();
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
    World world = Objects.requireNonNull(at.getWorld());
    for (ItemStack drop : drops) {
      world.dropItemNaturally(at, drop);
    }
    int essence = (int) snapshot.essence();
    if (essence > 0) {
      world.spawn(at, ExperienceOrb.class, orb -> orb.setExperience(essence));
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
      handle(entity).ifPresent(this::unload);
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
    platform.upkeep(owner, Set.of());
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
  protected List<Listener> createCompanionListeners() {
    List<Listener> listeners = new ArrayList<>(3);
    listeners.add(new GolemGuardListener(platform::player, this::handle));
    listeners.add(new GolemEquipmentListener(this::handle));
    if (PaperCompat.hasClass("io.papermc.paper.event.entity.EntityEquipmentChangedEvent")) {
      listeners.add(new GolemEquipmentListener.PaperChanges(this::handle));
    }
    if (PaperCompat.hasClass("com.destroystokyo.paper.event.entity.EntityAddToWorldEvent")) {
      listeners.add(new PaperEntityListener(this));
    }
    if (PaperCompat.hasClass(CopperBrainGuard.EVENT_CLASS)) {
      listeners.add(new CopperBrainGuard());
    }
    return listeners;
  }

  static boolean adoptable(Mob mob) {
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

  void adopt(Mob mob) {
    Optional<GolemState> packedState = GolemState.read(mob);
    if (packedState.isPresent() && mob.getPersistentDataContainer().has(PACKING_KEY, PersistentDataType.BYTE)) {
      Mob previous = packingBodies.putIfAbsent(packedState.get().id(), mob);
      if (previous != null && !previous.getUniqueId().equals(mob.getUniqueId())) {
        mob.remove();
        return;
      }
      packingBodies.put(packedState.get().id(), mob);
      if (!recoverPackingBody(mob, packedState.get())) {
        return;
      }
    }
    AttributeInstance scale = platform.scaleOf(mob);
    GolemRitualMarker.recover(mob, scale);
    if (handle(mob).isPresent()) {
      return;
    }
    Optional<GolemState> state = packedState;
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
      display(mob, handle.state().name().orElse(null));
      evaluate(handle, platform.isOnline(handle.owner()));
    }
  }

  private boolean recoverPackingBody(Mob mob, GolemState state) {
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
    Byte flags = mob.getPersistentDataContainer().get(PACKING_KEY, PersistentDataType.BYTE);
    restorePackingBody(mob, PackingBody.fromFlags(flags == null ? (byte) 0 : flags));
    forgetPacking(state.id());
    return true;
  }

  private void retryPackingBodies() {
    for (Map.Entry<UUID, Mob> entry : packingBodies.entrySet()) {
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

  void adoptLater(Entity entity) {
    if (entity instanceof Mob mob && GolemState.has(mob) && handle(mob).isEmpty()) {
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
    Mob golem = handle.entity();
    golem.setAware(true);
    golem.setInvulnerable(false);
    handle.state().setDormant(false);
    handle.flush();
  }

  private boolean putToSleep(GolemHandle handle) {
    Mob golem = handle.entity();
    golem.setAware(false);
    golem.setTarget(null);
    PaperCompat.stopPathfinding(golem);
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

  private void dissolveCarried(Player player) {
    PlayerInventory inventory = player.getInventory();
    if (inventory == null) {
      return;
    }
    boolean changed = false;
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      ItemStack item = inventory.getItem(slot);
      Optional<PackedGolem> packed = GolemItems.packedGolemOf(item);
      if (packed.isPresent() && packed.get().owner().equals(player.getUniqueId())
          && dissolveRevoked(packed.get(), player.getLocation())) {
        inventory.setItem(slot, null);
        changed = true;
      }
    }
    if (changed) {
      player.saveData();
    }
  }

  private Optional<ItemStack> decodeDrop(UUID id, String encoded) {
    if (encoded == null) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(platform.decodeItem(encoded));
    } catch (RuntimeException corrupt) {
      Adapt.warn("Golem " + id + " contains an unreadable item; it could not be returned.", corrupt);
      return Optional.empty();
    }
  }

  private void releaseContents(GolemHandle handle, boolean dropEquipment) {
    Mob golem = handle.entity();
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
    Optional<ItemStack> tool = heldTool(handle);
    List<ItemStack> worn = handle.equipmentItems();
    int essence = state.essencePoints();
    Location at = golem.getLocation();
    revert(handle);
    forgetRosterEntry(handle);
    if (handle.body() == GolemBody.BONE) {
      platform.crumble(golem);
    }
    World world = golem.getWorld();
    for (ItemStack drop : drops) {
      world.dropItemNaturally(at, drop);
    }
    for (ItemStack item : worn) {
      if (dropEquipment) {
        world.dropItemNaturally(at, item);
      } else {
        platform.returnToOwner(handle.owner(), at, item);
      }
    }
    tool.ifPresent(item -> {
      if (dropEquipment) {
        world.dropItemNaturally(at, item);
      } else {
        platform.returnToOwner(handle.owner(), at, item);
      }
    });
    if (essence > 0) {
      returnEssence(golem, world, at, essence, platform.player(handle.owner()));
    }
  }

  private Optional<ItemStack> heldTool(GolemHandle handle) {
    String held = handle.state().held();
    if (held == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(platform.decodeItem(held));
    } catch (RuntimeException corrupt) {
      Adapt.warn("Golem " + handle.id() + " holds an unreadable tool; it is not dropped.", corrupt);
      return Optional.empty();
    }
  }

  private void revert(GolemHandle handle) {
    Mob golem = handle.entity();
    double pool = handle.hostileDamagePool();
    if (handle.state().held() != null) {
      GolemHeldTool.hide(golem);
    }
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
      e.getDrops().clear();
      e.setDroppedExp(0);
    }
    if (!validated) {
      return;
    }
    GolemSettings current = settings.get();
    GolemState state = handle.state();
    List<ItemStack> drops = e.getDrops();
    drops.addAll(state.carriedItems());
    heldTool(handle).ifPresent(drops::add);
    drops.addAll(handle.equipmentItems());
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

  private Component restoredName(Player owner, GolemSnapshot snapshot) {
    return snapshot.name().orElseGet(() -> Component.text(boundName(owner, snapshot.body())));
  }

  private void undoRestore(Mob golem, UUID owner, Restore request) {
    undoSteps(() -> {
      Optional<GolemHandle> adopted = handle(golem);
      if (adopted.isPresent()) {
        revert(adopted.get());
      } else {
        GolemState.clear(golem);
      }
    }, () -> restow(owner, request));
  }

  private void undoAwaken(Mob golem, UUID owner, Restore request) {
    undoSteps(() -> {
      if (golem != null) {
        undoSteps(() -> handle(golem).ifPresent(this::unindex), golem::remove);
      }
    }, () -> restow(owner, request));
  }

  private void undoPack(Mob golem, UUID owner, GolemRoster roster, UUID id, boolean stowed) {
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

  private void writeBound(Mob golem, GolemState state, Component name) {
    state.setHostileDamagePool(HostileDamagePool.takeUnbound(golem));
    state.setName(name);
    state.write(golem);
    golem.getPersistentDataContainer().set(BOUND_ONCE_KEY, PersistentDataType.BYTE, (byte) 1);
    StackExclusion.exclude(golem);
    golem.setPersistent(true);
    golem.setRemoveWhenFarAway(false);
    if (state.body() == GolemBody.BONE) {
      GolemEquipmentSlot.strip(golem);
      golem.setCanPickupItems(false);
      if (golem instanceof AbstractSkeleton skeleton) {
        PaperCompat.setShouldBurnInDay(skeleton, false);
      }
      EntityEquipment equipment = golem.getEquipment();
      if (equipment != null) {
        for (Map.Entry<GolemEquipmentSlot, String> entry : state.equipment().entrySet()) {
          equipment.setItem(entry.getKey().slot(), platform.decodeItem(entry.getValue()));
          equipment.setDropChance(entry.getKey().slot(), 0F);
        }
      }
    }
    display(golem, name);
    AttributeInstance scale = platform.scaleOf(golem);
    if (scale != null) {
      GolemScale.setModifier(scale, GolemScale.modifierAmount(settings.get().bodyScale(state.body())));
    }
  }

  private void display(Mob golem, Component name) {
    Component shown = platform.nameMode().showsCustomName() ? name : null;
    if (!Objects.equals(PaperCompat.customName(golem), shown)) {
      PaperCompat.customName(golem, shown);
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
        BukkitExperience.award(owner, points);
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
    GolemHandle.ChunkPosition position = handle.trackPosition(handle.entity().getLocation());
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

  private int boundInChunk(Location at, Entity excluded) {
    int bound = 0;
    for (Entity entity : at.getChunk().getEntities()) {
      if (entity != excluded && GolemState.has(entity)) {
        bound++;
      }
    }
    return bound;
  }

  private static long chunkKey(Location location) {
    return GolemRoster.chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }

  private static String boundName(Player owner, GolemBody body) {
    return AdaptLanguage.text(GolemancyMessages.GOLEM_NAME,
        untrusted("owner", owner.getName()),
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

  public record Packing(PackedGolem packed, GolemSnapshot snapshot, ItemStack item, Location at) {
    public Packing {
      Objects.requireNonNull(packed);
      Objects.requireNonNull(snapshot);
      Objects.requireNonNull(item);
      at = Objects.requireNonNull(at).clone();
    }

    @Override
    public Location at() {
      return at.clone();
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
