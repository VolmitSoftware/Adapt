package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.api.golem.item.DormantGolem;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.event.world.WorldLifecycleEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.data.StackExclusion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemRegistryTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

  private final Map<Long, List<EntityRef>> chunkEntities = new HashMap<>();
  private final AtomicReference<GolemSettings> settings = new AtomicReference<>(GolemSettings.defaults());
  private FakeGolemPlatform platform;
  private RecordingGolemLifecycle lifecycle;
  private GolemRegistry registry;
  private WorldRef world;
  private PlayerRef owner;
  private ComponentPort experienceEvents;
  private MockedStatic<GolemItems> items;
  private ItemRef coreItem;
  private ItemRef runeItem;
  private ItemRef dormantItem;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    lifecycle = new RecordingGolemLifecycle();
    registry = new GolemRegistry(platform, settings::get, lifecycle);
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    lenient().when(world.minHeight()).thenReturn(-64);
    lenient().when(world.maxHeight()).thenReturn(320);
    lenient().when(world.entitiesIn(any(Box.class), nullable(Predicate.class))).thenAnswer(invocation -> chunkEntities(invocation.<Box>getArgument(0)));
    owner = mock(PlayerRef.class);
    experienceEvents = mock(ComponentPort.class, CALLS_REAL_METHODS);
    AdaptPorts.install(ComponentPort.class, experienceEvents);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    lenient().when(owner.name()).thenReturn("Steve");
    coreItem = mock(ItemRef.class);
    runeItem = mock(ItemRef.class);
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.core(any(GolemCoreType.class))).thenReturn(coreItem);
    items.when(() -> GolemItems.rune(any(GolemRune.class))).thenReturn(runeItem);
    dormantItem = mock(ItemRef.class);
    items.when(() -> GolemItems.dormantBlock(any(GolemBody.class), anyInt(), anyBoolean())).thenCallRealMethod();
    items.when(() -> GolemItems.isDormantBlock(any())).thenCallRealMethod();
    items.when(() -> GolemItems.dormantGolem(any(DormantGolem.class))).thenReturn(dormantItem);
  }

  @AfterEach
  void tearDown() {
    AdaptPorts.uninstall(ComponentPort.class);
    items.close();
  }

  @Test
  void bindWritesStateRosterAndBoundFlags() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, -20)));

    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    GolemState stored = GolemState.read(golem).orElseThrow();
    assertThat(stored.id()).isEqualTo(handle.id());
    assertThat(stored.owner()).isEqualTo(OWNER);
    assertThat(stored.body()).isEqualTo(GolemBody.IRON);
    assertThat(stored.cores()).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(stored.home()).isEqualTo(GolemAnchor.of(WORLD, 3, 64, -20));
    assertThat(stored.previouslyBound()).isFalse();
    assertThat(stored.dormant()).isFalse();

    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(entry.world()).isEqualTo(WORLD);
    assertThat(entry.chunkKey()).isEqualTo(GolemRoster.chunkKey(0, -2));
    assertThat(entry.lastSeen()).isEqualTo(platform.now);
    assertThat(platform.changes(OWNER)).isPositive();

    assertThat(platform.scale(golem)).singleElement().satisfies(modifier -> {
      assertThat(modifier.key()).isEqualTo(GolemScale.MODIFIER_KEY);
      assertThat(modifier.amount()).isCloseTo(-0.64D, offset(1e-9));
    });
    verify(golem).setPersistent(true);
    verify(golem).setRemoveWhenFarAway(false);
    assertThat(StackExclusion.isExcluded(golem)).isTrue();
    ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
    verify(golem).setCustomNameJson(name.capture());
    assertThat(GolemSnapshot.plainName(name.getValue())).isEqualTo("Steve's Iron Golem");

    assertThat(registry.handle(golem)).contains(handle);
    assertThat(registry.loadedOf(OWNER)).containsExactly(handle);
    assertThat(registry.ownerOf(golem)).contains(OWNER);
    assertThat(handle.isDormant()).isFalse();
    assertThat(registry.activeCount()).isEqualTo(1);
  }

  @Test
  void bindMovesTheUnboundPoolIntoTheState() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    golem.data().set(HostileDamagePool.POOL_KEY, 12D);

    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    assertThat(GolemState.read(golem).orElseThrow().hostileDamagePool()).isEqualTo(12D);
    assertThat(golem.data().get(HostileDamagePool.POOL_KEY)).isNull();
  }

  @Test
  void bindBlockersCoverEveryPrecondition() {
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    platform.online.add(OWNER);

    assertThat(registry.bindBlock(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).contains(GolemRegistry.BindBlock.OWNER_DATA_UNAVAILABLE);
    GolemRoster roster = onlineOwner();
    assertThat(registry.bindBlock(owner, golem, GolemBody.FROST, GolemCoreType.BLANK)).contains(GolemRegistry.BindBlock.BODY_MISMATCH);
    assertThat(registry.bindBlock(owner, golem, GolemBody.IRON, GolemCoreType.SORTER)).contains(GolemRegistry.BindBlock.CORE_NOT_ALLOWED);
    assertThat(registry.bindBlock(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).isEmpty();

    settings.set(withCaps(1, 1, 1500));
    roster.add(new GolemRoster.Binding(UUID.randomUUID(), GolemBody.FROST, GolemCoreType.BLANK, WORLD, 0L, 0L));
    assertThat(registry.bindBlock(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).contains(GolemRegistry.BindBlock.PLAYER_CAP);

    settings.set(withCaps(5, 1, 1500));
    MobRef neighbour = golem(new AtomicReference<>(at(2, 64, 2)));
    registry.bind(owner, neighbour, GolemBody.IRON, GolemCoreType.BLANK);
    assertThat(registry.bindBlock(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).contains(GolemRegistry.BindBlock.CHUNK_CAP);
    assertThatThrownBy(() -> registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).isInstanceOf(IllegalStateException.class);

    settings.set(GolemSettings.defaults());
    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    assertThat(registry.bindBlock(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).contains(GolemRegistry.BindBlock.ALREADY_BOUND);
  }

  @Test
  void aCopperGolemTakesTheBreakerCoreAtBind() {
    MobRef golem = GolemTestEntities.golem(GolemBody.COPPER);
    Loc location = at(0, 64, 0);
    lenient().when(golem.location()).thenReturn(location);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    track(golem, location);
    platform.online.add(OWNER);
    onlineOwner();

    assertThat(registry.bindBlock(owner, golem, GolemBody.COPPER, GolemCoreType.BREAKER)).isEmpty();
    GolemHandle handle = registry.bind(owner, golem, GolemBody.COPPER, GolemCoreType.BREAKER);

    assertThat(handle.body()).isEqualTo(GolemBody.COPPER);
    assertThat(handle.core()).isEqualTo(GolemCoreType.BREAKER);
  }

  @Test
  void releaseDropsTheLoadoutAndRevertsToVanilla() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 2)));
    handle.setHostileDamagePool(9D);

    assertThat(registry.release(handle)).isTrue();

    verify(world).dropItemNaturally(any(Vec3.class), eq(coreItem));
    verify(world).dropItemNaturally(any(Vec3.class), eq(runeItem));
    assertThat(GolemState.has(golem)).isFalse();
    assertThat(roster.golem(handle.id())).isEmpty();
    assertThat(registry.handle(golem)).isEmpty();
    assertThat(registry.loadedOf(OWNER)).isEmpty();
    assertThat(registry.activeCount()).isZero();
    assertThat(StackExclusion.isExcluded(golem)).isFalse();
    assertThat(golem.data().get(HostileDamagePool.POOL_KEY)).isEqualTo(9D);
    verify(golem).setCustomNameJson(isNull());
    assertThat(platform.scale(golem)).isEmpty();
    assertThat(handle.isRetired()).isTrue();
  }

  @Test
  void releasingAGolemMidResizeLeavesNoSizeModifierBehind() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    handle.setColossus(true);
    platform.scale(golem).add(new AttributeMod(GolemScale.RITUAL_KEY, -0.3D, AttributeOperation.ADD_VALUE));

    assertThat(registry.release(handle)).isTrue();

    assertThat(platform.scale(golem)).isEmpty();
    assertThat(GolemState.has(golem)).isFalse();
  }

  @Test
  void aBoundGolemLoadedWithAStaleResizeStepKeepsOnlyItsSettledScale() {
    GolemRoster roster = onlineOwner();
    MobRef golem = activeGolem(roster, at(0, 64, 0));
    AttributeMod settled = new AttributeMod(GolemScale.MODIFIER_KEY, -0.64D, AttributeOperation.ADD_VALUE);
    platform.scale(golem).add(settled);
    platform.scale(golem).add(new AttributeMod(GolemScale.RITUAL_KEY, 0.3D, AttributeOperation.ADD_VALUE));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(registry.handle(golem)).isPresent();
    assertThat(platform.scale(golem)).containsExactly(settled);
  }

  @Test
  void aColossusGolemAdoptedWithNoRegisteredFormReturnsToItsBoundSize() {
    GolemRoster roster = onlineOwner();
    MobRef golem = activeGolem(roster, at(0, 64, 0));
    GolemState grown = GolemState.read(golem).orElseThrow();
    grown.setColossus(true);
    grown.write(golem);

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    GolemHandle handle = registry.handle(golem).orElseThrow();
    assertThat(handle.state().colossus()).isFalse();
    assertThat(GolemState.read(golem).orElseThrow().colossus()).isFalse();
    assertThat(platform.scale(golem)).singleElement().satisfies(modifier -> assertThat(modifier.amount()).isCloseTo(-0.64D, offset(1e-9)));
  }

  @Test
  void bindingAppliesTheStatSourcesAndReleaseRemovesTheirModifiers() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    AttributeMod foreign = new AttributeMod(Id.of("other", "plating"), 1D, AttributeOperation.ADD_VALUE);
    armor.modifiers.add(foreign);
    GolemStatSource frame = statSource("iron_frame", new AtomicReference<>(4D));
    GolemStats.register(frame);
    try {
      GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

      assertThat(armor.value()).isEqualTo(5D);

      assertThat(registry.release(handle)).isTrue();

      assertThat(armor.modifiers).containsExactly(foreign);
      assertThat(platform.scale(golem)).isEmpty();
    } finally {
      GolemStats.unregister(frame);
    }
  }

  @Test
  void statsAreRecomputedWhenAGolemWakesButNotWhileItSleeps() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    AtomicReference<Double> level = new AtomicReference<>(2D);
    GolemStatSource frame = statSource("iron_frame", level);
    GolemStats.register(frame);
    try {
      registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
      registry.on(new PlayerQuitEvent(owner));
      platform.quit(OWNER);
      level.set(6D);

      assertThat(armor.value()).isEqualTo(2D);

      platform.join(OWNER, roster);
      registry.on(new PlayerJoinEvent(owner));

      assertThat(armor.value()).isEqualTo(6D);
    } finally {
      GolemStats.unregister(frame);
    }
  }

  @Test
  void anAdoptedGolemKeepsItsPersistedStatsAndPicksUpChanges() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    MobRef golem = persistedGolem(id, true, at(0, 64, 0));
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    AttributeMod persisted = new AttributeMod(GolemStats.key("iron_frame", GolemStat.ARMOR), 2D, AttributeOperation.ADD_VALUE);
    armor.modifiers.add(persisted);
    GolemStatSource frame = statSource("iron_frame", new AtomicReference<>(2D));
    GolemStats.register(frame);
    try {
      registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

      assertThat(armor.modifiers).containsExactly(persisted);
    } finally {
      GolemStats.unregister(frame);
    }
  }

  @Test
  void onlyAnEntitysFirstBindIsNotPreviouslyBound() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle first = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    registry.release(first);

    GolemHandle second = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    assertThat(first.state().previouslyBound()).isFalse();
    assertThat(second.state().previouslyBound()).isTrue();
    assertThat(second.id()).isNotEqualTo(first.id());
  }

  @Test
  void golemLoadingWhileItsOwnerIsOfflineStaysDormant() {
    MobRef golem = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    GolemHandle handle = registry.handle(golem).orElseThrow();
    assertThat(handle.isDormant()).isTrue();
    verify(golem).setAware(false);
    verify(golem).setTarget(null);
    verify(golem).setInvulnerable(true);
    assertThat(GolemState.read(golem).orElseThrow().dormant()).isTrue();
    assertThat(registry.activeCount()).isZero();
  }

  @Test
  void dormantGolemsAreNotInvulnerableWhenConfiguredOff() {
    settings.set(new GolemSettings(false, false, 1500, 8, 32, Map.of(), Map.of(), Map.of(), 0.5D, 0.5D));
    MobRef golem = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    verify(golem).setInvulnerable(false);
  }

  @Test
  void golemLoadingWithAnActiveEntryWakesAndUpdatesTheRoster() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(9, 9), 5L));
    MobRef golem = persistedGolem(id, true, at(33, 64, 1));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    GolemHandle handle = registry.handle(golem).orElseThrow();
    assertThat(handle.isDormant()).isFalse();
    verify(golem).setAware(true);
    verify(golem).setInvulnerable(false);
    assertThat(GolemState.read(golem).orElseThrow().dormant()).isFalse();
    GolemRoster.GolemEntry entry = roster.golem(id).orElseThrow();
    assertThat(entry.chunkKey()).isEqualTo(GolemRoster.chunkKey(2, 0));
    assertThat(entry.lastSeen()).isEqualTo(platform.now);
  }

  @Test
  void golemsWhoseIdIsNotActiveRevertWithoutDrops() {
    GolemRoster roster = onlineOwner();
    UUID packed = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(packed, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(packed, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);
    MobRef unknown = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));
    MobRef duplicateOfPacked = persistedGolem(packed, false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(unknown, duplicateOfPacked)));

    assertThat(GolemState.has(unknown)).isFalse();
    assertThat(GolemState.has(duplicateOfPacked)).isFalse();
    assertThat(registry.handle(unknown)).isEmpty();
    assertThat(registry.handle(duplicateOfPacked)).isEmpty();
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    assertThat(roster.golem(packed).orElseThrow().status()).isEqualTo(GolemRoster.Status.PACKED);
  }

  @Test
  void missingEntriesAreRestoredWhenTheGolemTurnsUp() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(id, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    MobRef golem = persistedGolem(id, false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(registry.handle(golem).orElseThrow().isDormant()).isFalse();
    assertThat(platform.changes(OWNER)).isPositive();
  }

  @Test
  void unknownOwnerDataStaysDormantUntilTheRosterArrives() {
    platform.online.add(OWNER);
    UUID id = UUID.randomUUID();
    MobRef golem = persistedGolem(id, false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(registry.handle(golem).orElseThrow().isDormant()).isTrue();
    assertThat(registry.hasTickDemand()).isTrue();

    GolemRoster roster = new GolemRoster();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    platform.rosters.put(OWNER, roster);
    registry.onTick();

    assertThat(registry.handle(golem).orElseThrow().isDormant()).isFalse();
  }

  @Test
  void ownerQuitSleepsTheirGolemsAndJoinWakesThem() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    registry.on(new PlayerQuitEvent(owner));
    platform.quit(OWNER);

    assertThat(handle.isDormant()).isTrue();
    verify(golem).setAware(false);
    assertThat(GolemState.read(golem).orElseThrow().dormant()).isTrue();
    assertThat(registry.activeCount()).isZero();

    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner));

    assertThat(handle.isDormant()).isFalse();
    verify(golem, atLeastOnce()).setAware(true);
    assertThat(GolemState.read(golem).orElseThrow().dormant()).isFalse();
    assertThat(registry.activeCount()).isEqualTo(1);
  }

  @Test
  void offlineGuardiansStayAwakeWhenConfigured() {
    settings.set(new GolemSettings(true, true, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 0.5D, 0.5D));
    onlineOwner();
    MobRef guardian = golem(new AtomicReference<>(at(0, 64, 0)));
    MobRef companion = golem(new AtomicReference<>(at(1, 64, 0)));
    GolemHandle guard = registry.bind(owner, guardian, GolemBody.IRON, GolemCoreType.GUARDIAN);
    GolemHandle follower = registry.bind(owner, companion, GolemBody.IRON, GolemCoreType.BLANK);

    registry.on(new PlayerQuitEvent(owner));

    assertThat(guard.isDormant()).isFalse();
    assertThat(follower.isDormant()).isTrue();
  }

  @Test
  void offlineBoneGuardiansWaitForTheirOwnersServantOwnershipToBeValidated() {
    settings.set(new GolemSettings(true, true, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 0.5D, 0.5D));
    onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);

    registry.on(new PlayerQuitEvent(owner));

    assertThat(handle.isDormant()).isTrue();
    assertThat(handle.isValidated()).isFalse();
    assertThat(registry.activeCount()).isZero();
  }

  @Test
  void serverCapHoldsExtraGolemsDormantUntilRoomFrees() {
    settings.set(withCaps(32, 8, 1));
    GolemRoster roster = onlineOwner();
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(firstId, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.add(new GolemRoster.Binding(secondId, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    MobRef first = persistedGolem(firstId, false, at(0, 64, 0));
    MobRef second = persistedGolem(secondId, false, at(1, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(first, second)));

    assertThat(registry.handle(first).orElseThrow().isDormant()).isFalse();
    assertThat(registry.handle(second).orElseThrow().isDormant()).isTrue();
    assertThat(registry.activeCount()).isEqualTo(1);

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(first)));
    registry.onTick();

    assertThat(registry.handle(second).orElseThrow().isDormant()).isFalse();
    assertThat(registry.activeCount()).isEqualTo(1);
  }

  @Test
  void deathDropsTheRolledLoadoutAndRemovesTheEntry() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 1D, 0D));
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 2)));
    List<ItemRef> drops = new ArrayList<>();
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.entity()).thenReturn(golem);
    when(death.drops()).thenReturn(drops);

    registry.on(death);

    assertThat(drops).containsExactly(coreItem);
    assertThat(roster.golem(handle.id())).isEmpty();
    assertThat(registry.handle(golem)).isEmpty();
    assertThat(registry.activeCount()).isZero();
  }

  @Test
  void releaseReturnsTheHeldToolToItsOwnerOnceAndNeverDropsItWithTheLoadout() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BREAKER);
    assertThat(handle.setHeldEncoded(platform.encodeItem(GolemTestItems.stack(Id.minecraft("diamond_pickaxe"), 1)))).isTrue();

    assertThat(registry.release(handle)).isTrue();

    assertThat(platform.returned).extracting(FakeGolemPlatform.Returned::owner).containsExactly(OWNER);
    assertThat(platform.returned).extracting(returned -> returned.item().type()).containsExactly(Id.minecraft("diamond_pickaxe"));
    assertThat(platform.returned).extracting(returned -> returned.location().blockX()).containsExactly(0);
    verify(world, never()).dropItemNaturally(any(Vec3.class), argThat(item -> item != null && Id.minecraft("diamond_pickaxe").equals(item.type())));
  }

  @Test
  void deathDropsTheHeldToolWithTheCarriedItems() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 0D, 0D));
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.ANGLER);
    assertThat(handle.setHeldEncoded(platform.encodeItem(GolemTestItems.stack(Id.minecraft("fishing_rod"), 1)))).isTrue();
    List<ItemRef> drops = new ArrayList<>();
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.entity()).thenReturn(golem);
    when(death.drops()).thenReturn(drops);

    registry.on(death);

    assertThat(drops).extracting(ItemRef::type).containsExactly(Id.minecraft("fishing_rod"));
  }

  @Test
  void aRetiredGolemRefusesANewHeldTool() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.ANGLER);

    assertThat(registry.release(handle)).isTrue();

    assertThat(handle.setHeldEncoded(platform.encodeItem(GolemTestItems.stack(Id.minecraft("fishing_rod"), 1)))).isFalse();
    assertThat(handle.state().held()).isNull();
  }

  @Test
  void aDyingGolemDropsItsStoredEssenceAsExperience() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(140.6D);
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.entity()).thenReturn(golem);
    when(death.drops()).thenReturn(new ArrayList<>());
    when(death.droppedExp()).thenReturn(5);

    registry.on(death);

    verify(death).setDroppedExp(145);
  }

  @Test
  void releasingAGolemGivesItsStoredEssenceToTheOnlineOwner() {
    onlineOwner();
    platform.players.put(OWNER, owner);
    when(owner.isOnline()).thenReturn(true);
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(60.7D);

    assertThat(registry.release(handle)).isTrue();

    verify(owner).giveExp(60);
    verify(experienceEvents).awardExperience(owner, 60);
    verify(world, never()).spawnExperience(any(Vec3.class), anyInt());
  }

  @Test
  void releasingAGolemWhileItsOwnerIsAwayDropsTheEssenceAsAnExperienceOrb() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(60D);

    assertThat(registry.release(handle)).isTrue();

    verify(world).spawnExperience(any(Vec3.class), eq(60));
    verify(owner, never()).giveExp(anyInt());
  }

  @Test
  void aRefusedGiftThatAlsoRetiresDropsTheReleasedEssenceOnlyOnce() {
    onlineOwner();
    platform.players.put(OWNER, owner);
    when(owner.isOnline()).thenReturn(true);
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(60D);
    platform.refuseRetiring = true;

    assertThat(registry.release(handle)).isTrue();

    verify(world, times(1)).spawnExperience(any(Vec3.class), anyInt());
    verify(owner, never()).giveExp(anyInt());
  }

  @Test
  void anOwnerWhoLeavesWhileTheGolemRefusesTheDropStillGetsTheEssenceDroppedOnceAtTheGolemsLastLocation() {
    GolemHandle handle = releasableGatherer(60D);
    EntityRef golem = handle.entity();
    platform.deferring.add(owner);

    assertThat(registry.release(handle)).isTrue();
    platform.refusing.add(golem);
    platform.retireDelayed();

    assertThat(platform.regionTasks).extracting(FakeGolemPlatform.RegionTask::location).containsExactly(at(0, 64, 0));
    verify(world, never()).spawnExperience(any(Vec3.class), anyInt());
    platform.runRegionTasks();

    assertDroppedOnceAt(at(0, 64, 0), 60);
  }

  @Test
  void anOwnerWhoLeavesWhileTheGolemRetiresBeforeTheDropStillGetsTheEssenceDroppedOnceAtTheGolemsLastLocation() {
    GolemHandle handle = releasableGatherer(60D);
    EntityRef golem = handle.entity();
    platform.deferring.add(owner);

    assertThat(registry.release(handle)).isTrue();
    platform.deferring.add(golem);
    platform.retireDelayed();
    assertThat(platform.regionTasks).isEmpty();
    platform.retireDelayed();

    assertThat(platform.regionTasks).extracting(FakeGolemPlatform.RegionTask::location).containsExactly(at(0, 64, 0));
    platform.runRegionTasks();

    assertDroppedOnceAt(at(0, 64, 0), 60);
  }

  @Test
  void returnedEssenceThatNoRegionWillDropIsLoggedAsLost() {
    GolemHandle handle = releasableGatherer(60D);
    EntityRef golem = handle.entity();
    platform.deferring.add(owner);

    assertThat(registry.release(handle)).isTrue();
    platform.refusing.add(golem);
    platform.refuseRegions = true;
    try (MockedStatic<AdaptLog> adapt = mockStatic(AdaptLog.class)) {
      platform.retireDelayed();

      adapt.verify(() -> AdaptLog.warn(contains("60 experience points")), times(1));
    }
    verify(world, never()).spawnExperience(any(Vec3.class), anyInt());
    verify(owner, never()).giveExp(anyInt());
  }

  @Test
  void aGolemWithNoEssenceDropsNoExtraExperience() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);

    assertThat(registry.release(handle)).isTrue();

    verify(world, never()).spawnExperience(any(Vec3.class), anyInt());
  }

  @Test
  void statsAndPoolWaitForTheDebouncedFlush() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    handle.addKills(2);
    handle.addDamage(7.5D);
    handle.setHostileDamagePool(4D);

    assertThat(platform.delayed).singleElement().satisfies(task -> assertThat(task.delayTicks()).isEqualTo(100));
    assertThat(GolemState.read(golem).orElseThrow().stats().kills()).isZero();

    platform.now += 4_000L;
    platform.runDelayed();
    assertThat(GolemState.read(golem).orElseThrow().stats().kills()).isZero();
    assertThat(platform.delayed).singleElement().satisfies(task -> assertThat(task.delayTicks()).isEqualTo(20));

    platform.now += 1_000L;
    platform.runDelayed();
    GolemState stored = GolemState.read(golem).orElseThrow();
    assertThat(stored.stats().kills()).isEqualTo(2L);
    assertThat(stored.stats().damage()).isEqualTo(7.5D);
    assertThat(stored.hostileDamagePool()).isEqualTo(4D);
    assertThat(platform.delayed).isEmpty();
  }

  @Test
  void loadoutAndCommandChangesFlushImmediately() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    handle.setCores(List.of(GolemCoreType.GUARDIAN, GolemCoreType.LUMBER));
    handle.setMode(GolemMode.STAY);
    handle.setChannels(ChannelMask.NONE | 0x0004);
    handle.setSnowTrail(true);

    GolemState stored = GolemState.read(golem).orElseThrow();
    assertThat(stored.cores()).containsExactly(GolemCoreType.GUARDIAN, GolemCoreType.LUMBER);
    assertThat(stored.mode()).isEqualTo(GolemMode.STAY);
    assertThat(stored.channels()).isEqualTo(0x0004);
    assertThat(stored.snowTrail()).isTrue();
    assertThat(platform.delayed).isEmpty();
  }

  @Test
  void removingACarriedEntryFlushesAndKeepsTheOtherEntries() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.state().setCarried(List.of("first", "second", "second", "unreadable"));

    assertThat(handle.removeCarried("second")).isTrue();
    assertThat(handle.removeCarried("absent")).isFalse();

    assertThat(GolemState.read(golem).orElseThrow().carried()).containsExactly("first", "second", "unreadable");
  }

  @Test
  void anUnloadedHandleNeverHandsOutCarriedEntries() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.state().setCarried(List.of("first"));
    handle.flush();
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)));

    assertThat(handle.removeCarried("first")).isFalse();
    assertThat(GolemState.read(golem).orElseThrow().carried()).containsExactly("first");
  }

  @Test
  void releasingAMissingEntryRemovesItAndSavesTheRoster() {
    GolemRoster roster = onlineOwner();
    UUID missing = UUID.randomUUID();
    UUID active = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(missing, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.add(new GolemRoster.Binding(active, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(missing, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    int changesBefore = platform.changes(OWNER);

    assertThat(registry.releaseEntry(OWNER, active)).isFalse();
    assertThat(registry.releaseEntry(OWNER, UUID.randomUUID())).isFalse();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);

    assertThat(registry.releaseEntry(OWNER, missing)).isTrue();

    assertThat(roster.golem(missing)).isEmpty();
    assertThat(roster.golem(active)).isPresent();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore + 1);
    assertThat(registry.releaseEntry(OWNER, missing)).isFalse();
  }

  @Test
  void adminReleaseOfALoadedGolemReleasesItTheWayItsOwnerWould() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 2)));

    CompletableFuture<GolemRegistry.AdminRelease> released = registry.releaseFor(OWNER, handle.id());

    assertThat(released).isCompletedWithValue(GolemRegistry.AdminRelease.RELEASED);
    verify(world).dropItemNaturally(any(Vec3.class), eq(coreItem));
    verify(world).dropItemNaturally(any(Vec3.class), eq(runeItem));
    assertThat(GolemState.has(golem)).isFalse();
    assertThat(roster.golem(handle.id())).isEmpty();
    assertThat(registry.handle(golem)).isEmpty();
  }

  @Test
  void adminReleaseOfALoadedGolemRunsOnItsOwnThread() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.deferring.add(golem);

    CompletableFuture<GolemRegistry.AdminRelease> released = registry.releaseFor(OWNER, handle.id());

    assertThat(released).isNotDone();
    assertThat(GolemState.has(golem)).isTrue();
    platform.runDelayed();
    assertThat(released).isCompletedWithValue(GolemRegistry.AdminRelease.RELEASED);
    assertThat(roster.golem(handle.id())).isEmpty();
  }

  @Test
  void adminReleaseOfAGolemThatRetiresBeforeItsTurnIsRefused() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.refusing.add(golem);

    assertThat(registry.releaseFor(OWNER, handle.id())).isCompletedWithValue(GolemRegistry.AdminRelease.REFUSED);
    assertThat(roster.golem(handle.id())).isPresent();
  }

  @Test
  void adminReleaseOfAnUnloadedGolemForgetsMissingPackedAndShatteredEntriesButRefusesActiveOnes() {
    GolemRoster roster = onlineOwner();
    UUID active = UUID.randomUUID();
    UUID missing = UUID.randomUUID();
    UUID packed = UUID.randomUUID();
    UUID shattered = UUID.randomUUID();
    for (UUID id : List.of(active, missing, packed, shattered)) {
      roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    }
    roster.transition(missing, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    roster.stow(packed, GolemRoster.Status.PACKED, "{\"packed\":true}");
    roster.stow(shattered, GolemRoster.Status.SHATTERED, "{\"shattered\":true}");
    int changesBefore = platform.changes(OWNER);

    assertThat(registry.releaseFor(OWNER, active)).isCompletedWithValue(GolemRegistry.AdminRelease.ACTIVE_UNLOADED);
    assertThat(registry.releaseFor(OWNER, missing)).isCompletedWithValue(GolemRegistry.AdminRelease.RELEASED);
    assertThat(registry.releaseFor(OWNER, packed)).isCompletedWithValue(GolemRegistry.AdminRelease.RELEASED);
    assertThat(registry.releaseFor(OWNER, shattered)).isCompletedWithValue(GolemRegistry.AdminRelease.RELEASED);
    assertThat(registry.releaseFor(OWNER, UUID.randomUUID())).isCompletedWithValue(GolemRegistry.AdminRelease.REFUSED);

    assertThat(roster.golems()).extracting(GolemRoster.GolemEntry::id).containsExactly(active);
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore + 3);
  }

  @Test
  void adminReleaseNeedsTheOwnersRoster() {
    assertThat(registry.releaseFor(OWNER, UUID.randomUUID())).isCompletedWithValue(GolemRegistry.AdminRelease.REFUSED);
  }

  @Test
  void wakingAnOwnerRechecksTheirLoadedGolemsAndWakesThoseThatMay() {
    UUID id = UUID.randomUUID();
    MobRef golem = persistedGolem(id, false, at(0, 64, 0));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));
    GolemRoster roster = new GolemRoster();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    platform.join(OWNER, roster);
    assertThat(registry.handle(golem).orElseThrow().isDormant()).isTrue();

    assertThat(registry.wake(OWNER)).isEqualTo(1);

    GolemHandle handle = registry.handle(golem).orElseThrow();
    assertThat(handle.isDormant()).isFalse();
    assertThat(handle.isValidated()).isTrue();
    assertThat(registry.wake(UUID.randomUUID())).isZero();
  }

  @Test
  void releasingAMissingEntryNeedsTheOwnersRoster() {
    GolemRoster roster = onlineOwner();
    UUID missing = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(missing, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(missing, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    platform.quit(OWNER);

    assertThat(registry.releaseEntry(OWNER, missing)).isFalse();
    assertThat(roster.golem(missing)).isPresent();
  }

  @Test
  void aReleasedMissingGolemThatTurnsUpRevertsWithoutDrops() {
    GolemRoster roster = onlineOwner();
    UUID missing = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(missing, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    roster.transition(missing, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    registry.releaseEntry(OWNER, missing);
    MobRef golem = persistedGolem(missing, false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(GolemState.has(golem)).isFalse();
    assertThat(registry.handle(golem)).isEmpty();
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void unloadFlushesPendingStatsAndRemembersWhereTheGolemWent() {
    GolemRoster roster = onlineOwner();
    AtomicReference<Loc> location = new AtomicReference<>(at(0, 64, 0));
    MobRef golem = golem(location);
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.addKills(3);
    location.set(at(40, 70, -40));
    platform.now += 60_000L;

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)));

    assertThat(GolemState.read(golem).orElseThrow().stats().kills()).isEqualTo(3L);
    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.chunkKey()).isEqualTo(GolemRoster.chunkKey(2, -3));
    assertThat(entry.lastSeen()).isEqualTo(platform.now);
    assertThat(registry.handle(golem)).isEmpty();
    assertThat(registry.activeCount()).isZero();

    handle.addKills(1);
    platform.now += 10_000L;
    platform.runDelayed();
    assertThat(GolemState.read(golem).orElseThrow().stats().kills()).isEqualTo(3L);
  }

  @Test
  void worldSaveFlushesDirtyGolems() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.addItemsMoved(64);

    registry.on(new WorldLifecycleEvent(world, WorldLifecycleEvent.Phase.SAVE));

    assertThat(GolemState.read(golem).orElseThrow().stats().itemsMoved()).isEqualTo(64L);
  }

  @Test
  void entriesWhoseChunkLoadsWithoutTheGolemBecomeMissing() {
    GolemRoster roster = onlineOwner();
    UUID absent = UUID.randomUUID();
    UUID arriving = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(absent, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(5, 5), 0L));
    roster.add(new GolemRoster.Binding(arriving, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(5, 5), 0L));
    registry.on(new PlayerJoinEvent(owner));

    registry.on(new ChunkEvent(world, new ChunkPos(5, 5), ChunkEvent.Phase.LOAD, false));
    platform.now += 2_000L;
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(5, 5), ChunkEntitiesEvent.Phase.LOAD, List.of(persistedGolem(arriving, false, at(80, 64, 80)))));
    platform.now += 2_999L;
    registry.onTick();
    assertThat(roster.golem(absent).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);

    platform.now += 1L;
    registry.onTick();

    assertThat(roster.golem(absent).orElseThrow().status()).isEqualTo(GolemRoster.Status.MISSING);
    assertThat(roster.golem(arriving).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void joiningNextToAnAlreadyLoadedChunkStartsTheMissingCountdown() {
    GolemRoster roster = onlineOwner();
    UUID absent = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(absent, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(1, 1), 0L));
    platform.loadedChunks.add(WORLD + ":" + GolemRoster.chunkKey(1, 1));

    registry.on(new PlayerJoinEvent(owner));
    platform.now += GolemMissingWatch.GRACE_MILLIS;
    registry.onTick();

    assertThat(roster.golem(absent).orElseThrow().status()).isEqualTo(GolemRoster.Status.MISSING);
  }

  @Test
  void aSecondLoadedCopyOfAGolemReverts() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    MobRef original = persistedGolem(id, false, at(0, 64, 0));
    MobRef copy = persistedGolem(id, false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(original)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(copy)));

    assertThat(GolemState.has(original)).isTrue();
    assertThat(registry.handle(original)).isPresent();
    assertThat(GolemState.has(copy)).isFalse();
    assertThat(registry.handle(copy)).isEmpty();
  }

  @Test
  void acceptingALoadedGolemLeavesItsSightingAloneAndSavesNothing() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.raiseSighting(id, 3L);
    MobRef golem = withSighting(persistedGolem(id, false, at(0, 64, 0)), 3L);
    int changesBefore = platform.changes(OWNER);

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(registry.handle(golem).orElseThrow().isValidated()).isTrue();
    assertThat(roster.golem(id).orElseThrow().sighting()).isEqualTo(3L);
    assertThat(GolemState.read(golem).orElseThrow().sighting()).isEqualTo(3L);
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
  }

  @Test
  void aCopyAheadOfItsRosterIsKeptAndRaisesTheRosterToMatch() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    MobRef golem = withSighting(persistedGolem(id, false, at(0, 64, 0)), 5L);
    int changesBefore = platform.changes(OWNER);

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(registry.handle(golem).orElseThrow().isValidated()).isTrue();
    assertThat(roster.golem(id).orElseThrow().sighting()).isEqualTo(5L);
    assertThat(GolemState.read(golem).orElseThrow().sighting()).isEqualTo(5L);
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore + 1);
  }

  @Test
  void unloadingAValidatedGolemAdvancesItsSightingOnTheBodyAndInTheRosterBeforeSaving() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.raiseSighting(id, 3L);
    MobRef golem = withSighting(persistedGolem(id, false, at(0, 64, 0)), 3L);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));
    List<Long> savedBodySightings = new ArrayList<>();
    platform.rosterListener = changed -> savedBodySightings.add(GolemState.read(golem).orElseThrow().sighting());

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)));

    assertThat(roster.golem(id).orElseThrow().sighting()).isEqualTo(4L);
    assertThat(GolemState.read(golem).orElseThrow().sighting()).isEqualTo(4L);
    assertThat(savedBodySightings).isNotEmpty().allSatisfy(sighting -> assertThat(sighting).isEqualTo(4L));
  }

  @Test
  void unloadingAnUnvalidatedOrInactiveGolemLeavesItsSightingAlone() {
    MobRef offline = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(offline)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(offline)));
    assertThat(GolemState.read(offline).orElseThrow().sighting()).isZero();

    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    roster.transition(handle.id(), GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)));

    assertThat(GolemState.read(golem).orElseThrow().sighting()).isZero();
    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isZero();
  }

  @Test
  void aCrashTwinThatLoadsAfterItsCopyUnloadedRevertsWithoutDropsWhileTheUnloadedCopyStaysBound() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    MobRef first = persistedGolem(id, false, at(0, 64, 0));
    MobRef twin = persistedGolem(id, false, at(40, 64, 40));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(first)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(first)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(twin)));

    assertThat(GolemState.has(twin)).isFalse();
    assertThat(registry.handle(twin)).isEmpty();
    verify(twin).setAware(true);
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(roster.golem(id).orElseThrow().sighting()).isEqualTo(1L);

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(first)));

    assertThat(registry.handle(first).orElseThrow().isValidated()).isTrue();
    assertThat(roster.golem(id).orElseThrow().sighting()).isEqualTo(1L);
  }

  @Test
  void aGolemWhoseUnloadWriteIsLostInACrashLoadsStaleRevertsWithoutDropsAndGoesMissing() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    MobRef golem = persistedGolem(id, false, at(0, 64, 0));
    GolemState chunkOnDisk = GolemState.read(golem).orElseThrow();
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)));
    GolemRoster savedRoster = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);
    assertThat(savedRoster.golem(id).orElseThrow().sighting()).isEqualTo(1L);

    registry = new GolemRegistry(platform, settings::get, lifecycle);
    platform.join(OWNER, savedRoster);
    registry.on(new PlayerJoinEvent(owner));
    MobRef reloaded = golem(new AtomicReference<>(at(0, 64, 0)));
    chunkOnDisk.write(reloaded);
    registry.on(new ChunkEvent(world, new ChunkPos(0, 0), ChunkEvent.Phase.LOAD, false));
    platform.loadedChunks.add(WORLD + ":" + GolemRoster.chunkKey(0, 0));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(reloaded)));

    assertThat(GolemState.has(reloaded)).isFalse();
    assertThat(registry.handle(reloaded)).isEmpty();
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    assertThat(savedRoster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);

    platform.now += GolemMissingWatch.GRACE_MILLIS;
    registry.onTick();

    assertThat(savedRoster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.MISSING);
  }

  @Test
  void aGolemLoadedWhenTheServerCrashesLosesNothing() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));
    GolemState chunkOnDisk = GolemState.read(golem).orElseThrow();
    handle.addTasks(1);
    GolemRoster savedRoster = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);

    registry = new GolemRegistry(platform, settings::get, lifecycle);
    platform.join(OWNER, savedRoster);
    MobRef reloaded = golem(new AtomicReference<>(at(0, 64, 0)));
    chunkOnDisk.write(reloaded);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(reloaded)));

    assertThat(registry.handle(reloaded).orElseThrow().isValidated()).isTrue();
    assertThat(GolemState.has(reloaded)).isTrue();
    assertThat(savedRoster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(savedRoster.golem(handle.id()).orElseThrow().sighting()).isEqualTo(1L);
  }

  @Test
  void aGolemReloadedFromItsSavedChunkStaysBoundWithTheSightingItUnloadedWith() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isZero();

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)));
    assertThat(GolemState.read(golem).orElseThrow().sighting()).isEqualTo(1L);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(registry.handle(golem).orElseThrow().isValidated()).isTrue();
    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isEqualTo(1L);
    assertThat(GolemState.read(golem).orElseThrow().sighting()).isEqualTo(1L);
  }

  @Test
  void aGolemArrivingFromACrossWorldTeleportKeepsItsSighting() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));
    MobRef moved = golem(new AtomicReference<>(at(300, 70, 300)));
    GolemState.read(golem).orElseThrow().write(moved);
    GolemRegistry.PaperEntityListener listener = companion(GolemRegistry.PaperEntityListener.class);

    listener.on(new EntityWorldEvent(golem, EntityWorldEvent.Phase.REMOVE));
    listener.on(new EntityWorldEvent(moved, EntityWorldEvent.Phase.ADD));
    platform.runDelayed();

    assertThat(registry.handle(golem)).isEmpty();
    assertThat(registry.handle(moved).orElseThrow().isValidated()).isTrue();
    assertThat(GolemState.read(golem).orElseThrow().sighting()).isEqualTo(1L);
    assertThat(GolemState.read(moved).orElseThrow().sighting()).isEqualTo(1L);
    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isEqualTo(1L);
  }

  @Test
  void joiningAndWakingLeaveEverySightingAlone() {
    UUID id = UUID.randomUUID();
    MobRef golem = withSighting(persistedGolem(id, false, at(0, 64, 0)), 2L);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));
    GolemRoster roster = new GolemRoster();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.raiseSighting(id, 2L);

    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner));
    assertThat(registry.handle(golem).orElseThrow().isValidated()).isTrue();
    registry.wake(OWNER);
    registry.on(new PlayerJoinEvent(owner));

    assertThat(roster.golem(id).orElseThrow().sighting()).isEqualTo(2L);
    assertThat(GolemState.read(golem).orElseThrow().sighting()).isEqualTo(2L);
  }

  @Test
  void aNewerCopyLoadedBesideAStaleOneKeepsTheGolemWhileItsOwnerIsOffline() {
    UUID id = UUID.randomUUID();
    MobRef stale = persistedGolem(id, false, at(0, 64, 0));
    MobRef newer = withSighting(persistedGolem(id, false, at(40, 64, 40)), 1L);

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(stale)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(newer)));

    assertThat(GolemState.has(stale)).isFalse();
    assertThat(registry.handle(stale)).isEmpty();
    assertThat(GolemState.has(newer)).isTrue();
    assertThat(registry.handle(newer)).isPresent();
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));

    GolemRoster roster = new GolemRoster();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.raiseSighting(id, 1L);
    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner));

    assertThat(registry.handle(newer).orElseThrow().isValidated()).isTrue();
    assertThat(registry.loadedOf(OWNER)).extracting(GolemHandle::entity).containsExactly(newer);
  }

  @Test
  void anOutdatedCopyOnAnotherThreadRevertsOnItsOwnThread() {
    UUID id = UUID.randomUUID();
    MobRef stale = persistedGolem(id, false, at(0, 64, 0));
    MobRef newer = withSighting(persistedGolem(id, false, at(40, 64, 40)), 1L);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(stale)));
    platform.ownsEntities = false;

    registry.adopt(newer);

    assertThat(GolemState.has(stale)).isTrue();
    platform.ownsEntities = true;
    platform.runDelayed();
    assertThat(GolemState.has(stale)).isFalse();
    assertThat(GolemState.has(newer)).isTrue();
  }

  @Test
  void anOutdatedCopyIsUnvalidatedAtOnceWhileItsRevertWaitsForItsThread() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    MobRef stale = persistedGolem(id, false, at(0, 64, 0));
    MobRef newer = withSighting(persistedGolem(id, false, at(40, 64, 40)), 1L);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(stale)));
    GolemHandle outdated = registry.handle(stale).orElseThrow();
    assertThat(outdated.isValidated()).isTrue();
    platform.ownsEntities = false;

    registry.adopt(newer);

    assertThat(GolemState.has(stale)).isTrue();
    assertThat(outdated.isValidated()).isFalse();
  }

  @Test
  void shutdownAdvancesTheSightingOfValidatedGolemsBeforeThePlayerDataSave() {
    MobRef unvalidated = persistedGolem(UUID.randomUUID(), false, at(20, 64, 20));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(unvalidated)));
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    List<Long> savedBodySightings = new ArrayList<>();
    platform.rosterListener = changed -> savedBodySightings.add(GolemState.read(golem).orElseThrow().sighting());

    assertThat(registry.flushAll(1_000L)).isTrue();

    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isEqualTo(1L);
    assertThat(GolemState.read(golem).orElseThrow().sighting()).isEqualTo(1L);
    assertThat(savedBodySightings).isNotEmpty().allSatisfy(sighting -> assertThat(sighting).isEqualTo(1L));
    assertThat(GolemState.read(unvalidated).orElseThrow().sighting()).isZero();
  }

  @Test
  void aLateShutdownSaveOfARetiredGolemLeavesTheRosterUnchanged() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.ownsEntities = false;
    int changesBefore = platform.changes(OWNER);

    assertThat(registry.flushAll(1L)).isFalse();
    assertThat(handle.isRetired()).isTrue();
    platform.ownsEntities = true;
    platform.runDelayed();

    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isZero();
    assertThat(GolemState.read(golem).orElseThrow().sighting()).isZero();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
  }

  @Test
  void aShutdownSaveThatNeverReachesTheBodyLeavesTheRosterUnchanged() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    doAnswer(invocation -> {
      handle.retire();
      return null;
    }).when(golem).setAware(false);
    int changesBefore = platform.changes(OWNER);

    registry.flushAll(1_000L);

    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isZero();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
  }

  @Test
  void aShutdownSaveOfAGolemWhoseSchedulerRetiredItCompletesWithoutWaitingOutTheTimeout() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.ownsEntities = false;
    platform.refuseRetiring = true;

    assertThat(registry.flushAll(5_000L)).isTrue();
  }

  @Test
  void anUnloadWhoseBodyWriteFailsLeavesTheRosterUnchanged() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    DataHolder data = spy(golem.data());
    when(golem.data()).thenReturn(data);
    doThrow(new IllegalStateException("chunk storage refused")).when(data).set(eq(GolemState.KEY), any());
    int changesBefore = platform.changes(OWNER);

    assertThatThrownBy(() -> registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)))).hasMessage("chunk storage refused");

    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isZero();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
  }

  @Test
  void shutdownFlushesEveryLoadedGolem() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.addTasks(5);

    assertThat(registry.flushAll(1_000L)).isTrue();

    GolemState stored = GolemState.read(golem).orElseThrow();
    assertThat(stored.stats().tasks()).isEqualTo(5L);
    assertThat(stored.dormant()).isTrue();
    verify(golem).setAware(false);
    verify(golem).setInvulnerable(true);
    assertThat(registry.handle(golem)).isEmpty();
  }

  @Test
  void releaseIsRefusedUntilTheGolemIsValidated() {
    UUID id = UUID.randomUUID();
    MobRef golem = persistedGolem(id, false, at(0, 64, 0));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));
    GolemHandle handle = registry.handle(golem).orElseThrow();

    assertThat(handle.isValidated()).isFalse();
    assertThat(registry.release(handle)).isFalse();

    platform.online.add(OWNER);
    registry.on(new PlayerJoinEvent(owner));
    assertThat(handle.isValidated()).isFalse();
    assertThat(registry.release(handle)).isFalse();

    assertThat(GolemState.has(golem)).isTrue();
    assertThat(registry.handle(golem)).contains(handle);
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void releaseIsRefusedWhenTheRosterEntryIsNotActive() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    roster.transition(handle.id(), GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);

    assertThat(registry.release(handle)).isFalse();

    assertThat(GolemState.has(golem)).isTrue();
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void quitClearsValidationAndJoinRestoresIt() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    assertThat(handle.isValidated()).isTrue();

    registry.on(new PlayerQuitEvent(owner));
    assertThat(handle.isValidated()).isFalse();
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(registry.release(handle)).isFalse();
    assertThat(GolemState.has(golem)).isTrue();
    platform.quit(OWNER);

    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner));
    assertThat(handle.isValidated()).isTrue();
  }

  @Test
  void unvalidatedGolemsDieWithVanillaLootOnlyAndLeaveTheEntryToGoMissing() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 1D, 1D));
    GolemRoster roster = new GolemRoster();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(0, 0), 0L));
    MobRef golem = persistedGolem(id, false, at(0, 64, 0));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));
    List<ItemRef> drops = new ArrayList<>();
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.entity()).thenReturn(golem);
    when(death.drops()).thenReturn(drops);

    registry.on(death);

    assertThat(drops).isEmpty();
    assertThat(registry.handle(golem)).isEmpty();
    platform.join(OWNER, roster);
    platform.loadedChunks.add(WORLD + ":" + GolemRoster.chunkKey(0, 0));
    registry.on(new PlayerJoinEvent(owner));
    platform.now += GolemMissingWatch.GRACE_MILLIS;
    registry.onTick();
    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.MISSING);
  }

  @Test
  void entitiesAddedToAWorldAreAdoptedOneTickLaterAndOnlyOnce() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    MobRef golem = persistedGolem(id, false, at(0, 64, 0));
    GolemRegistry.PaperEntityListener listener = companion(GolemRegistry.PaperEntityListener.class);

    listener.on(new EntityWorldEvent(golem, EntityWorldEvent.Phase.ADD));
    listener.on(new EntityWorldEvent(golem, EntityWorldEvent.Phase.ADD));

    assertThat(registry.handle(golem)).isEmpty();
    assertThat(platform.delayed).hasSize(2).allSatisfy(task -> assertThat(task.delayTicks()).isEqualTo(1));

    platform.runDelayed();
    GolemHandle handle = registry.handle(golem).orElseThrow();
    assertThat(handle.isValidated()).isTrue();

    listener.on(new EntityWorldEvent(golem, EntityWorldEvent.Phase.ADD));
    registry.adopt(golem);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(platform.delayed).isEmpty();
    assertThat(registry.handle(golem)).containsSame(handle);
    assertThat(registry.loadedOf(OWNER)).containsExactly(handle);
    assertThat(registry.activeCount()).isEqualTo(1);
  }

  @Test
  void companionsGuardBoundGolemsAndBlockCopperTransport() {
    assertThat(registry.createCompanionListeners())
        .hasAtLeastOneElementOfType(GolemGuardListener.class)
        .hasAtLeastOneElementOfType(CopperBrainGuard.class)
        .hasAtLeastOneElementOfType(GolemRegistry.PaperEntityListener.class);
  }

  @Test
  void serverCapWakesQueuedGolemsInArrivalOrder() {
    settings.set(withCaps(32, 8, 1));
    GolemRoster roster = onlineOwner();
    MobRef first = activeGolem(roster, at(0, 64, 0));
    MobRef second = activeGolem(roster, at(1, 64, 0));
    MobRef third = activeGolem(roster, at(2, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(first)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(second)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(third)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(first)));
    registry.onTick();

    assertThat(registry.handle(second).orElseThrow().isDormant()).isFalse();
    assertThat(registry.handle(third).orElseThrow().isDormant()).isTrue();
  }

  @Test
  void newcomersCannotJumpTheServerCapQueue() {
    settings.set(withCaps(32, 8, 1));
    GolemRoster roster = onlineOwner();
    MobRef first = activeGolem(roster, at(0, 64, 0));
    MobRef queued = activeGolem(roster, at(1, 64, 0));
    MobRef newcomer = activeGolem(roster, at(2, 64, 0));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(first, queued)));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(first)));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(newcomer)));

    assertThat(registry.handle(newcomer).orElseThrow().isDormant()).isTrue();
    registry.onTick();
    assertThat(registry.handle(queued).orElseThrow().isDormant()).isFalse();
    assertThat(registry.handle(newcomer).orElseThrow().isDormant()).isTrue();
  }

  @Test
  void loweringTheServerCapSleepsTheNewestAwakeGolems() {
    GolemRoster roster = onlineOwner();
    MobRef oldest = activeGolem(roster, at(0, 64, 0));
    MobRef middle = activeGolem(roster, at(1, 64, 0));
    MobRef newest = activeGolem(roster, at(2, 64, 0));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(oldest, middle, newest)));
    assertThat(registry.activeCount()).isEqualTo(3);

    settings.set(withCaps(32, 8, 1));
    registry.enforceCap();

    assertThat(registry.activeCount()).isEqualTo(1);
    assertThat(registry.handle(oldest).orElseThrow().isDormant()).isFalse();
    assertThat(registry.handle(middle).orElseThrow().isDormant()).isTrue();
    assertThat(registry.handle(newest).orElseThrow().isDormant()).isTrue();
    verify(newest).setAware(false);

    settings.set(GolemSettings.defaults());
    registry.onTick();
    assertThat(registry.activeCount()).isEqualTo(3);
  }

  @Test
  void bindAndReleaseRequireTheGolemsOwningThread() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    platform.ownsEntities = false;
    assertThatThrownBy(() -> registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).isInstanceOf(IllegalStateException.class);
    assertThat(GolemState.has(golem)).isFalse();

    platform.ownsEntities = true;
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.ownsEntities = false;
    assertThatThrownBy(() -> registry.release(handle)).isInstanceOf(IllegalStateException.class);
    assertThat(GolemState.has(golem)).isTrue();
  }

  @Test
  void quitFromAnotherThreadWritesTheCachedPositionImmediately() {
    GolemRoster roster = onlineOwner();
    AtomicReference<Loc> location = new AtomicReference<>(at(0, 64, 0));
    MobRef golem = golem(location);
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    location.set(at(40, 64, 40));
    handle.setMode(GolemMode.STAY);
    location.set(at(200, 64, 200));
    platform.ownsEntities = false;
    platform.now += 10_000L;

    registry.on(new PlayerQuitEvent(owner));

    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.chunkKey()).isEqualTo(GolemRoster.chunkKey(2, 2));
    assertThat(entry.lastSeen()).isEqualTo(platform.now);
    assertThat(handle.isValidated()).isFalse();
    assertThat(handle.isDormant()).isFalse();

    platform.quit(OWNER);
    platform.runDelayed();
    assertThat(handle.isDormant()).isTrue();
  }

  @Test
  void startupSweepsAdoptLoadedGolemsOfOnlineOwners() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    long chunk = GolemRoster.chunkKey(1, 1);
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, chunk, 0L));
    MobRef golem = persistedGolem(id, true, at(20, 64, 20));
    platform.loadChunk(WORLD, chunk, golem);

    registry.joinOnlineOwners();

    GolemHandle handle = registry.handle(golem).orElseThrow();
    assertThat(handle.isValidated()).isTrue();
    assertThat(handle.isDormant()).isFalse();
    assertThat(platform.sweeps).containsExactly(WORLD + ":" + chunk);
    platform.now += GolemMissingWatch.GRACE_MILLIS;
    registry.onTick();
    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void anUnboundGolemLoadedWithARitualMarkerGetsItsAwarenessAndSizeBack() {
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    golem.data().set(GolemRitualMarker.KEY, (byte) 1);
    platform.scale(golem).add(new AttributeMod(GolemScale.RITUAL_KEY, -0.3D, AttributeOperation.ADD_VALUE));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    verify(golem).setAware(true);
    assertThat(platform.scale(golem)).isEmpty();
    assertThat(GolemRitualMarker.has(golem)).isFalse();
    assertThat(registry.handle(golem)).isEmpty();
  }

  @Test
  void joinSweepsRecoverRitualMarkedGolemsInTheSweptChunks() {
    GolemRoster roster = onlineOwner();
    long chunk = GolemRoster.chunkKey(1, 1);
    roster.add(new GolemRoster.Binding(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, WORLD, chunk, 0L));
    MobRef stranded = golem(new AtomicReference<>(at(20, 64, 20)));
    stranded.data().set(GolemRitualMarker.KEY, (byte) 0);
    platform.scale(stranded).add(new AttributeMod(GolemScale.RITUAL_KEY, -0.3D, AttributeOperation.ADD_VALUE));
    platform.loadChunk(WORLD, chunk, stranded);

    registry.joinOnlineOwners();

    verify(stranded).setAware(false);
    assertThat(platform.scale(stranded)).isEmpty();
    assertThat(GolemRitualMarker.has(stranded)).isFalse();
  }

  @Test
  void handlesOfSilentlyRemovedEntitiesArePrunedAfterTwoSightings() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    when(golem.isValid()).thenReturn(false);

    registry.onTick();
    assertThat(registry.handle(golem)).isPresent();

    platform.now += 30_000L;
    registry.onTick();

    assertThat(registry.handle(golem)).isEmpty();
    assertThat(registry.activeCount()).isZero();
  }

  @Test
  void aGolemSeenInvalidOnceThenValidAgainIsNeverPruned() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    when(golem.isValid()).thenReturn(false);
    registry.onTick();
    when(golem.isValid()).thenReturn(true);
    platform.now += 30_000L;
    registry.onTick();
    when(golem.isValid()).thenReturn(false);
    platform.now += 30_000L;

    registry.onTick();

    assertThat(registry.handle(golem)).isPresent();
  }

  @Test
  void worldSaveFlushesOnlyThatWorldsGolems() {
    GolemRoster roster = onlineOwner();
    WorldRef nether = mock(WorldRef.class);
    UUID netherId = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    lenient().when(nether.uid()).thenReturn(netherId);
    MobRef here = activeGolem(roster, at(0, 64, 0));
    MobRef there = activeGolem(roster, new Loc(nether, 0.5D, 64D, 0.5D));
    lenient().when(there.world()).thenReturn(nether);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(here, there)));
    registry.handle(here).orElseThrow().addKills(1);
    registry.handle(there).orElseThrow().addKills(1);

    registry.on(new WorldLifecycleEvent(world, WorldLifecycleEvent.Phase.SAVE));

    assertThat(GolemState.read(here).orElseThrow().stats().kills()).isEqualTo(1L);
    assertThat(GolemState.read(there).orElseThrow().stats().kills()).isZero();
  }

  @Test
  void bindWakesTheGolemBrain() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));

    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    assertThat(lifecycle.events).containsExactly(new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.AWAKE, handle));
  }

  @Test
  void dormancySleepsTheGolemBrainAndWakingStartsItAgain() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    lifecycle.events.clear();

    registry.on(new PlayerQuitEvent(owner));
    platform.quit(OWNER);
    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner));

    assertThat(lifecycle.events).containsExactly(
        new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.ASLEEP, handle),
        new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.AWAKE, handle));
  }

  @Test
  void golemLoadingWhileItsOwnerIsOfflineNeverWakesABrain() {
    MobRef golem = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(lifecycle.kinds()).containsExactly(RecordingGolemLifecycle.Kind.ASLEEP);
  }

  @Test
  void unloadDetachesTheGolemBrain() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    lifecycle.events.clear();

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(golem)));

    assertThat(lifecycle.events).containsExactly(new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.DETACHED, handle));
  }

  @Test
  void releaseDetachesTheGolemBrainWhileTheGolemIsStillBound() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    lifecycle.events.clear();

    assertThat(registry.release(handle)).isTrue();

    assertThat(lifecycle.events).containsExactly(new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.DETACHED, handle));
    assertThat(lifecycle.boundOnDetach).containsExactly(true);
  }

  @Test
  void deathDetachesTheGolemBrain() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    lifecycle.events.clear();
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.entity()).thenReturn(golem);
    when(death.drops()).thenReturn(new ArrayList<>());

    registry.on(death);

    assertThat(lifecycle.events).containsExactly(new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.DETACHED, handle));
  }

  @Test
  void shutdownSleepsAndDetachesEveryGolemBrain() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    lifecycle.events.clear();

    registry.flushAll(1_000L);

    assertThat(lifecycle.events).containsExactly(
        new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.ASLEEP, handle),
        new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.DETACHED, handle));
  }

  @Test
  void antiDupeRevertDetachesWithoutWaking() {
    GolemRoster roster = onlineOwner();
    MobRef unknown = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(unknown)));

    assertThat(lifecycle.kinds()).containsExactly(RecordingGolemLifecycle.Kind.DETACHED);
    assertThat(roster.golems()).isEmpty();
  }

  @Test
  void aShatteringDeathDropsCargoAndEssenceAndLeavesAShatteredCoreInsteadOfRollingTheLoadout() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 1D, 1D));
    GolemRoster roster = onlineOwner();
    Loc at = at(4, 64, 4);
    MobRef golem = golem(new AtomicReference<>(at));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 2)));
    handle.setEssence(30D);
    handle.setChannels(0x0003);
    handle.state().setCarried(List.of("cargo"));
    handle.addKills(4L);
    AtomicReference<PackedGolem> packed = new AtomicReference<>();
    ItemRef shatteredCore = mock(ItemRef.class);
    items.when(() -> GolemItems.shatteredCore(any(PackedGolem.class))).thenAnswer(invocation -> {
      packed.set(invocation.getArgument(0));
      return shatteredCore;
    });
    RecordingMender mender = new RecordingMender();
    mender.shatters = true;
    GolemMend.register(mender);
    try {
      List<ItemRef> drops = new ArrayList<>();
      EntityDeathEvent death = death(golem, drops, 5);

      registry.on(death);

      assertThat(drops).doesNotContain(coreItem, runeItem);
      verify(death).setDroppedExp(35);
      assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
      assertThat(roster.golemCount()).isEqualTo(1);
      assertThat(platform.ownerBoundDrops).singleElement().satisfies(drop -> {
        assertThat(drop.item()).isSameAs(shatteredCore);
        assertThat(drop.owner()).isEqualTo(OWNER);
        assertThat(drop.location()).isEqualTo(at);
      });
      assertThat(packed.get().golemId()).isEqualTo(handle.id());
      assertThat(packed.get().owner()).isEqualTo(OWNER);
      assertThat(roster.golem(handle.id()).orElseThrow().digest()).isEqualTo(GolemRoster.digest(packed.get().state()));
      assertThat(roster.golem(handle.id()).orElseThrow().holds(packed.get().state())).isTrue();
      GolemSnapshot snapshot = GolemSnapshot.fromJson(packed.get().state()).orElseThrow();
      assertThat(snapshot.id()).isEqualTo(handle.id());
      assertThat(snapshot.body()).isEqualTo(GolemBody.IRON);
      assertThat(snapshot.cores()).containsExactly(GolemCoreType.GUARDIAN);
      assertThat(snapshot.runes()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 2));
      assertThat(snapshot.channels()).isEqualTo(0x0003);
      assertThat(snapshot.stats().kills()).isEqualTo(4L);
      assertThat(snapshot.carried()).isEmpty();
      assertThat(snapshot.essence()).isZero();
      assertThat(snapshot.takenAt()).isEqualTo(platform.now);
      assertThat(mender.shattered).containsExactly(handle);
      assertThat(mender.shatteredAt).containsExactly(at);
      assertThat(registry.handle(golem)).isEmpty();
    } finally {
      GolemMend.unregister(mender);
    }
  }

  @Test
  void theShatteredEntryIsSavedBeforeTheCoreDrops() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    items.when(() -> GolemItems.shatteredCore(any(PackedGolem.class))).thenReturn(mock(ItemRef.class));
    int savesBefore = platform.changes(OWNER);
    List<String> order = new ArrayList<>();
    platform.rosterListener = changed -> order.add("save:" + roster.golem(handle.id()).orElseThrow().status());
    platform.dropListener = () -> order.add("drop:" + roster.golem(handle.id()).orElseThrow().status());
    RecordingMender mender = new RecordingMender();
    mender.shatters = true;
    GolemMend.register(mender);
    try {
      registry.on(death(golem, new ArrayList<>(), 0));
    } finally {
      GolemMend.unregister(mender);
    }

    assertThat(order).containsExactly("save:SHATTERED", "drop:SHATTERED");
    assertThat(platform.changes(OWNER)).isEqualTo(savesBefore + 1);
  }

  @Test
  void deathsTheMenderDeclinesRollTheLoadoutAndForgetTheEntry() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 1D, 0D));
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    RecordingMender mender = new RecordingMender();
    GolemMend.register(mender);
    try {
      List<ItemRef> drops = new ArrayList<>();
      registry.on(death(golem, drops, 0));

      assertThat(mender.asked).containsExactly(handle);
      assertThat(drops).containsExactly(coreItem);
      assertThat(roster.golem(handle.id())).isEmpty();
      assertThat(platform.ownerBoundDrops).isEmpty();
      assertThat(mender.shattered).isEmpty();
    } finally {
      GolemMend.unregister(mender);
    }
  }

  @Test
  void unvalidatedDeathsNeverShatter() {
    GolemRoster roster = new GolemRoster();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(0, 0), 0L));
    MobRef golem = persistedGolem(id, false, at(0, 64, 0));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));
    RecordingMender mender = new RecordingMender();
    mender.shatters = true;
    GolemMend.register(mender);
    try {
      registry.on(death(golem, new ArrayList<>(), 0));
    } finally {
      GolemMend.unregister(mender);
    }

    assertThat(mender.asked).isEmpty();
    assertThat(platform.ownerBoundDrops).isEmpty();
    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void aShatteredGolemThatTurnsUpRevertsWithoutDrops() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    roster.transition(id, GolemRoster.Status.ACTIVE, GolemRoster.Status.SHATTERED);
    MobRef golem = persistedGolem(id, false, at(0, 64, 0));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(golem)));

    assertThat(GolemState.has(golem)).isFalse();
    assertThat(registry.handle(golem)).isEmpty();
    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void shatteredAndPackedEntriesAreReleasableFromTheRosterButActiveOnesAreNot() {
    GolemRoster roster = onlineOwner();
    UUID shattered = UUID.randomUUID();
    UUID packed = UUID.randomUUID();
    UUID active = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(shattered, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.add(new GolemRoster.Binding(packed, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.add(new GolemRoster.Binding(active, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(shattered, GolemRoster.Status.ACTIVE, GolemRoster.Status.SHATTERED);
    roster.stow(packed, GolemRoster.Status.PACKED, "{}");
    int changesBefore = platform.changes(OWNER);

    assertThat(registry.releaseEntry(OWNER, active)).isFalse();
    assertThat(registry.releaseEntry(OWNER, packed)).isTrue();
    assertThat(registry.releaseEntry(OWNER, shattered)).isTrue();

    assertThat(roster.golem(shattered)).isEmpty();
    assertThat(roster.golem(packed)).isEmpty();
    assertThat(roster.golem(active)).isPresent();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore + 2);
  }

  @Test
  void restoreRebindsAFreshBodyUnderTheShatteredIdAfterSavingTheRoster() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    String name = GolemSnapshot.textName("Old Clank");
    GolemSnapshot snapshot = snapshot(id, GolemBody.IRON, GolemCoreType.GUARDIAN, name);
    PackedGolem item = shattered(roster, snapshot, GolemRoster.chunkKey(9, 9));
    MobRef fresh = golem(new AtomicReference<>(at(3, 64, -20)));
    List<Boolean> boundAtSave = new ArrayList<>();
    platform.rosterListener = changed -> boundAtSave.add(GolemState.has(fresh));
    int changesBefore = platform.changes(OWNER);

    Optional<GolemHandle> restored = registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED));

    assertThat(restored).isPresent();
    GolemHandle handle = restored.orElseThrow();
    assertThat(boundAtSave).first().isEqualTo(false);
    assertThat(platform.changes(OWNER)).isGreaterThan(changesBefore);
    assertThat(handle.id()).isEqualTo(id);
    assertThat(roster.golemCount()).isEqualTo(1);
    GolemRoster.GolemEntry entry = roster.golem(id).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(entry.digest()).isNull();
    assertThat(entry.chunkKey()).isEqualTo(GolemRoster.chunkKey(0, -2));
    GolemState stored = GolemState.read(fresh).orElseThrow();
    assertThat(stored.id()).isEqualTo(id);
    assertThat(stored.cores()).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(stored.runes()).containsExactly(new GolemRune(GolemRuneType.BULWARK, 3));
    assertThat(stored.stats().kills()).isEqualTo(12L);
    assertThat(stored.previouslyBound()).isTrue();
    ArgumentCaptor<String> shownName = ArgumentCaptor.forClass(String.class);
    verify(fresh).setCustomNameJson(shownName.capture());
    assertThat(shownName.getValue()).isEqualTo(name);
    assertThat(platform.scale(fresh)).singleElement().satisfies(modifier -> assertThat(modifier.key()).isEqualTo(GolemScale.MODIFIER_KEY));
    verify(fresh).setPersistent(true);
    verify(fresh).setRemoveWhenFarAway(false);
    assertThat(StackExclusion.isExcluded(fresh)).isTrue();
    assertThat(handle.isValidated()).isTrue();
    assertThat(handle.isDormant()).isFalse();
    assertThat(registry.handle(fresh)).contains(handle);
  }

  @Test
  void aRestoredGolemWithoutASavedNameTakesItsOwnersBoundName() {
    GolemRoster roster = onlineOwner();
    GolemSnapshot snapshot = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem item = shattered(roster, snapshot, 0L);
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));

    registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED));

    ArgumentCaptor<String> shownName = ArgumentCaptor.forClass(String.class);
    verify(fresh).setCustomNameJson(shownName.capture());
    assertThat(GolemSnapshot.plainName(shownName.getValue())).isEqualTo("Steve's Iron Golem");
  }

  @Test
  void restoreLeavesTheBodyAloneWhenTheEntryIsNotInTheExpectedState() {
    GolemRoster roster = onlineOwner();
    UUID active = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(active, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    GolemSnapshot activeSnapshot = snapshot(active, GolemBody.IRON, GolemCoreType.BLANK, null);
    GolemSnapshot unknown = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    int changesBefore = platform.changes(OWNER);

    assertThat(registry.restore(owner, fresh, restoreOf(activeSnapshot))).isEmpty();
    assertThat(registry.restore(owner, fresh, restoreOf(unknown))).isEmpty();

    assertThat(GolemState.has(fresh)).isFalse();
    assertThat(registry.handle(fresh)).isEmpty();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
    assertThat(roster.golem(active).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void restoreRefusesAnItemWhoseStateDoesNotMatchTheEntryDigest() {
    GolemRoster roster = onlineOwner();
    GolemSnapshot snapshot = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.GUARDIAN, null);
    PackedGolem item = shattered(roster, snapshot, 0L);
    GolemSnapshot older = GolemSnapshot.capture(GolemState.restored(snapshot, GolemAnchor.of(WORLD, 0, 64, 0), 0L),
        new GolemSnapshot.Traits(null, 3D, 0, false, true), platform.now - 1L);
    PackedGolem staleCopy = new PackedGolem(snapshot.id(), OWNER, older.toJson());
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    int changesBefore = platform.changes(OWNER);

    assertThat(registry.restore(owner, fresh, new GolemRegistry.Restore(staleCopy, GolemRoster.Status.SHATTERED))).isEmpty();
    assertThat(registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.PACKED))).isEmpty();

    assertThat(GolemState.has(fresh)).isFalse();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
    GolemRoster.GolemEntry entry = roster.golem(snapshot.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(entry.holds(item.state())).isTrue();
    assertThat(entry.holds(staleCopy.state())).isFalse();
  }

  @Test
  void restoreRefusesASnapshotWhoseBodyOrCoreDiffersFromTheEntryWithoutBreakingItsSeal() {
    GolemRoster roster = onlineOwner();
    UUID otherCoreId = UUID.randomUUID();
    UUID otherBodyId = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(otherCoreId, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    roster.add(new GolemRoster.Binding(otherBodyId, GolemBody.FROST, GolemCoreType.BLANK, WORLD, 0L, 0L));
    GolemSnapshot otherCore = snapshot(otherCoreId, GolemBody.IRON, GolemCoreType.BREAKER, null);
    GolemSnapshot otherBody = snapshot(otherBodyId, GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem otherCoreItem = new PackedGolem(otherCoreId, OWNER, otherCore.toJson());
    PackedGolem otherBodyItem = new PackedGolem(otherBodyId, OWNER, otherBody.toJson());
    roster.stow(otherCoreId, GolemRoster.Status.SHATTERED, otherCoreItem.state());
    roster.stow(otherBodyId, GolemRoster.Status.SHATTERED, otherBodyItem.state());
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    int changesBefore = platform.changes(OWNER);

    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(otherCoreItem, GolemRoster.Status.SHATTERED)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(otherBodyItem, GolemRoster.Status.SHATTERED)))
        .isInstanceOf(IllegalStateException.class);

    assertThat(GolemState.has(fresh)).isFalse();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
    assertThat(roster.golem(otherCoreId).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(roster.golem(otherCoreId).orElseThrow().holds(otherCoreItem.state())).isTrue();
    assertThat(roster.golem(otherBodyId).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(roster.golem(otherBodyId).orElseThrow().holds(otherBodyItem.state())).isTrue();
  }

  @Test
  void restoreReadsTheSnapshotFromTheItemAndShatteredCoresNeverBringCargo() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    GolemState state = GolemState.create(new GolemState.Seed(id, OWNER, GolemBody.IRON, GolemCoreType.GATHERER, GolemAnchor.of(WORLD, 5, 64, 5)));
    state.setCarried(List.of("stack-a"));
    state.setEssence(40D);
    GolemSnapshot laden = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 20D, 0, false, true), platform.now);
    PackedGolem item = shattered(roster, laden, 0L);
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));

    GolemHandle handle = registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)).orElseThrow();

    assertThat(handle.id()).isEqualTo(id);
    assertThat(handle.state().cores()).containsExactly(GolemCoreType.GATHERER);
    assertThat(handle.state().carried()).isEmpty();
    assertThat(handle.state().essence()).isZero();
  }

  @Test
  void restoreRefusesAnUnreadableItemBeforeTouchingTheRoster() {
    GolemRoster roster = onlineOwner();
    GolemSnapshot snapshot = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    shattered(roster, snapshot, 0L);
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    int changesBefore = platform.changes(OWNER);

    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(new PackedGolem(snapshot.id(), OWNER, "{broken"),
        GolemRoster.Status.SHATTERED))).isInstanceOf(IllegalStateException.class);

    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
    assertThat(roster.golem(snapshot.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(GolemState.has(fresh)).isFalse();
  }

  @Test
  void packingReleasesTheGolemSealsItsEntryAndSavesBeforeTheBodyIsRemoved() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setCarriedEncoded(List.of("stack-a", "stack-b"));
    handle.setEssence(12D);
    when(golem.health()).thenReturn(40D);
    platform.stat(golem, GolemStat.MAX_HEALTH, 100D);
    List<String> order = new ArrayList<>();
    platform.rosterListener = changed -> order.add("save " + roster.golem(handle.id()).orElseThrow().status());
    doAnswer(invocation -> order.add("remove")).when(golem).remove();
    int detachedBefore = lifecycle.kinds().size();

    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();

    assertThat(order).containsExactly("save PACKED", "remove");
    assertThat(lifecycle.kinds().subList(detachedBefore, lifecycle.kinds().size())).containsExactly(RecordingGolemLifecycle.Kind.DETACHED);
    assertThat(registry.handle(golem)).isEmpty();
    assertThat(handle.isRetired()).isTrue();
    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.PACKED);
    assertThat(entry.holds(packing.packed().state())).isTrue();
    assertThat(packing.packed().golemId()).isEqualTo(handle.id());
    assertThat(packing.packed().owner()).isEqualTo(OWNER);
    assertThat(GolemSnapshot.fromJson(packing.packed().state())).contains(packing.snapshot());
    assertThat(packing.snapshot().carried()).containsExactly("stack-a", "stack-b");
    assertThat(packing.snapshot().essence()).isEqualTo(12D);
    assertThat(packing.snapshot().health()).isEqualTo(40D);
    assertThat(packing.item()).isSameAs(dormantItem);
    assertThat(packing.at()).isEqualTo(at(3, 64, 3));
    ArgumentCaptor<DormantGolem> dormant = ArgumentCaptor.forClass(DormantGolem.class);
    items.verify(() -> GolemItems.dormantGolem(dormant.capture()));
    assertThat(dormant.getValue().packed()).isEqualTo(packing.packed());
    assertThat(dormant.getValue().block()).isEqualTo(Id.minecraft("iron_block"));
    assertThat(dormant.getValue().cores()).containsExactly(GolemCoreType.GATHERER);
    assertThat(dormant.getValue().health()).isEqualTo(40D);
    assertThat(dormant.getValue().maxHealth()).isEqualTo(100D);
    verify(golem, never()).setCustomNameJson(null);
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void onlyValidatedActiveGolemsPack() {
    GolemRoster roster = onlineOwner();
    MobRef missing = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle missingHandle = registry.bind(owner, missing, GolemBody.IRON, GolemCoreType.BLANK);
    roster.transition(missingHandle.id(), GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    UUID waitingId = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(waitingId, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    platform.quit(OWNER);
    MobRef waiting = persistedGolem(waitingId, false, at(20, 64, 20));
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(waiting)));
    GolemHandle waitingHandle = registry.handle(waiting).orElseThrow();
    platform.join(OWNER, roster);

    assertThat(registry.pack(missingHandle).join()).isEmpty();
    assertThat(registry.pack(waitingHandle).join()).isEmpty();

    verify(missing, never()).remove();
    verify(waiting, never()).remove();
    assertThat(roster.golem(missingHandle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.MISSING);
    assertThat(roster.golem(waitingId).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void aFailingItemBuildLeavesTheGolemBoundAndItsEntryActive() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    int changesBefore = platform.changes(OWNER);
    items.when(() -> GolemItems.dormantGolem(any(DormantGolem.class))).thenThrow(new IllegalStateException("model broken"));

    assertThatThrownBy(() -> registry.pack(handle).join()).hasMessage("model broken");

    verify(golem, never()).remove();
    assertThat(registry.handle(golem)).isPresent();
    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(entry.digest()).isNull();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
  }

  @Test
  void anErrorAfterTheClaimStillRestowsTheEntry() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    MobRef body = golem(new AtomicReference<>(at(9, 64, 9)));
    platform.spawns.add(body);

    assertThatThrownBy(() -> registry.awaken(owner, at(9, 64, 9), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
      throw new LinkageError("missing spawn method");
    })).isInstanceOf(LinkageError.class);

    verify(body).remove();
    assertThat(registry.handle(body)).isEmpty();
    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.PACKED);
    assertThat(entry.holds(packing.packed().state())).isTrue();
  }

  @Test
  void aThrowingRollbackKeepsTheRootCauseAndStillRestowsTheEntry() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    MobRef body = golem(new AtomicReference<>(at(9, 64, 9)));
    doThrow(new IllegalStateException("remove failed")).when(body).remove();
    platform.spawns.add(body);

    Throwable thrown = catchThrowable(() -> registry.awaken(owner, at(9, 64, 9), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED),
        mob -> {
          throw new IllegalStateException("prepare failed");
        }));

    assertThat(thrown).hasMessage("prepare failed");
    assertThat(thrown.getSuppressed()).singleElement().satisfies(suppressed -> assertThat(suppressed).hasMessage("remove failed"));
    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.PACKED);
    assertThat(entry.holds(packing.packed().state())).isTrue();
  }

  @Test
  void aFailedSaveOfTheAwakenedEntryRollsTheAwakeningBack() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    platform.spawns.add(golem(new AtomicReference<>(at(9, 64, 9))));
    AtomicBoolean failedOnce = new AtomicBoolean();
    platform.rosterListener = changed -> {
      if (roster.golem(handle.id()).orElseThrow().status() == GolemRoster.Status.ACTIVE && failedOnce.compareAndSet(false, true)) {
        throw new IllegalStateException("save refused");
      }
    };

    assertThatThrownBy(() -> registry.awaken(owner, at(9, 64, 9), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    })).hasMessage("save refused");

    assertThat(platform.spawnedAt).isEmpty();
    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.PACKED);
    assertThat(entry.holds(packing.packed().state())).isTrue();
  }

  @Test
  void packingWaitsForTheDurableSnapshotBeforeRemovingTheFrozenBody() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.checkpoint = new CompletableFuture<>();

    CompletableFuture<Optional<GolemRegistry.Packing>> packing = registry.pack(handle);

    verify(golem, never()).remove();
    verify(golem).setInvulnerable(true);
    assertThat(packing).isNotDone();
    assertThat(roster.golem(handle.id()).orElseThrow().pendingPackedState()).isPresent();
    platform.checkpoint.complete(null);
    assertThat(packing.join()).isPresent();
    verify(golem).remove();
  }

  @Test
  void aRefusedPackingCallbackKeepsTheBodyTrackedUntilRemovalBeforeDelivery() {
    GolemRoster roster = onlineOwner();
    platform.players.put(OWNER, owner);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    when(owner.inventory()).thenReturn(inventory);
    when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of());
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.checkpoint = new CompletableFuture<>();
    CompletableFuture<Optional<GolemRegistry.Packing>> packing = registry.pack(handle);
    platform.refusing.add(golem);
    platform.checkpoint.complete(null);

    registry.onTick();

    assertThat(packing.join()).isEmpty();
    verify(golem, never()).remove();
    verify(inventory, never()).addItem(any(ItemRef.class));
    assertThat(registry.hasTickDemand()).isTrue();
    platform.refusing.remove(golem);
    registry.onTick();

    InOrder order = inOrder(golem, inventory);
    order.verify(golem).remove();
    order.verify(inventory).addItem(dormantItem);
    assertThat(roster.golem(handle.id()).orElseThrow().pendingPackedState()).isEmpty();
  }

  @Test
  void aStalePackingCallbackCannotRemoveABodyWhoseTransferMarkerChanged() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.checkpoint = new CompletableFuture<>();
    CompletableFuture<Optional<GolemRegistry.Packing>> packing = registry.pack(handle);
    golem.data().set(DataKey.of(Id.of("adapt", "golem_packing_token"), DataCodecs.STRING), "another transfer");

    platform.checkpoint.complete(null);

    assertThat(packing.join()).isEmpty();
    verify(golem, never()).remove();
  }

  @Test
  void aRefusedRollbackRestoresTheCurrentCargoAndClearsThePersistentFreezeOnRetry() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.setCarriedEncoded(List.of("current cargo"));
    when(golem.isAware()).thenReturn(true);
    when(golem.canPickupItems()).thenReturn(true);
    platform.checkpoint = new CompletableFuture<>();
    registry.pack(handle);
    platform.refusing.add(golem);
    platform.checkpoint.completeExceptionally(new IllegalStateException("disk full"));
    platform.refusing.remove(golem);

    registry.onTick();

    assertThat(registry.handle(golem).orElseThrow().state().carried()).containsExactly("current cargo");
    assertThat(golem.data().hasAny(Id.of("adapt", "golem_packing"))).isFalse();
    verify(golem).setCanPickupItems(true);
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void restartRemovesAMarkedPackedBodyAndRecoversItsSnapshotOnce() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.checkpoint = new CompletableFuture<>();
    registry.pack(handle);
    GolemRoster recovered = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);
    platform.rosters.put(OWNER, recovered);
    registry = new GolemRegistry(platform, settings::get, lifecycle);

    registry.adopt(golem);

    verify(golem).remove();
    assertThat(registry.handle(golem)).isEmpty();
    assertThat(recovered.golem(handle.id()).orElseThrow().pendingPackedState()).isPresent();
  }

  @Test
  void restartThawsAMarkedBodyWhenPackingNeverReachedDurableStorage() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    when(golem.canPickupItems()).thenReturn(true);
    GolemRoster beforePacking = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);
    platform.checkpoint = new CompletableFuture<>();
    registry.pack(handle);
    platform.rosters.put(OWNER, beforePacking);
    registry = new GolemRegistry(platform, settings::get, lifecycle);

    registry.adopt(golem);

    verify(golem, never()).remove();
    verify(golem).setCanPickupItems(true);
    assertThat(registry.handle(golem)).isPresent();
    assertThat(golem.data().hasAny(Id.of("adapt", "golem_packing"))).isFalse();
  }

  @Test
  void aFailedDurableCheckpointRestoresTheActiveBody() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.checkpoint = new CompletableFuture<>();
    CompletableFuture<Optional<GolemRegistry.Packing>> packing = registry.pack(handle);

    platform.checkpoint.completeExceptionally(new IllegalStateException("disk full"));

    assertThat(packing).isCompletedExceptionally();
    verify(golem, never()).remove();
    assertThat(registry.handle(golem)).isPresent();
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(roster.golem(handle.id()).orElseThrow().pendingPackedState()).isEmpty();
  }

  @Test
  void aFailedCheckpointWithARejectedBodyCallbackCannotMakeAnUnpersistedItemDeliverable() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.checkpoint = new CompletableFuture<>();
    CompletableFuture<Optional<GolemRegistry.Packing>> packing = registry.pack(handle);
    platform.refusing.add(golem);

    platform.checkpoint.completeExceptionally(new IllegalStateException("disk full"));

    assertThat(packing).isCompletedExceptionally();
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(roster.golem(handle.id()).orElseThrow().pendingPackedState()).isEmpty();
    verify(golem, never()).remove();
  }

  @Test
  void refusedPackedDeliveryRecoversAfterRosterSerializationAndCanOnlyAwakenOnce() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    platform.refusing.add(owner);

    registry.deliverPacked(owner, packing.packed(), packing.item());

    verify(owner, never()).saveData();
    String json = Json.toJson(roster, false);
    GolemRoster recovered = Json.fromJson(json, GolemRoster.class);
    assertThat(recovered.golem(handle.id()).orElseThrow().pendingPackedState()).contains(packing.packed().state());
    platform.rosters.put(OWNER, recovered);
    platform.players.put(OWNER, owner);
    platform.refusing.remove(owner);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    when(owner.inventory()).thenReturn(inventory);
    when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of());
    registry = new GolemRegistry(platform, settings::get, lifecycle);
    registry.on(new PlayerJoinEvent(owner));

    verify(inventory).addItem(dormantItem);
    verify(owner).saveData();
    assertThat(recovered.golem(handle.id()).orElseThrow().pendingPackedState()).isEmpty();
    assertThat(recovered.unstow(handle.id(), GolemRoster.Status.PACKED, packing.packed().state())).isTrue();
    assertThat(recovered.unstow(handle.id(), GolemRoster.Status.PACKED, packing.packed().state())).isFalse();
  }

  @Test
  void aNewPackGenerationCannotBeAwakenedWithThePriorPackedItem() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing first = registry.pack(handle).join().orElseThrow();
    assertThat(roster.unstow(handle.id(), GolemRoster.Status.PACKED, first.packed().state())).isTrue();
    assertThat(roster.stow(handle.id(), GolemRoster.Status.PACKED, first.snapshot().toPackedJson(UUID.randomUUID()))).isTrue();
    assertThat(roster.unstow(handle.id(), GolemRoster.Status.PACKED, first.packed().state())).isFalse();
  }

  @Test
  void aFailedSaveOfThePackedEntryLeavesTheGolemBoundAndActive() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    AtomicBoolean failedOnce = new AtomicBoolean();
    platform.rosterListener = changed -> {
      if (roster.golem(handle.id()).orElseThrow().status() == GolemRoster.Status.PACKED && failedOnce.compareAndSet(false, true)) {
        throw new IllegalStateException("save refused");
      }
    };

    assertThatThrownBy(() -> registry.pack(handle).join()).hasMessage("save refused");

    verify(golem, never()).remove();
    assertThat(registry.handle(golem)).isPresent();
    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(entry.digest()).isNull();
  }

  @Test
  void anErrorDuringARestoreStillRestowsTheEntry() {
    GolemRoster roster = onlineOwner();
    GolemSnapshot snapshot = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem item = shattered(roster, snapshot, 0L);
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    doThrow(new LinkageError("missing persistence method")).when(fresh).setPersistent(true);

    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)))
        .isInstanceOf(LinkageError.class);

    assertThat(GolemState.has(fresh)).isFalse();
    assertThat(roster.golem(snapshot.id()).orElseThrow().holds(item.state())).isTrue();
  }

  @Test
  void aBodyRemovalFailureWithAValidBodyRollsThePackBack() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    doThrow(new IllegalStateException("removal refused")).when(golem).remove();

    assertThatThrownBy(() -> registry.pack(handle).join()).hasRootCauseMessage("removal refused");

    assertThat(registry.handle(golem)).isPresent();
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void aSnapshotFailureLeavesTheGolemBoundAndActive() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    when(golem.isPlayerCreated()).thenThrow(new IllegalStateException("traits unreadable"));

    assertThatThrownBy(() -> registry.pack(handle).join()).hasMessage("traits unreadable");

    verify(golem, never()).remove();
    assertThat(registry.handle(golem)).isPresent();
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void packingNeedsTheGolemsThread() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    platform.ownsEntities = false;

    assertThatThrownBy(() -> registry.pack(handle).join()).isInstanceOf(IllegalStateException.class);
    verify(golem, never()).remove();
  }

  @Test
  void aPackedGolemAwakensUnderItsOldIdWithItsCargoEssenceAndRecord() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setCarriedEncoded(List.of("stack-a"));
    handle.setEssence(12D);
    handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 2)));
    handle.addKills(7L);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    Loc spot = at(30, 70, 30);
    MobRef body = golem(new AtomicReference<>(spot));
    platform.spawns.add(body);
    List<String> order = new ArrayList<>();
    platform.rosterListener = changed -> order.add("save " + roster.golem(handle.id()).orElseThrow().status());
    platform.spawnListener = spawned -> order.add("spawn " + spawned.uuid().equals(body.uuid()));

    GolemHandle awakened = registry.awaken(owner, spot, new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED),
        mob -> order.add("prepare " + GolemState.has(mob))).orElseThrow();

    assertThat(order).startsWith("save ACTIVE", "spawn true", "prepare true");
    assertThat(platform.spawnedAt).containsExactly(spot);
    assertThat(platform.spawnedTypes).containsExactly(GolemBody.IRON.entityType());
    assertThat(awakened.id()).isEqualTo(handle.id());
    assertThat(awakened.entity()).isSameAs(body);
    assertThat(awakened.isValidated()).isTrue();
    assertThat(awakened.state().carried()).containsExactly("stack-a");
    assertThat(awakened.state().essence()).isEqualTo(12D);
    assertThat(awakened.state().runes()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 2));
    assertThat(awakened.state().stats().kills()).isEqualTo(7L);
    assertThat(awakened.state().home()).isEqualTo(GolemAnchor.of(WORLD, 3, 64, 3));
    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(entry.digest()).isNull();
    assertThat(registry.handle(body)).contains(awakened);
    verify(body).setPersistent(true);
    assertThat(platform.scale(body)).singleElement().satisfies(modifier -> assertThat(modifier.key()).isEqualTo(GolemScale.MODIFIER_KEY));
  }

  @Test
  void anAwakenedGolemStartsFromItsEntrysSightingAndOutdatesItsPrePackCopies() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    GolemState prePackChunk = GolemState.read(golem).orElseThrow();
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    Loc spot = at(30, 70, 30);
    MobRef body = golem(new AtomicReference<>(spot));
    platform.spawns.add(body);

    GolemHandle awakened = registry.awaken(owner, spot, new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    }).orElseThrow();

    assertThat(awakened.isValidated()).isTrue();
    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isEqualTo(1L);
    assertThat(GolemState.read(body).orElseThrow().sighting()).isEqualTo(1L);

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(body)));
    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isEqualTo(2L);
    MobRef prePackCopy = golem(new AtomicReference<>(at(3, 64, 3)));
    prePackChunk.write(prePackCopy);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(prePackCopy)));

    assertThat(GolemState.has(prePackCopy)).isFalse();
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
  }

  @Test
  void aPrePackCopyRevertsEvenWhenTheAwakenedBodyUnloadedWhileItsOwnerWasOffline() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    GolemState prePackChunk = GolemState.read(golem).orElseThrow();
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    Loc spot = at(30, 70, 30);
    MobRef body = golem(new AtomicReference<>(spot));
    platform.spawns.add(body);
    registry.awaken(owner, spot, new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    }).orElseThrow();
    registry.on(new PlayerQuitEvent(owner));
    platform.quit(OWNER);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.UNLOAD, List.of(body)));
    chunkEntities.values().forEach(entities -> entities.remove(body));
    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner));
    MobRef prePackCopy = golem(new AtomicReference<>(at(3, 64, 3)));
    prePackChunk.write(prePackCopy);

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(prePackCopy)));

    assertThat(GolemState.has(prePackCopy)).isFalse();
    assertThat(roster.golem(handle.id()).orElseThrow().sighting()).isEqualTo(1L);
    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(body)));
    assertThat(registry.handle(body)).isPresent();
    assertThat(GolemState.has(body)).isTrue();
  }

  @Test
  void aRebuiltGolemStartsFromItsEntrysSightingSoItIsNotMistakenForAStaleCopy() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    GolemSnapshot snapshot = snapshot(id, GolemBody.IRON, GolemCoreType.GUARDIAN, null);
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    roster.raiseSighting(id, 5L);
    PackedGolem item = new PackedGolem(id, OWNER, snapshot.toJson());
    roster.stow(id, GolemRoster.Status.SHATTERED, item.state());
    MobRef fresh = golem(new AtomicReference<>(at(3, 64, -20)));

    GolemHandle restored = registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)).orElseThrow();

    assertThat(restored.isValidated()).isTrue();
    assertThat(GolemState.has(fresh)).isTrue();
    assertThat(roster.golem(id).orElseThrow().sighting()).isEqualTo(6L);
    assertThat(GolemState.read(fresh).orElseThrow().sighting()).isEqualTo(6L);
  }

  @Test
  void awakeningAReleasedOrStaleDormantGolemChangesNothing() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    PackedGolem stale = new PackedGolem(handle.id(), OWNER, packing.snapshot().withoutCargo().toJson().replace("\"takenAt\"", "\"takenAt\" "));
    int changesBefore = platform.changes(OWNER);

    assertThat(registry.awaken(owner, at(9, 64, 9), new GolemRegistry.Restore(stale, GolemRoster.Status.PACKED), mob -> {
    })).isEmpty();
    roster.remove(handle.id());
    assertThat(registry.awaken(owner, at(9, 64, 9), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    })).isEmpty();

    assertThat(platform.spawnedAt).isEmpty();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
  }

  @Test
  void aRefusedSpawnRemovesTheBodyAndRestoresTheSealedEntry() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    MobRef body = golem(new AtomicReference<>(at(9, 64, 9)));
    when(body.isValid()).thenReturn(false);
    platform.spawns.add(body);

    assertThatThrownBy(() -> registry.awaken(owner, at(9, 64, 9), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    })).isInstanceOf(IllegalStateException.class);

    verify(body).remove();
    assertThat(registry.handle(body)).isEmpty();
    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.PACKED);
    assertThat(entry.holds(packing.packed().state())).isTrue();
  }

  @Test
  void aFailingPrepareRollsTheAwakeningBack() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    MobRef body = golem(new AtomicReference<>(at(9, 64, 9)));
    platform.spawns.add(body);

    assertThatThrownBy(() -> registry.awaken(owner, at(9, 64, 9), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
      throw new IllegalStateException("copper refused");
    })).hasMessage("copper refused");

    verify(body).remove();
    assertThat(roster.golem(handle.id()).orElseThrow().holds(packing.packed().state())).isTrue();
  }

  @Test
  void awakenBlockersCoverCoresOwnerDataAndBothCaps() {
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemSnapshot iron = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    Loc spot = at(0, 64, 0);
    platform.online.add(OWNER);
    assertThat(registry.awakenBlock(spot, iron)).contains(GolemRegistry.BindBlock.OWNER_DATA_UNAVAILABLE);

    onlineOwner();
    assertThat(registry.awakenBlock(spot, iron)).isEmpty();
    assertThat(registry.awakenBlock(spot, snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.SORTER, null)))
        .contains(GolemRegistry.BindBlock.CORE_NOT_ALLOWED);

    settings.set(withCaps(32, 1, 1500));
    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    assertThat(registry.awakenBlock(spot, iron)).contains(GolemRegistry.BindBlock.CHUNK_CAP);
    assertThat(registry.awakenBlock(at(40, 64, 40), iron)).isEmpty();

    settings.set(withCaps(32, 8, 1));
    assertThat(registry.awakenBlock(at(40, 64, 40), iron)).contains(GolemRegistry.BindBlock.SERVER_CAP);
  }

  @Test
  void awakeningNeedsTheSpotsThreadAndRefusesABlockedSpot() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    GolemRegistry.Restore request = new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED);
    platform.ownsLocations = false;
    assertThatThrownBy(() -> registry.awaken(owner, at(9, 64, 9), request, mob -> {
    })).isInstanceOf(IllegalStateException.class);
    platform.ownsLocations = true;
    settings.set(withCaps(32, 8, 0));
    assertThatThrownBy(() -> registry.awaken(owner, at(9, 64, 9), request, mob -> {
    })).isInstanceOf(IllegalStateException.class);

    assertThat(platform.spawnedAt).isEmpty();
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.PACKED);
  }

  @Test
  void aFailedRestoreRollsTheRosterBackToTheSealedEntry() {
    GolemRoster roster = onlineOwner();
    GolemSnapshot snapshot = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem item = shattered(roster, snapshot, 0L);
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    doThrow(new IllegalStateException("persistence refused")).when(fresh).setPersistent(true);
    int changesBefore = platform.changes(OWNER);

    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)))
        .hasMessage("persistence refused");

    assertThat(GolemState.has(fresh)).isFalse();
    assertThat(registry.handle(fresh)).isEmpty();
    GolemRoster.GolemEntry entry = roster.golem(snapshot.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(entry.holds(item.state())).isTrue();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore + 2);
  }

  @Test
  void anAwakeningRollbackRemovesTheBodyEvenWhenUnindexingItFails() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    MobRef body = golem(new AtomicReference<>(at(9, 64, 9)));
    platform.spawns.add(body);
    lifecycle.awakeFailure = new IllegalStateException("brain refused");
    lifecycle.detachFailure = new IllegalStateException("detach refused");

    assertThatThrownBy(() -> registry.awaken(owner, at(9, 64, 9), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    })).hasMessage("brain refused");

    verify(body).remove();
    assertThat(roster.golem(handle.id()).orElseThrow().holds(packing.packed().state())).isTrue();
  }

  @Test
  void aRestoreThatFailsAfterAdoptionRevertsTheBodyAndRestoresTheEntry() {
    GolemRoster roster = onlineOwner();
    GolemSnapshot snapshot = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem item = shattered(roster, snapshot, 0L);
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    lifecycle.awakeFailure = new IllegalStateException("brain refused");

    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)))
        .hasMessage("brain refused");

    assertThat(GolemState.has(fresh)).isFalse();
    assertThat(registry.handle(fresh)).isEmpty();
    assertThat(platform.scale(fresh)).isEmpty();
    assertThat(roster.golem(snapshot.id()).orElseThrow().holds(item.state())).isTrue();
  }

  @Test
  void aGolemRestoredUnderTheFullServerCapWakesAtFullHealth() {
    settings.set(withCaps(32, 8, 1));
    GolemRoster roster = onlineOwner();
    registry.bind(owner, golem(new AtomicReference<>(at(40, 64, 40))), GolemBody.IRON, GolemCoreType.BLANK);
    GolemSnapshot snapshot = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem item = shattered(roster, snapshot, 0L);
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    platform.stat(fresh, GolemStat.MAX_HEALTH, 150D);
    GolemHandle handle = registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)).orElseThrow();
    assertThat(handle.isDormant()).isTrue();

    handle.restoreFullHealth();
    verify(fresh, never()).setHealth(anyDouble());
    settings.set(withCaps(32, 8, 2));
    registry.onTick();

    assertThat(handle.isDormant()).isFalse();
    verify(fresh).setHealth(150D);
    handle.restoreFullHealth();
    verify(fresh, times(2)).setHealth(150D);
  }

  @Test
  void restoreBlockersSkipThePlayerCapButKeepTheChunkCap() {
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    UUID id = UUID.randomUUID();
    platform.online.add(OWNER);
    assertThat(registry.restoreBlock(fresh, snapshot(id, GolemBody.IRON, GolemCoreType.BLANK, null)))
        .contains(GolemRegistry.BindBlock.OWNER_DATA_UNAVAILABLE);

    GolemRoster roster = onlineOwner();
    settings.set(withCaps(1, 1, 1500));
    shattered(roster, snapshot(id, GolemBody.IRON, GolemCoreType.BLANK, null), 0L);
    assertThat(registry.restoreBlock(fresh, snapshot(id, GolemBody.IRON, GolemCoreType.BLANK, null))).isEmpty();
    assertThat(registry.restoreBlock(fresh, snapshot(id, GolemBody.FROST, GolemCoreType.BLANK, null))).contains(GolemRegistry.BindBlock.BODY_MISMATCH);
    assertThat(registry.restoreBlock(fresh, snapshot(id, GolemBody.IRON, GolemCoreType.SORTER, null))).contains(GolemRegistry.BindBlock.CORE_NOT_ALLOWED);

    persistedGolem(UUID.randomUUID(), false, at(2, 64, 2));
    assertThat(registry.restoreBlock(fresh, snapshot(id, GolemBody.IRON, GolemCoreType.BLANK, null))).contains(GolemRegistry.BindBlock.CHUNK_CAP);

    MobRef bound = persistedGolem(UUID.randomUUID(), false, at(40, 64, 40));
    assertThat(registry.restoreBlock(bound, snapshot(id, GolemBody.IRON, GolemCoreType.BLANK, null))).contains(GolemRegistry.BindBlock.ALREADY_BOUND);
  }

  @Test
  void restoreNeedsTheGolemsThreadAndAnOpenBody() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    GolemSnapshot iron = snapshot(id, GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem item = shattered(roster, iron, 0L);
    MobRef fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemSnapshot frost = snapshot(id, GolemBody.FROST, GolemCoreType.BLANK, null);

    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(new PackedGolem(id, OWNER, frost.toJson()), GolemRoster.Status.SHATTERED)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(new PackedGolem(UUID.randomUUID(), OWNER, item.state()),
        GolemRoster.Status.SHATTERED))).isInstanceOf(IllegalStateException.class);
    platform.ownsEntities = false;
    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)))
        .isInstanceOf(IllegalStateException.class);

    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(GolemState.has(fresh)).isFalse();
  }

  @Test
  void snapshotsReadTheLiveHealthStoredNameAndCopperState() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    when(golem.health()).thenReturn(33D);
    when(golem.isPlayerCreated()).thenReturn(true);
    registry.rename(handle, GolemSnapshot.textName("Plate"));
    platform.patina.put(golem.uuid(), 2);
    platform.waxedGolems.add(golem.uuid());

    GolemSnapshot snapshot = registry.snapshot(handle);

    assertThat(snapshot.id()).isEqualTo(handle.id());
    assertThat(snapshot.health()).isEqualTo(33D);
    assertThat(snapshot.name()).contains(GolemSnapshot.textName("Plate"));
    assertThat(snapshot.copperStage()).isEqualTo(2);
    assertThat(snapshot.waxed()).isTrue();
    assertThat(snapshot.playerCreated()).isTrue();
    assertThat(snapshot.takenAt()).isEqualTo(platform.now);
  }

  @Test
  void designatingAMasterworkSavesTheRosterMarkBeforeTheGolemChanges() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    List<String> order = new ArrayList<>();
    platform.rosterListener = changed -> order.add("save roster=" + roster.golem(handle.id()).orElseThrow().masterwork() + " golem=" + handle.state().masterwork());
    int savesBefore = platform.changes(OWNER);

    assertThat(registry.designate(handle, 1)).isEqualTo(GolemRegistry.Designation.DESIGNATED);

    assertThat(order).containsExactly("save roster=true golem=false");
    assertThat(platform.changes(OWNER)).isEqualTo(savesBefore + 1);
    assertThat(handle.state().masterwork()).isTrue();
    assertThat(GolemState.read(golem).orElseThrow().masterwork()).isTrue();
    assertThat(roster.golem(handle.id()).orElseThrow().masterwork()).isTrue();
    assertThat(registry.masterworks(OWNER)).isEqualTo(1);
  }

  @Test
  void theMasterworkCapCountsUnloadedPackedAndShatteredMasterworks() {
    GolemRoster roster = onlineOwner();
    UUID packed = UUID.randomUUID();
    UUID unloaded = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(packed, GolemBody.COPPER, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(40, 40), 0L));
    roster.add(new GolemRoster.Binding(unloaded, GolemBody.FROST, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(-40, 40), 0L));
    roster.markMasterwork(packed, true);
    roster.stow(packed, GolemRoster.Status.PACKED, "packed");
    GolemHandle handle = registry.bind(owner, golem(new AtomicReference<>(at(0, 64, 0))), GolemBody.IRON, GolemCoreType.GUARDIAN);

    assertThat(registry.designate(handle, 1)).isEqualTo(GolemRegistry.Designation.CAP);
    assertThat(handle.state().masterwork()).isFalse();
    assertThat(roster.golem(handle.id()).orElseThrow().masterwork()).isFalse();

    roster.markMasterwork(unloaded, true);
    assertThat(registry.designate(handle, 2)).isEqualTo(GolemRegistry.Designation.CAP);
    assertThat(registry.masterworks(OWNER)).isEqualTo(2);
    assertThat(registry.designate(handle, 3)).isEqualTo(GolemRegistry.Designation.DESIGNATED);
    assertThat(registry.masterworks(OWNER)).isEqualTo(3);
  }

  @Test
  void designationIsRefusedWithoutTheLevelOrWithoutTheRoster() {
    GolemRoster roster = onlineOwner();
    GolemHandle handle = registry.bind(owner, golem(new AtomicReference<>(at(0, 64, 0))), GolemBody.IRON, GolemCoreType.GUARDIAN);

    assertThat(registry.designate(handle, 0)).isEqualTo(GolemRegistry.Designation.LOCKED);
    platform.rosters.remove(OWNER);
    assertThat(registry.designate(handle, 1)).isEqualTo(GolemRegistry.Designation.UNAVAILABLE);
    platform.rosters.put(OWNER, roster);
    assertThat(registry.designate(handle, 1)).isEqualTo(GolemRegistry.Designation.DESIGNATED);
    assertThat(registry.designate(handle, 2)).isEqualTo(GolemRegistry.Designation.ALREADY);
    assertThat(roster.masterworkCount()).isEqualTo(1);
  }

  @Test
  void aBoundBoneGolemCanBeDesignatedAMasterworkWithoutTouchingItsHealthCost() {
    GolemRoster roster = onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.BLANK);
    ItemRef heart = mock(ItemRef.class);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    assertThat(platform.upkeep.get(OWNER)).containsExactly(handle.id());

    assertThat(registry.designate(handle, 1)).isEqualTo(GolemRegistry.Designation.DESIGNATED);

    assertThat(handle.state().masterwork()).isTrue();
    assertThat(roster.golem(handle.id()).orElseThrow().masterwork()).isTrue();
    assertThat(GolemState.read(bone).orElseThrow().masterwork()).isTrue();
    assertThat(platform.upkeep.get(OWNER)).containsExactly(handle.id());
    assertThat(registry.undesignate(handle, 0)).contains(List.of(heart));
    assertThat(handle.state().masterwork()).isFalse();
    assertThat(platform.upkeep.get(OWNER)).containsExactly(handle.id());
  }

  @Test
  void aMasterworkBoneGolemTakesAGuardianSecondCoreFromTheCoresBoneAllows() {
    onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.BLANK);
    items.when(GolemItems::masterworkHeart).thenReturn(mock(ItemRef.class));
    FakeMastery mastery = new FakeMastery();
    mastery.perks = new GolemMastery.Perks(1, true, false, 0D);
    GolemMastery.register(mastery);
    try {
      assertThat(registry.addCore(handle, GolemCoreType.GUARDIAN)).contains(GolemMastery.CoreRefusal.NO_SLOT);
      registry.designate(handle, 1);

      assertThat(registry.addCore(handle, GolemCoreType.LUMBER)).contains(GolemMastery.CoreRefusal.NOT_ALLOWED);
      assertThat(registry.addCore(handle, GolemCoreType.GUARDIAN)).isEmpty();

      assertThat(GolemState.read(bone).orElseThrow().cores()).containsExactly(GolemCoreType.BLANK, GolemCoreType.GUARDIAN);
      assertThat(registry.removeCore(handle, GolemCoreType.GUARDIAN)).isTrue();
      assertThat(GolemState.read(bone).orElseThrow().cores()).containsExactly(GolemCoreType.BLANK);
    } finally {
      GolemMastery.unregister(mastery);
    }
  }

  @Test
  void removingTheDesignationClearsTheGolemBeforeReturningTheHeartTheSecondCoreAndTheExtraRunes() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    registry.designate(handle, 1);
    ItemRef heart = mock(ItemRef.class);
    ItemRef lumber = mock(ItemRef.class);
    ItemRef bulwark = mock(ItemRef.class);
    ItemRef tide = mock(ItemRef.class);
    GolemRune might = new GolemRune(GolemRuneType.MIGHT, 2);
    GolemRune swiftness = new GolemRune(GolemRuneType.SWIFTNESS, 1);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    items.when(() -> GolemItems.core(GolemCoreType.LUMBER)).thenReturn(lumber);
    items.when(() -> GolemItems.rune(new GolemRune(GolemRuneType.BULWARK, 3))).thenReturn(bulwark);
    items.when(() -> GolemItems.rune(new GolemRune(GolemRuneType.TIDE, 1))).thenReturn(tide);
    handle.setCores(List.of(GolemCoreType.GUARDIAN, GolemCoreType.LUMBER));
    handle.setRunes(List.of(might, swiftness, new GolemRune(GolemRuneType.BULWARK, 3), new GolemRune(GolemRuneType.TIDE, 1)));
    List<String> order = new ArrayList<>();
    platform.rosterListener = changed -> order.add("save roster=" + roster.golem(handle.id()).orElseThrow().masterwork() + " golem=" + handle.state().masterwork());

    Optional<List<ItemRef>> returned = registry.undesignate(handle, 2);

    assertThat(order).containsExactly("save roster=false golem=true");
    assertThat(returned).contains(List.of(heart, lumber, bulwark, tide));
    GolemState stored = GolemState.read(golem).orElseThrow();
    assertThat(stored.masterwork()).isFalse();
    assertThat(stored.cores()).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(stored.runes()).containsExactly(might, swiftness);
    assertThat(roster.golem(handle.id()).orElseThrow().masterwork()).isFalse();
    assertThat(registry.undesignate(handle, 2)).isEmpty();
  }

  @Test
  void aCompanionThatLosesItsSecondCoreLeavesWorkMode() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    items.when(GolemItems::masterworkHeart).thenReturn(mock(ItemRef.class));
    FakeMastery mastery = new FakeMastery();
    mastery.perks = new GolemMastery.Perks(1, true, false, 0D);
    GolemMastery.register(mastery);
    try {
      registry.designate(handle, 1);
      assertThat(registry.addCore(handle, GolemCoreType.LUMBER)).isEmpty();
      handle.setMode(GolemMode.WORK);

      assertThat(registry.removeCore(handle, GolemCoreType.LUMBER)).isTrue();
      assertThat(handle.state().mode()).isEqualTo(GolemMode.FOLLOW);

      registry.addCore(handle, GolemCoreType.LUMBER);
      handle.setMode(GolemMode.WORK);
      registry.undesignate(handle, 0);
      assertThat(handle.state().mode()).isEqualTo(GolemMode.FOLLOW);
      assertThat(handle.state().cores()).containsExactly(GolemCoreType.BLANK);
    } finally {
      GolemMastery.unregister(mastery);
    }
  }

  @Test
  void aSecondCoreNeedsTheDualCorePerkAndAnAllowedUnlockedRoleCore() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    FakeMastery mastery = new FakeMastery();
    GolemMastery.register(mastery);
    try {
      mastery.perks = new GolemMastery.Perks(1, true, false, 0D);
      assertThat(registry.addCore(handle, GolemCoreType.LUMBER)).contains(GolemMastery.CoreRefusal.NO_SLOT);
      registry.designate(handle, 1);
      mastery.perks = GolemMastery.Perks.NONE;
      assertThat(registry.addCore(handle, GolemCoreType.LUMBER)).contains(GolemMastery.CoreRefusal.NO_SLOT);
      mastery.perks = new GolemMastery.Perks(1, true, false, 0D);
      assertThat(registry.addCore(handle, GolemCoreType.SORTER)).contains(GolemMastery.CoreRefusal.NOT_ALLOWED);
      mastery.unlockedCores.remove(GolemCoreType.LUMBER);
      assertThat(registry.addCore(handle, GolemCoreType.LUMBER)).contains(GolemMastery.CoreRefusal.LOCKED);
      mastery.unlockedCores.add(GolemCoreType.LUMBER);

      assertThat(registry.addCore(handle, GolemCoreType.LUMBER)).isEmpty();
      assertThat(GolemState.read(golem).orElseThrow().cores()).containsExactly(GolemCoreType.GUARDIAN, GolemCoreType.LUMBER);
      assertThat(registry.addCore(handle, GolemCoreType.BREAKER)).contains(GolemMastery.CoreRefusal.NO_SLOT);
      assertThat(registry.removeCore(handle, GolemCoreType.BREAKER)).isFalse();
      assertThat(registry.removeCore(handle, GolemCoreType.GUARDIAN)).isFalse();
      assertThat(registry.removeCore(handle, GolemCoreType.LUMBER)).isTrue();
      assertThat(GolemState.read(golem).orElseThrow().cores()).containsExactly(GolemCoreType.GUARDIAN);
      assertThat(registry.removeCore(handle, GolemCoreType.LUMBER)).isFalse();
    } finally {
      GolemMastery.unregister(mastery);
    }
  }

  @Test
  void releasingAMasterworkDropsItsHeartWithItsLoadout() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    ItemRef heart = mock(ItemRef.class);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    registry.designate(handle, 1);

    assertThat(registry.release(handle)).isTrue();

    verify(world).dropItemNaturally(any(Vec3.class), eq(heart));
    verify(world).dropItemNaturally(any(Vec3.class), eq(coreItem));
  }

  @Test
  void aDyingMasterworkDropsItsHeartAtTheHeartChance() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 0D, 0D));
    GolemRoster roster = onlineOwner();
    ItemRef heart = mock(ItemRef.class);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    FakeMastery mastery = new FakeMastery();
    GolemMastery.register(mastery);
    try {
      MobRef lucky = golem(new AtomicReference<>(at(0, 64, 0)));
      GolemHandle first = registry.bind(owner, lucky, GolemBody.IRON, GolemCoreType.GUARDIAN);
      registry.designate(first, 2);
      List<ItemRef> kept = new ArrayList<>();
      registry.on(death(lucky, kept, 0));

      mastery.heartDropChance = 0D;
      MobRef unlucky = golem(new AtomicReference<>(at(2, 64, 0)));
      GolemHandle second = registry.bind(owner, unlucky, GolemBody.IRON, GolemCoreType.GUARDIAN);
      registry.designate(second, 2);
      List<ItemRef> lost = new ArrayList<>();
      registry.on(death(unlucky, lost, 0));

      assertThat(kept).containsExactly(heart);
      assertThat(lost).isEmpty();
      assertThat(roster.masterworkCount()).isZero();
    } finally {
      GolemMastery.unregister(mastery);
    }
  }

  @Test
  void aShatteredMasterworkKeepsItsDesignationInsteadOfDroppingItsHeart() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 1D, 1D));
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    ItemRef heart = mock(ItemRef.class);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    AtomicReference<PackedGolem> packed = new AtomicReference<>();
    items.when(() -> GolemItems.shatteredCore(any(PackedGolem.class))).thenAnswer(invocation -> {
      packed.set(invocation.getArgument(0));
      return mock(ItemRef.class);
    });
    registry.designate(handle, 1);
    RecordingMender mender = new RecordingMender();
    mender.shatters = true;
    GolemMend.register(mender);
    try {
      List<ItemRef> drops = new ArrayList<>();
      registry.on(death(golem, drops, 0));

      assertThat(drops).doesNotContain(heart);
      assertThat(GolemSnapshot.fromJson(packed.get().state()).orElseThrow().masterwork()).isTrue();
      assertThat(roster.golem(handle.id()).orElseThrow().masterwork()).isTrue();
      assertThat(roster.masterworkCount()).isEqualTo(1);
    } finally {
      GolemMend.unregister(mender);
    }
  }

  @Test
  void aPackedMasterworkKeepsItsDesignationInItsDormantGolemAndAwakensAsAMasterwork() {
    GolemRoster roster = onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(3, 64, 3)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    ItemRef heart = mock(ItemRef.class);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    registry.designate(handle, 1);

    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();

    assertThat(packing.snapshot().masterwork()).isTrue();
    assertThat(GolemSnapshot.fromJson(packing.packed().state()).orElseThrow().masterwork()).isTrue();
    items.verify(GolemItems::masterworkHeart, never());
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    assertThat(roster.golem(handle.id()).orElseThrow().masterwork()).isTrue();
    assertThat(roster.masterworkCount()).isEqualTo(1);

    Loc spot = at(30, 70, 30);
    MobRef body = golem(new AtomicReference<>(spot));
    platform.spawns.add(body);
    GolemHandle awakened = registry.awaken(owner, spot, new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    }).orElseThrow();

    assertThat(awakened.state().masterwork()).isTrue();
    assertThat(roster.golem(handle.id()).orElseThrow().masterwork()).isTrue();
    assertThat(roster.masterworkCount()).isEqualTo(1);
    items.verify(GolemItems::masterworkHeart, never());
  }

  @Test
  void aSightingCorrectsTheRosterMarkFromTheGolemsOwnState() {
    GolemRoster roster = onlineOwner();
    UUID stale = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(stale, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.markMasterwork(stale, true);
    MobRef plain = persistedGolem(stale, false, at(0, 64, 0));
    UUID missed = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(missed, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    MobRef masterwork = persistedGolem(missed, false, at(2, 64, 0));
    GolemState designated = GolemState.read(masterwork).orElseThrow();
    designated.setMasterwork(true);
    designated.write(masterwork);

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(plain, masterwork)));

    assertThat(roster.golem(stale).orElseThrow().masterwork()).isFalse();
    assertThat(roster.golem(missed).orElseThrow().masterwork()).isTrue();
  }

  @Test
  void bindingASkeletalServantShrinksItToFitOneBlockAndKeepsItsHealthCost() {
    GolemRoster roster = onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));

    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.BLANK);

    assertThat(platform.scale(bone)).singleElement().satisfies(modifier -> {
      assertThat(modifier.key()).isEqualTo(GolemScale.MODIFIER_KEY);
      assertThat(modifier.amount()).isCloseTo(-0.51D, offset(1e-9));
    });
    verify(bone).setPersistent(true);
    verify(bone).setRemoveWhenFarAway(false);
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(roster.golemCount()).isEqualTo(1);
    assertThat(platform.upkeep.get(OWNER)).containsExactly(handle.id());
  }

  @Test
  void bindingASkeletalServantStripsItsSummonGearWithoutDroppingIt() {
    onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));

    registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);

    for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAIN_HAND,
        EquipmentSlot.OFF_HAND)) {
      verify(bone).setEquipment(slot, null);
      verify(bone).setEquipmentDropChance(slot, 0F);
    }
    verify(bone).setCanPickupItems(false);
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    assertThat(platform.returned).isEmpty();
  }

  @Test
  void bindingAnIronGolemNeverTouchesItsEquipment() {
    onlineOwner();
    MobRef iron = golem(new AtomicReference<>(at(0, 64, 0)));

    registry.bind(owner, iron, GolemBody.IRON, GolemCoreType.GUARDIAN);

    verify(iron, never()).setEquipment(any(), any());
    verify(iron, never()).setEquipmentDropChance(any(), anyFloat());
    verify(iron, never()).setCanPickupItems(anyBoolean());
  }

  @Test
  void releasingABoneGolemReturnsItsEquipmentToItsOwnerWhileTheSkeletonCrumblesBare() {
    onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);
    handle.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("iron_helmet"), 1));
    handle.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.stack(Id.minecraft("bow"), 1));

    assertThat(registry.release(handle)).isTrue();

    assertThat(platform.returned).extracting(FakeGolemPlatform.Returned::owner).containsOnly(OWNER);
    assertThat(platform.returned).extracting(returned -> returned.item().type()).containsExactly(Id.minecraft("iron_helmet"), Id.minecraft("bow"));
    assertThat(platform.returned).extracting(returned -> returned.location().blockX()).containsOnly(0);
    assertThat(platform.crumbled).containsExactly(bone);
    verify(world).dropItemNaturally(any(Vec3.class), eq(coreItem));
    verify(world, times(1)).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void aDyingBoneGolemDropsItsEquipmentAtItsLocationLikeCarriedItems() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 0D, 0D));
    onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);
    handle.equip(GolemEquipmentSlot.BOOTS, GolemTestItems.stack(Id.minecraft("iron_boots"), 1));
    handle.equip(GolemEquipmentSlot.OFF_HAND, GolemTestItems.stack(Id.minecraft("shield"), 1));
    List<ItemRef> drops = new ArrayList<>(List.of(mock(ItemRef.class)));
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.entity()).thenReturn(bone);
    when(death.drops()).thenReturn(drops);

    registry.on(death);

    assertThat(drops).extracting(ItemRef::type).containsExactly(Id.minecraft("iron_boots"), Id.minecraft("shield"));
    assertThat(platform.returned).isEmpty();
  }

  @Test
  void aGuardianCoreBindsABoneBodyWhileWorkCoresAreRefused() {
    onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));

    assertThat(registry.bindBlock(owner, bone, GolemBody.BONE, GolemCoreType.GATHERER)).contains(GolemRegistry.BindBlock.CORE_NOT_ALLOWED);
    assertThat(registry.bindBlock(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN)).isEmpty();
    assertThat(registry.bindBlock(owner, bone, GolemBody.BONE, GolemCoreType.BLANK)).isEmpty();
  }

  @Test
  void onlyActiveOrMissingBoneEntriesKeepAHealthCostWhenTheirOwnerJoins() {
    GolemRoster roster = onlineOwner();
    UUID awake = UUID.randomUUID();
    UUID missing = UUID.randomUUID();
    UUID iron = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(awake, GolemBody.BONE, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.add(new GolemRoster.Binding(missing, GolemBody.BONE, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    roster.add(new GolemRoster.Binding(iron, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(missing, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);

    registry.on(new PlayerJoinEvent(owner));

    assertThat(platform.upkeep.get(OWNER)).containsExactlyInAnyOrder(awake, missing);
  }

  @Test
  void theHealthCostLeavesWithItsOwnerAndReturnsOnRejoinAndAfterARestart() {
    GolemRoster roster = onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);

    registry.on(new PlayerQuitEvent(owner));
    platform.quit(OWNER);

    assertThat(platform.upkeep.get(OWNER)).isEmpty();

    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner));

    assertThat(platform.upkeep.get(OWNER)).containsExactly(handle.id());

    FakeGolemPlatform restarted = new FakeGolemPlatform();
    restarted.join(OWNER, roster);
    new GolemRegistry(restarted, settings::get, new RecordingGolemLifecycle()).joinOnlineOwners();

    assertThat(restarted.upkeep.get(OWNER)).containsExactly(handle.id());
  }

  @Test
  void aDyingBoneGolemDropsItsRolledCoreButNoSkeletonLootAndEndsItsHealthCost() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 1D, 0D));
    GolemRoster roster = onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);
    ItemRef arrows = mock(ItemRef.class);
    List<ItemRef> drops = new ArrayList<>(List.of(arrows));
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.entity()).thenReturn(bone);
    when(death.drops()).thenReturn(drops);

    registry.on(death);

    assertThat(drops).containsExactly(coreItem);
    verify(death).setDroppedExp(0);
    assertThat(roster.golem(handle.id())).isEmpty();
    assertThat(platform.upkeep.get(OWNER)).isEmpty();
  }

  @Test
  void releasingABoneGolemCrumblesTheSkeletonReturnsItsCoreAndEndsItsHealthCost() {
    GolemRoster roster = onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);

    assertThat(registry.release(handle)).isTrue();

    verify(world).dropItemNaturally(any(Vec3.class), eq(coreItem));
    assertThat(platform.crumbled).containsExactly(bone);
    assertThat(roster.golem(handle.id())).isEmpty();
    assertThat(platform.upkeep.get(OWNER)).isEmpty();
    assertThat(platform.scale(bone)).isEmpty();
  }

  @Test
  void releasingAnIronGolemNeverCrumblesIt() {
    onlineOwner();
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    assertThat(registry.release(handle)).isTrue();

    assertThat(platform.crumbled).isEmpty();
  }

  @Test
  void releasingAMissingBoneEntryEndsItsHealthCost() {
    GolemRoster roster = onlineOwner();
    UUID missing = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(missing, GolemBody.BONE, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(missing, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    registry.on(new PlayerJoinEvent(owner));
    assertThat(platform.upkeep.get(OWNER)).containsExactly(missing);

    assertThat(registry.releaseEntry(OWNER, missing)).isTrue();

    assertThat(platform.upkeep.get(OWNER)).isEmpty();
  }

  @Test
  void aBoneGolemPacksIntoBoneAndRetainsItsEquipmentAndHealthReservation() {
    GolemRoster roster = onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.BLANK);
    ItemRef helmet = GolemTestItems.stack(Id.minecraft("iron_helmet"), 1);
    ItemRef sword = GolemTestItems.stack(Id.minecraft("iron_sword"), 1);
    handle.equip(GolemEquipmentSlot.HELMET, helmet);
    handle.equip(GolemEquipmentSlot.MAIN_HAND, sword);

    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    GolemSnapshot saved = GolemSnapshot.fromJson(packing.packed().state()).orElseThrow();

    assertThat(saved.equipment()).containsAllEntriesOf(handle.state().equipment());
    assertThat(saved.homeChest()).isNull();
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.PACKED);
    assertThat(GolemRegistry.upkept(roster)).containsExactly(handle.id());
    verify(bone).remove();
    verify(bone).setBurnsInDaylight(false);
    ArgumentCaptor<DormantGolem> dormant = ArgumentCaptor.forClass(DormantGolem.class);
    items.verify(() -> GolemItems.dormantGolem(dormant.capture()));
    assertThat(dormant.getValue().block()).isEqualTo(Id.minecraft("bone_block"));

    MobRef awakened = skeleton(new AtomicReference<>(at(3, 64, 3)));
    platform.spawns.add(awakened);
    GolemHandle restored = registry.awaken(owner, at(3, 64, 3),
        new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), spawned -> {
        }).orElseThrow();

    assertThat(restored.state().equipment()).isEqualTo(saved.equipment());
    assertThat(platform.upkeep.get(OWNER)).containsExactly(handle.id());
    verify(awakened).setBurnsInDaylight(false);
    verify(awakened).setEquipment(eq(EquipmentSlot.HEAD), argThat(item -> item != null && Id.minecraft("iron_helmet").equals(item.type())));
    verify(awakened).setEquipment(eq(EquipmentSlot.MAIN_HAND), argThat(item -> item != null && Id.minecraft("iron_sword").equals(item.type())));
  }

  @Test
  void revokingLivingBonesDropsTheirSuppliedLoadoutOnceAndFreesHealth() {
    GolemRoster roster = onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);
    handle.equip(GolemEquipmentSlot.MAIN_HAND, GolemTestItems.stack(Id.minecraft("iron_sword"), 1));
    handle.state().setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 1)));

    registry.revokeBones(OWNER);
    registry.revokeBones(OWNER);

    assertThat(handle.isRetired()).isTrue();
    assertThat(roster.golem(handle.id())).isEmpty();
    assertThat(platform.upkeep.get(OWNER)).isEmpty();
    assertThat(platform.crumbled).containsExactly(bone);
    assertThat(platform.returned).isEmpty();
    verify(world).dropItemNaturally(any(Vec3.class), eq(coreItem));
    verify(world).dropItemNaturally(any(Vec3.class), eq(runeItem));
    verify(world).dropItemNaturally(any(Vec3.class), argThat(item -> Id.minecraft("iron_sword").equals(item.type())));
    verify(world, times(3)).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void revokedUnloadedBonesStayRevokedAcrossRestartAndOnlyOneBodyReturnsTheirItems() {
    GolemRoster roster = onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);
    handle.equip(GolemEquipmentSlot.OFF_HAND, GolemTestItems.stack(Id.minecraft("shield"), 1));
    registry.unload(handle);
    GolemState saved = GolemState.read(bone).orElseThrow();

    registry.revokeBones(OWNER);
    GolemRoster restoredRoster = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);
    platform.rosters.put(OWNER, restoredRoster);
    GolemRegistry restarted = new GolemRegistry(platform, settings::get, lifecycle);
    restarted.adopt(bone);
    MobRef duplicate = skeleton(new AtomicReference<>(at(1, 64, 0)));
    saved.write(duplicate);
    restarted.adopt(duplicate);

    assertThat(restoredRoster.golem(handle.id())).isEmpty();
    assertThat(restoredRoster.golemCount()).isZero();
    assertThat(platform.upkeep.get(OWNER)).isEmpty();
    assertThat(platform.crumbled).containsExactly(bone, duplicate);
    verify(world).dropItemNaturally(any(Vec3.class), eq(coreItem));
    verify(world).dropItemNaturally(any(Vec3.class), argThat(item -> Id.minecraft("shield").equals(item.type())));
    verify(world, times(2)).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void revokedPackedBonesReturnEquipmentOnlyOnceAndCannotAwakenAfterRelearning() {
    GolemRoster roster = onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);
    handle.equip(GolemEquipmentSlot.HELMET, GolemTestItems.stack(Id.minecraft("iron_helmet"), 1));
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();

    registry.revokeBones(OWNER);
    GolemRoster restored = Json.fromJson(Json.toJson(roster, false), GolemRoster.class);
    platform.rosters.put(OWNER, restored);
    assertThat(restored.golemCount()).isZero();
    assertThat(GolemRegistry.upkept(restored)).isEmpty();
    assertThat(registry.awaken(owner, at(3, 64, 3), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), spawned -> {
    })).isEmpty();
    assertThat(registry.dissolveRevoked(packing.packed(), at(0, 64, 0))).isTrue();
    assertThat(registry.dissolveRevoked(packing.packed(), at(0, 64, 0))).isFalse();
    verify(world).dropItemNaturally(any(Vec3.class), eq(coreItem));
    verify(world).dropItemNaturally(any(Vec3.class), argThat(item -> Id.minecraft("iron_helmet").equals(item.type())));
    verify(world, times(2)).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void revocationConsumesAPackedBoneInTheOwnersInventoryAndDropsItsLoadout() {
    onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    when(owner.inventory()).thenReturn(inventory);
    when(owner.location()).thenReturn(at(4, 64, 4));
    when(inventory.size()).thenReturn(1);
    when(inventory.item(0)).thenReturn(dormantItem);
    items.when(() -> GolemItems.packedGolemOf(dormantItem)).thenReturn(Optional.of(packing.packed()));
    platform.players.put(OWNER, owner);

    registry.revokeBones(OWNER);

    verify(inventory).clear(0);
    verify(owner).saveData();
    verify(world).dropItemNaturally(eq(at(4, 64, 4).position()), eq(coreItem));
    assertThat(platform.upkeep.get(OWNER)).isEmpty();
  }

  @Test
  void joiningWithoutSkeletalServantRevokesAnExistingBone() {
    onlineOwner();
    MobRef bone = skeleton(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);
    platform.keepsBoneGolems = false;

    registry.on(new PlayerJoinEvent(owner));

    assertThat(handle.isRetired()).isTrue();
    assertThat(platform.crumbled).containsExactly(bone);
    assertThat(platform.upkeep.get(OWNER)).isEmpty();
  }

  private MobRef skeleton(AtomicReference<Loc> location) {
    MobRef skeleton = GolemTestEntities.golem(GolemBody.BONE);
    lenient().when(skeleton.location()).thenAnswer(invocation -> location.get());
    lenient().when(skeleton.world()).thenReturn(world);
    lenient().when(skeleton.isValid()).thenReturn(true);
    track(skeleton, location.get());
    return skeleton;
  }

  private <T> T companion(Class<T> type) {
    for (Object listener : registry.createCompanionListeners()) {
      if (type.isInstance(listener)) {
        return type.cast(listener);
      }
    }
    throw new AssertionError("no companion " + type.getSimpleName());
  }

  private static GolemStatSource statSource(String id, AtomicReference<Double> armor) {
    return new GolemStatSource() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public List<GolemStatDelta> deltas(GolemHandle handle) {
        return List.of(GolemStatDelta.add(GolemStat.ARMOR, armor.get()));
      }
    };
  }

  private GolemHandle releasableGatherer(double essence) {
    onlineOwner();
    platform.players.put(OWNER, owner);
    when(owner.isOnline()).thenReturn(true);
    MobRef golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(essence);
    return handle;
  }

  private EntityDeathEvent death(MobRef golem, List<ItemRef> drops, int experience) {
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    lenient().when(death.entity()).thenReturn(golem);
    lenient().when(death.drops()).thenReturn(drops);
    lenient().when(death.droppedExp()).thenReturn(experience);
    return death;
  }

  private GolemSnapshot snapshot(UUID id, GolemBody body, GolemCoreType core, String name) {
    GolemState state = GolemState.create(new GolemState.Seed(id, OWNER, body, core, GolemAnchor.of(WORLD, 5, 64, 5)));
    state.setRunes(List.of(new GolemRune(GolemRuneType.BULWARK, 3)));
    state.stats().addKills(12L);
    return GolemSnapshot.capture(state, new GolemSnapshot.Traits(name, 0D, 0, false, true), platform.now);
  }

  private PackedGolem shattered(GolemRoster roster, GolemSnapshot snapshot, long chunkKey) {
    roster.add(new GolemRoster.Binding(snapshot.id(), snapshot.body(), snapshot.primaryCore(), WORLD, chunkKey, 0L));
    PackedGolem item = new PackedGolem(snapshot.id(), OWNER, snapshot.toJson());
    roster.stow(snapshot.id(), GolemRoster.Status.SHATTERED, item.state());
    return item;
  }

  private static GolemRegistry.Restore restoreOf(GolemSnapshot snapshot) {
    return new GolemRegistry.Restore(new PackedGolem(snapshot.id(), OWNER, snapshot.toJson()), GolemRoster.Status.SHATTERED);
  }

  private void assertDroppedOnceAt(Loc location, int experience) {
    verify(world, times(1)).spawnExperience(eq(location.position()), eq(experience));
    verify(owner, never()).giveExp(anyInt());
  }

  private GolemRoster onlineOwner() {
    GolemRoster roster = platform.rosters.getOrDefault(OWNER, new GolemRoster());
    platform.join(OWNER, roster);
    return roster;
  }

  private MobRef golem(AtomicReference<Loc> location) {
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);
    lenient().when(golem.location()).thenAnswer(invocation -> location.get());
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    track(golem, location.get());
    return golem;
  }

  private MobRef activeGolem(GolemRoster roster, Loc location) {
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, location.world().uid(), 0L, 0L));
    return persistedGolem(id, false, location);
  }

  private MobRef persistedGolem(UUID id, boolean dormant, Loc location) {
    MobRef golem = golem(new AtomicReference<>(location));
    GolemState state = GolemState.create(new GolemState.Seed(id, OWNER, GolemBody.IRON, GolemCoreType.BLANK, GolemAnchor.at(location)));
    state.setDormant(dormant);
    state.write(golem);
    return golem;
  }

  private static MobRef withSighting(MobRef golem, long sighting) {
    GolemState state = GolemState.read(golem).orElseThrow();
    state.setSighting(sighting);
    state.write(golem);
    return golem;
  }

  private void track(EntityRef entity, Loc location) {
    long key = GolemRoster.chunkKey(location.blockX() >> 4, location.blockZ() >> 4);
    chunkEntities.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entity);
  }

  private Loc at(double x, double y, double z) {
    return new Loc(world, x + 0.5D, y, z + 0.5D);
  }

  private List<EntityRef> chunkEntities(Box box) {
    long key = GolemRoster.chunkKey(((int) Math.floor(box.minX())) >> 4, ((int) Math.floor(box.minZ())) >> 4);
    return List.copyOf(chunkEntities.getOrDefault(key, List.of()));
  }

  private static GolemSettings withCaps(int perPlayer, int perChunk, int server) {
    GolemSettings defaults = GolemSettings.defaults();
    return new GolemSettings(true, false, server, perChunk, perPlayer, defaults.bodyScales(), defaults.bodyCarry(), defaults.allowedCores(), 0.5D, 0.5D);
  }
}
