package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.item.DormantGolem;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GolemRegistryNamesTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final String CLANK = GolemSnapshot.textName("Clank");

  private final Map<Long, List<EntityRef>> chunkEntities = new HashMap<>();
  private FakeGolemPlatform platform;
  private GolemRegistry registry;
  private WorldRef world;
  private PlayerRef owner;
  private MockedStatic<GolemItems> items;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    registry = new GolemRegistry(platform, GolemSettings::defaults, new RecordingGolemLifecycle());
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    lenient().when(world.minHeight()).thenReturn(-64);
    lenient().when(world.maxHeight()).thenReturn(320);
    lenient().when(world.entitiesIn(any(Box.class), nullable(java.util.function.Predicate.class)))
        .thenAnswer(invocation -> chunk(invocation.<Box>getArgument(0)));
    owner = mock(PlayerRef.class);
    lenient().when(owner.uuid()).thenReturn(OWNER);
    lenient().when(owner.name()).thenReturn("Steve");
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.core(any(GolemCoreType.class))).thenReturn(mock(ItemRef.class));
    items.when(() -> GolemItems.dormantBlock(any(GolemBody.class), anyInt(), anyBoolean())).thenCallRealMethod();
    items.when(() -> GolemItems.isDormantBlock(any())).thenCallRealMethod();
    items.when(() -> GolemItems.dormantGolem(any(DormantGolem.class))).thenReturn(mock(ItemRef.class));
    platform.join(OWNER, new GolemRoster());
  }

  @AfterEach
  void tearDown() {
    items.close();
  }

  @Test
  void bindingStoresTheDefaultNameAndShowsItOnTheEntity() {
    MobRef golem = golem(at(0, 64, 0));

    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    assertThat(plain(GolemState.read(golem).orElseThrow().name().orElseThrow())).isEqualTo("Steve's Iron Golem");
    assertThat(plain(handle.state().name().orElseThrow())).isEqualTo("Steve's Iron Golem");
    assertThat(plain(golem.customNameJson())).isEqualTo("Steve's Iron Golem");
  }

  @Test
  void aRenameIsStoredFlushedAndShown() {
    MobRef golem = golem(at(0, 64, 0));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    registry.rename(handle, CLANK);

    assertThat(handle.state().name()).contains(CLANK);
    assertThat(GolemState.read(golem).orElseThrow().name()).contains(CLANK);
    assertThat(golem.customNameJson()).isEqualTo(CLANK);
  }

  @Test
  void renamingNeedsTheGolemsThread() {
    MobRef golem = golem(at(0, 64, 0));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    platform.ownsEntities = false;

    assertThatThrownBy(() -> registry.rename(handle, CLANK)).isInstanceOf(IllegalStateException.class);
    assertThat(handle.state().name().map(GolemRegistryNamesTest::plain)).contains("Steve's Iron Golem");
  }

  @Test
  void aRenamedGolemKeepsItsNameThroughStasisPacking() {
    MobRef golem = golem(at(3, 64, 3));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    registry.rename(handle, CLANK);

    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    assertThat(packing.snapshot().name()).contains(CLANK);
    MobRef body = golem(at(30, 70, 30));
    platform.spawns.add(body);

    GolemHandle awakened = registry.awaken(owner, at(30, 70, 30), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    }).orElseThrow();

    assertThat(awakened.state().name()).contains(CLANK);
    assertThat(GolemState.read(body).orElseThrow().name()).contains(CLANK);
    assertThat(body.customNameJson()).isEqualTo(CLANK);
  }

  @Test
  void aRenamedGolemKeepsItsNameThroughAShatteredCoreRebuild() {
    MobRef golem = golem(at(0, 64, 0));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    registry.rename(handle, CLANK);
    GolemSnapshot shattered = registry.snapshot(handle).withoutCargo();
    GolemRoster roster = platform.rosters.get(OWNER);
    PackedGolem item = new PackedGolem(handle.id(), OWNER, shattered.toJson());
    roster.stow(handle.id(), GolemRoster.Status.SHATTERED, item.state());
    MobRef fresh = golem(at(4, 64, 4));

    GolemHandle rebuilt = registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)).orElseThrow();

    assertThat(rebuilt.state().name()).contains(CLANK);
    assertThat(fresh.customNameJson()).isEqualTo(CLANK);
  }

  @Test
  void glossLeavesTheEntityUnnamedWhileBindingStoresTheName() {
    platform.nameMode = GolemNameMode.GLOSS;
    MobRef golem = golem(at(0, 64, 0));
    golem.setCustomNameJson(GolemSnapshot.textName("Village Guard"));

    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    assertThat(golem.customNameJson()).isNull();
    assertThat(plain(handle.state().name().orElseThrow())).isEqualTo("Steve's Iron Golem");
    assertThat(plain(GolemState.read(golem).orElseThrow().name().orElseThrow())).isEqualTo("Steve's Iron Golem");
  }

  @Test
  void glossLeavesABoundBoneGolemUnnamedAndVanillaNamesIt() {
    platform.nameMode = GolemNameMode.GLOSS;
    MobRef glossBone = entity(GolemBody.BONE.entityType(), at(0, 64, 0));

    GolemHandle glossHandle = registry.bind(owner, glossBone, GolemBody.BONE, GolemCoreType.GUARDIAN);

    assertThat(glossBone.customNameJson()).isNull();
    assertThat(plain(glossHandle.state().name().orElseThrow())).isEqualTo("Steve's Bone Golem");

    platform.nameMode = GolemNameMode.VANILLA;
    MobRef vanillaBone = entity(GolemBody.BONE.entityType(), at(20, 64, 20));

    registry.bind(owner, vanillaBone, GolemBody.BONE, GolemCoreType.GUARDIAN);

    assertThat(plain(vanillaBone.customNameJson())).isEqualTo("Steve's Bone Golem");
  }

  @Test
  void glossLeavesAnAwakenedAndARebuiltGolemUnnamed() {
    MobRef golem = golem(at(3, 64, 3));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    registry.rename(handle, CLANK);
    GolemRegistry.Packing packing = registry.pack(handle).join().orElseThrow();
    MobRef body = golem(at(30, 70, 30));
    platform.spawns.add(body);
    platform.nameMode = GolemNameMode.GLOSS;

    GolemHandle awakened = registry.awaken(owner, at(30, 70, 30), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    }).orElseThrow();

    assertThat(awakened.state().name()).contains(CLANK);
    assertThat(body.customNameJson()).isNull();

    GolemSnapshot shattered = registry.snapshot(awakened).withoutCargo();
    PackedGolem item = new PackedGolem(awakened.id(), OWNER, shattered.toJson());
    platform.rosters.get(OWNER).stow(awakened.id(), GolemRoster.Status.SHATTERED, item.state());
    MobRef fresh = golem(at(4, 64, 4));

    GolemHandle rebuilt = registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)).orElseThrow();

    assertThat(rebuilt.state().name()).contains(CLANK);
    assertThat(fresh.customNameJson()).isNull();
  }

  @Test
  void aRenameInGlossStoresTheNameAndLeavesTheEntityAlone() {
    platform.nameMode = GolemNameMode.GLOSS;
    MobRef golem = golem(at(0, 64, 0));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    registry.rename(handle, CLANK);

    assertThat(handle.state().name()).contains(CLANK);
    assertThat(GolemState.read(golem).orElseThrow().name()).contains(CLANK);
    assertThat(golem.customNameJson()).isNull();
    verify(golem, never()).setCustomNameJson(any());
  }

  @Test
  void aSwitchBetweenGlossAndVanillaClearsAndRestoresEachGolemsNameOnItsThread() {
    MobRef first = golem(at(0, 64, 0));
    MobRef second = golem(at(40, 64, 40));
    GolemHandle firstHandle = registry.bind(owner, first, GolemBody.IRON, GolemCoreType.GUARDIAN);
    registry.bind(owner, second, GolemBody.IRON, GolemCoreType.GATHERER);
    registry.rename(firstHandle, CLANK);
    platform.nameMode = GolemNameMode.GLOSS;
    platform.ownsEntities = false;

    registry.showNames();

    assertThat(first.customNameJson()).isEqualTo(CLANK);
    platform.ownsEntities = true;
    platform.runDelayed();
    assertThat(first.customNameJson()).isNull();
    assertThat(second.customNameJson()).isNull();
    assertThat(firstHandle.state().name()).contains(CLANK);

    platform.nameMode = GolemNameMode.VANILLA;
    platform.ownsEntities = false;
    registry.showNames();
    assertThat(first.customNameJson()).isNull();
    platform.ownsEntities = true;
    platform.runDelayed();

    assertThat(first.customNameJson()).isEqualTo(CLANK);
    assertThat(plain(second.customNameJson())).isEqualTo("Steve's Iron Golem");
  }

  @Test
  void aSwitchToVanillaNamesABoneGolemToo() {
    platform.nameMode = GolemNameMode.GLOSS;
    MobRef bone = entity(GolemBody.BONE.entityType(), at(0, 64, 0));
    registry.bind(owner, bone, GolemBody.BONE, GolemCoreType.GUARDIAN);
    assertThat(bone.customNameJson()).isNull();

    platform.nameMode = GolemNameMode.VANILLA;
    registry.showNames();

    assertThat(plain(bone.customNameJson())).isEqualTo("Steve's Bone Golem");
  }

  @Test
  void aLoadedGolemTakesItsStoredName() {
    MobRef stored = golem(at(0, 64, 0));
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.BLANK,
        GolemAnchor.of(WORLD, 0, 64, 0)));
    state.setName(CLANK);
    state.write(stored);
    platform.rosters.get(OWNER).add(new GolemRoster.Binding(state.id(), GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    stored.setCustomNameJson(GolemSnapshot.textName("Stale"));

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(stored)));

    assertThat(registry.handle(stored)).isPresent();
    assertThat(stored.customNameJson()).isEqualTo(CLANK);
  }

  @Test
  void aLoadedGolemInGlossModeLosesAStaleNameAndKeepsTheStoredOne() {
    MobRef stored = golem(at(0, 64, 0));
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.BLANK,
        GolemAnchor.of(WORLD, 0, 64, 0)));
    state.setName(CLANK);
    state.write(stored);
    platform.rosters.get(OWNER).add(new GolemRoster.Binding(state.id(), GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    stored.setCustomNameJson(CLANK);
    platform.nameMode = GolemNameMode.GLOSS;

    registry.on(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(stored)));

    GolemHandle handle = registry.handle(stored).orElseThrow();
    assertThat(stored.customNameJson()).isNull();
    assertThat(handle.state().name()).contains(CLANK);
  }

  private MobRef golem(Loc location) {
    return entity(GolemBody.IRON.entityType(), location);
  }

  private MobRef entity(Id type, Loc location) {
    MobRef golem = GolemTestEntities.entity(MobRef.class, type);
    AtomicReference<String> shown = new AtomicReference<>();
    lenient().when(golem.customNameJson()).thenAnswer(invocation -> shown.get());
    lenient().doAnswer(invocation -> {
      shown.set(invocation.getArgument(0));
      return null;
    }).when(golem).setCustomNameJson(nullable(String.class));
    lenient().when(golem.location()).thenReturn(location);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.health()).thenReturn(100D);
    long key = GolemRoster.chunkKey(location.blockX() >> 4, location.blockZ() >> 4);
    chunkEntities.computeIfAbsent(key, ignored -> new ArrayList<>()).add(golem);
    return golem;
  }

  private Loc at(double x, double y, double z) {
    return new Loc(world, x + 0.5D, y, z + 0.5D);
  }

  private List<EntityRef> chunk(Box box) {
    long key = GolemRoster.chunkKey(((int) Math.floor(box.minX())) >> 4, ((int) Math.floor(box.minZ())) >> 4);
    return List.copyOf(chunkEntities.getOrDefault(key, List.of()));
  }

  private static String plain(String json) {
    return json == null ? null : GolemSnapshot.plainName(json);
  }
}
