package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.item.DormantGolem;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.PackedGolem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class GolemRegistryNamesTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final Component CLANK = Component.text("Clank");

  private final Map<Long, List<Entity>> chunkEntities = new HashMap<>();
  private FakeGolemPlatform platform;
  private GolemRegistry registry;
  private World world;
  private Player owner;
  private MockedStatic<GolemItems> items;

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    registry = new GolemRegistry(platform, GolemSettings::defaults, new RecordingGolemLifecycle());
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.getChunkAt(any(Location.class))).thenAnswer(invocation -> chunk(invocation.<Location>getArgument(0)));
    owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    lenient().when(owner.getName()).thenReturn("Steve");
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.core(any(GolemCoreType.class))).thenReturn(mock(ItemStack.class));
    items.when(() -> GolemItems.dormantBlock(any(GolemBody.class), anyInt(), anyBoolean())).thenCallRealMethod();
    items.when(() -> GolemItems.isDormantBlock(any())).thenCallRealMethod();
    items.when(() -> GolemItems.dormantGolem(any(DormantGolem.class))).thenReturn(mock(ItemStack.class));
    platform.join(OWNER, new GolemRoster());
  }

  @AfterEach
  void tearDown() {
    items.close();
  }

  @Test
  void bindingStoresTheDefaultNameAndVanillaShowsItOnTheEntity() {
    IronGolem golem = golem(at(0, 64, 0));

    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    assertThat(plain(GolemState.read(golem).orElseThrow().name().orElseThrow())).isEqualTo("Steve's Iron Golem");
    assertThat(plain(handle.state().name().orElseThrow())).isEqualTo("Steve's Iron Golem");
    assertThat(plain(golem.customName())).isEqualTo("Steve's Iron Golem");
  }

  @Test
  void glossKeepsTheCustomNameForItsOverlay() {
    platform.nameMode = GolemNameMode.GLOSS;
    IronGolem golem = golem(at(0, 64, 0));

    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    assertThat(plain(golem.customName())).isEqualTo("Steve's Iron Golem");
  }

  @Test
  void insightStoresTheNameButLeavesTheEntityUnnamed() {
    platform.nameMode = GolemNameMode.INSIGHT;
    IronGolem golem = golem(at(0, 64, 0));
    golem.customName(Component.text("Village Guard"));

    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    assertThat(golem.customName()).isNull();
    assertThat(plain(handle.state().name().orElseThrow())).isEqualTo("Steve's Iron Golem");
  }

  @Test
  void aRenameIsStoredFlushedAndShownByTheMode() {
    IronGolem golem = golem(at(0, 64, 0));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    registry.rename(handle, CLANK);

    assertThat(handle.state().name()).contains(CLANK);
    assertThat(GolemState.read(golem).orElseThrow().name()).contains(CLANK);
    assertThat(golem.customName()).isEqualTo(CLANK);

    platform.nameMode = GolemNameMode.INSIGHT;
    registry.rename(handle, Component.text("Bolt"));
    assertThat(GolemState.read(golem).orElseThrow().name()).contains(Component.text("Bolt"));
    assertThat(golem.customName()).isNull();
  }

  @Test
  void renamingNeedsTheGolemsThread() {
    IronGolem golem = golem(at(0, 64, 0));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    platform.ownsEntities = false;

    assertThatThrownBy(() -> registry.rename(handle, CLANK)).isInstanceOf(IllegalStateException.class);
    assertThat(handle.state().name().map(GolemRegistryNamesTest::plain)).contains("Steve's Iron Golem");
  }

  @Test
  void aRenamedGolemKeepsItsNameThroughStasisPacking() {
    IronGolem golem = golem(at(3, 64, 3));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    registry.rename(handle, CLANK);

    GolemRegistry.Packing packing = registry.pack(handle).orElseThrow();
    assertThat(packing.snapshot().name()).contains(CLANK);
    IronGolem body = golem(at(30, 70, 30));
    platform.spawns.add(body);
    platform.nameMode = GolemNameMode.GLOSS;

    GolemHandle awakened = registry.awaken(owner, at(30, 70, 30), new GolemRegistry.Restore(packing.packed(), GolemRoster.Status.PACKED), mob -> {
    }).orElseThrow();

    assertThat(awakened.state().name()).contains(CLANK);
    assertThat(GolemState.read(body).orElseThrow().name()).contains(CLANK);
    assertThat(body.customName()).isEqualTo(CLANK);
  }

  @Test
  void aRenamedGolemKeepsItsNameThroughAShatteredCoreRebuild() {
    IronGolem golem = golem(at(0, 64, 0));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    registry.rename(handle, CLANK);
    GolemSnapshot shattered = registry.snapshot(handle).withoutCargo();
    GolemRoster roster = platform.rosters.get(OWNER);
    PackedGolem item = new PackedGolem(handle.id(), OWNER, shattered.toJson());
    roster.stow(handle.id(), GolemRoster.Status.SHATTERED, item.state());
    platform.nameMode = GolemNameMode.INSIGHT;
    IronGolem fresh = golem(at(4, 64, 4));

    GolemHandle rebuilt = registry.restore(owner, fresh, new GolemRegistry.Restore(item, GolemRoster.Status.SHATTERED)).orElseThrow();

    assertThat(rebuilt.state().name()).contains(CLANK);
    assertThat(fresh.customName()).isNull();
  }

  @Test
  void aModeChangeReappliesNamesToEveryLoadedGolemOnItsThread() {
    IronGolem first = golem(at(0, 64, 0));
    IronGolem second = golem(at(40, 64, 40));
    GolemHandle firstHandle = registry.bind(owner, first, GolemBody.IRON, GolemCoreType.GUARDIAN);
    registry.bind(owner, second, GolemBody.IRON, GolemCoreType.GATHERER);
    registry.rename(firstHandle, CLANK);
    platform.nameMode = GolemNameMode.INSIGHT;
    platform.ownsEntities = false;

    registry.showNames();

    assertThat(first.customName()).isEqualTo(CLANK);
    platform.ownsEntities = true;
    platform.runDelayed();
    assertThat(first.customName()).isNull();
    assertThat(second.customName()).isNull();

    platform.nameMode = GolemNameMode.GLOSS;
    registry.showNames();
    assertThat(first.customName()).isEqualTo(CLANK);
    assertThat(plain(second.customName())).isEqualTo("Steve's Iron Golem");
  }

  @Test
  void aLoadedGolemTakesTheCurrentModesName() {
    IronGolem stored = golem(at(0, 64, 0));
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.BLANK,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    state.setName(CLANK);
    state.write(stored);
    platform.rosters.get(OWNER).add(new GolemRoster.Binding(state.id(), GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    stored.customName(Component.text("Stale"));
    platform.nameMode = GolemNameMode.INSIGHT;

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(stored)));

    assertThat(registry.handle(stored)).isPresent();
    assertThat(stored.customName()).isNull();

    registry.on(new EntitiesUnloadEvent(mock(Chunk.class), List.of(stored)));
    platform.nameMode = GolemNameMode.VANILLA;
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(stored)));
    assertThat(stored.customName()).isEqualTo(CLANK);
  }

  private IronGolem golem(Location location) {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    AtomicReference<Component> shown = new AtomicReference<>();
    lenient().when(golem.customName()).thenAnswer(invocation -> shown.get());
    lenient().doAnswer(invocation -> {
      shown.set(invocation.getArgument(0));
      return null;
    }).when(golem).customName(any());
    lenient().when(golem.getLocation()).thenAnswer(invocation -> location.clone());
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.getHealth()).thenReturn(100D);
    long key = GolemRoster.chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    chunkEntities.computeIfAbsent(key, ignored -> new ArrayList<>()).add(golem);
    return golem;
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x + 0.5D, y, z + 0.5D);
  }

  private Chunk chunk(Location location) {
    Chunk chunk = mock(Chunk.class);
    long key = GolemRoster.chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    lenient().when(chunk.getEntities()).thenAnswer(invocation -> Optional.ofNullable(chunkEntities.get(key)).orElse(List.of()).toArray(new Entity[0]));
    return chunk;
  }

  private static String plain(Component component) {
    return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
  }
}
