package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.volmlib.util.entity.StackExclusion;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemRegistryTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

  private final Map<Long, List<Entity>> chunkEntities = new HashMap<>();
  private final AtomicReference<GolemSettings> settings = new AtomicReference<>(GolemSettings.defaults());
  private FakeGolemPlatform platform;
  private RecordingGolemLifecycle lifecycle;
  private GolemRegistry registry;
  private World world;
  private Player owner;
  private MockedStatic<GolemItems> items;
  private ItemStack coreItem;
  private ItemStack runeItem;

  @BeforeAll
  static void registerGolemKind() {
    MinionIdentity.registerKind(GolemancyRuntime.MINION_KIND);
  }

  @BeforeEach
  void setUp() {
    platform = new FakeGolemPlatform();
    lifecycle = new RecordingGolemLifecycle();
    registry = new GolemRegistry(platform, settings::get, lifecycle);
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    lenient().when(world.getChunkAt(any(Location.class))).thenAnswer(invocation -> chunk(invocation.<Location>getArgument(0)));
    owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    lenient().when(owner.getName()).thenReturn("Steve");
    coreItem = mock(ItemStack.class);
    runeItem = mock(ItemStack.class);
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.core(any(GolemCoreType.class))).thenReturn(coreItem);
    items.when(() -> GolemItems.rune(any(GolemRune.class))).thenReturn(runeItem);
  }

  @AfterEach
  void tearDown() {
    items.close();
  }

  @Test
  void bindWritesStateRosterAndBoundFlags() {
    GolemRoster roster = onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(3, 64, -20)));

    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);

    GolemState stored = GolemState.read(golem).orElseThrow();
    assertThat(stored.id()).isEqualTo(handle.id());
    assertThat(stored.owner()).isEqualTo(OWNER);
    assertThat(stored.body()).isEqualTo(GolemBody.IRON);
    assertThat(stored.cores()).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(stored.home()).isEqualTo(GolemState.Anchor.of(WORLD, 3, 64, -20));
    assertThat(stored.previouslyBound()).isFalse();
    assertThat(stored.dormant()).isFalse();

    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(entry.world()).isEqualTo(WORLD);
    assertThat(entry.chunkKey()).isEqualTo(GolemRoster.chunkKey(0, -2));
    assertThat(entry.lastSeen()).isEqualTo(platform.now);
    assertThat(platform.changes(OWNER)).isPositive();

    assertThat(platform.scale(golem)).singleElement().satisfies(modifier -> {
      assertThat(modifier.getKey()).isEqualTo(GolemScale.MODIFIER_KEY);
      assertThat(modifier.getAmount()).isCloseTo(-0.64D, offset(1e-9));
    });
    verify(golem).setPersistent(true);
    verify(golem).setRemoveWhenFarAway(false);
    assertThat(StackExclusion.isExcluded(golem)).isTrue();
    ArgumentCaptor<Component> name = ArgumentCaptor.forClass(Component.class);
    verify(golem).customName(name.capture());
    assertThat(PlainTextComponentSerializer.plainText().serialize(name.getValue())).isEqualTo("Steve's Iron Golem");

    assertThat(registry.handle(golem)).contains(handle);
    assertThat(registry.loadedOf(OWNER)).containsExactly(handle);
    assertThat(registry.isBound(golem)).isTrue();
    assertThat(registry.ownerOf(golem)).contains(OWNER);
    assertThat(MinionIdentity.isMinion(golem)).isTrue();
    assertThat(MinionIdentity.ownerOf(golem)).isEqualTo(OWNER);
    assertThat(handle.isDormant()).isFalse();
    assertThat(registry.activeCount()).isEqualTo(1);
  }

  @Test
  void bindMovesTheUnboundPoolIntoTheState() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    golem.getPersistentDataContainer().set(HostileDamagePool.POOL_KEY, PersistentDataType.DOUBLE, 12D);

    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    assertThat(GolemState.read(golem).orElseThrow().hostileDamagePool()).isEqualTo(12D);
    assertThat(golem.getPersistentDataContainer().get(HostileDamagePool.POOL_KEY, PersistentDataType.DOUBLE)).isNull();
  }

  @Test
  void bindBlockersCoverEveryPrecondition() {
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
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
    IronGolem neighbour = golem(new AtomicReference<>(at(2, 64, 2)));
    registry.bind(owner, neighbour, GolemBody.IRON, GolemCoreType.BLANK);
    assertThat(registry.bindBlock(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).contains(GolemRegistry.BindBlock.CHUNK_CAP);
    assertThatThrownBy(() -> registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).isInstanceOf(IllegalStateException.class);

    settings.set(GolemSettings.defaults());
    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    assertThat(registry.bindBlock(owner, golem, GolemBody.IRON, GolemCoreType.BLANK)).contains(GolemRegistry.BindBlock.ALREADY_BOUND);
  }

  @Test
  void releaseDropsTheLoadoutAndRevertsToVanilla() {
    GolemRoster roster = onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 2)));
    handle.setHostileDamagePool(9D);

    assertThat(registry.release(handle)).isTrue();

    verify(world).dropItemNaturally(any(Location.class), eq(coreItem));
    verify(world).dropItemNaturally(any(Location.class), eq(runeItem));
    assertThat(GolemState.has(golem)).isFalse();
    assertThat(roster.golem(handle.id())).isEmpty();
    assertThat(registry.handle(golem)).isEmpty();
    assertThat(registry.loadedOf(OWNER)).isEmpty();
    assertThat(registry.activeCount()).isZero();
    assertThat(StackExclusion.isExcluded(golem)).isFalse();
    assertThat(golem.getPersistentDataContainer().get(HostileDamagePool.POOL_KEY, PersistentDataType.DOUBLE)).isEqualTo(9D);
    verify(golem).customName(isNull());
    assertThat(platform.scale(golem)).isEmpty();
    assertThat(handle.isRetired()).isTrue();
  }

  @Test
  void releasingAGolemMidResizeLeavesNoSizeModifierBehind() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    handle.setColossus(true);
    platform.scale(golem).add(new AttributeModifier(GolemScale.RITUAL_KEY, -0.3D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));

    assertThat(registry.release(handle)).isTrue();

    assertThat(platform.scale(golem)).isEmpty();
    assertThat(GolemState.has(golem)).isFalse();
  }

  @Test
  void aBoundGolemLoadedWithAStaleResizeStepKeepsOnlyItsSettledScale() {
    GolemRoster roster = onlineOwner();
    IronGolem golem = activeGolem(roster, at(0, 64, 0));
    AttributeModifier settled = new AttributeModifier(GolemScale.MODIFIER_KEY, -0.64D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY);
    platform.scale(golem).add(settled);
    platform.scale(golem).add(new AttributeModifier(GolemScale.RITUAL_KEY, 0.3D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    assertThat(registry.handle(golem)).isPresent();
    assertThat(platform.scale(golem)).containsExactly(settled);
  }

  @Test
  void aColossusGolemAdoptedWithNoRegisteredFormReturnsToItsBoundSize() {
    GolemRoster roster = onlineOwner();
    IronGolem golem = activeGolem(roster, at(0, 64, 0));
    GolemState grown = GolemState.read(golem).orElseThrow();
    grown.setColossus(true);
    grown.write(golem);

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    GolemHandle handle = registry.handle(golem).orElseThrow();
    assertThat(handle.state().colossus()).isFalse();
    assertThat(GolemState.read(golem).orElseThrow().colossus()).isFalse();
    assertThat(platform.scale(golem)).singleElement().satisfies(modifier -> assertThat(modifier.getAmount()).isCloseTo(-0.64D, offset(1e-9)));
  }

  @Test
  void bindingAppliesTheStatSourcesAndReleaseRemovesTheirModifiers() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    AttributeModifier foreign = new AttributeModifier(new NamespacedKey("other", "plating"), 1D, AttributeModifier.Operation.ADD_NUMBER);
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    AtomicReference<Double> level = new AtomicReference<>(2D);
    GolemStatSource frame = statSource("iron_frame", level);
    GolemStats.register(frame);
    try {
      registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
      registry.on(new PlayerQuitEvent(owner, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
      platform.quit(OWNER);
      level.set(6D);

      assertThat(armor.value()).isEqualTo(2D);

      platform.join(OWNER, roster);
      registry.on(new PlayerJoinEvent(owner, (Component) null));

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
    IronGolem golem = persistedGolem(id, true, at(0, 64, 0));
    FakeAttribute armor = platform.stat(golem, GolemStat.ARMOR, 0D);
    AttributeModifier persisted = new AttributeModifier(GolemStats.key("iron_frame", GolemStat.ARMOR), 2D, AttributeModifier.Operation.ADD_NUMBER);
    armor.modifiers.add(persisted);
    GolemStatSource frame = statSource("iron_frame", new AtomicReference<>(2D));
    GolemStats.register(frame);
    try {
      registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

      assertThat(armor.modifiers).containsExactly(persisted);
    } finally {
      GolemStats.unregister(frame);
    }
  }

  @Test
  void onlyAnEntitysFirstBindIsNotPreviouslyBound() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle first = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    registry.release(first);

    GolemHandle second = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    assertThat(first.state().previouslyBound()).isFalse();
    assertThat(second.state().previouslyBound()).isTrue();
    assertThat(second.id()).isNotEqualTo(first.id());
  }

  @Test
  void golemLoadingWhileItsOwnerIsOfflineStaysDormant() {
    IronGolem golem = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    GolemHandle handle = registry.handle(golem).orElseThrow();
    assertThat(handle.dormancyReason()).isEqualTo(DormancyReason.OWNER_OFFLINE);
    verify(golem).setAware(false);
    verify(golem).setTarget(null);
    verify(golem).setInvulnerable(true);
    assertThat(GolemState.read(golem).orElseThrow().dormant()).isTrue();
    assertThat(registry.activeCount()).isZero();
  }

  @Test
  void dormantGolemsAreNotInvulnerableWhenConfiguredOff() {
    settings.set(new GolemSettings(false, false, 1500, 8, 32, Map.of(), Map.of(), Map.of(), 0.5D, 0.5D));
    IronGolem golem = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    verify(golem).setInvulnerable(false);
  }

  @Test
  void golemLoadingWithAnActiveEntryWakesAndUpdatesTheRoster() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(9, 9), 5L));
    IronGolem golem = persistedGolem(id, true, at(33, 64, 1));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

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
    IronGolem unknown = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));
    IronGolem duplicateOfPacked = persistedGolem(packed, false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(unknown, duplicateOfPacked)));

    assertThat(GolemState.has(unknown)).isFalse();
    assertThat(GolemState.has(duplicateOfPacked)).isFalse();
    assertThat(registry.handle(unknown)).isEmpty();
    assertThat(registry.handle(duplicateOfPacked)).isEmpty();
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    assertThat(roster.golem(packed).orElseThrow().status()).isEqualTo(GolemRoster.Status.PACKED);
  }

  @Test
  void missingEntriesAreRestoredWhenTheGolemTurnsUp() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(id, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    IronGolem golem = persistedGolem(id, false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(registry.handle(golem).orElseThrow().isDormant()).isFalse();
    assertThat(platform.changes(OWNER)).isPositive();
  }

  @Test
  void unknownOwnerDataStaysDormantUntilTheRosterArrives() {
    platform.online.add(OWNER);
    UUID id = UUID.randomUUID();
    IronGolem golem = persistedGolem(id, false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    assertThat(registry.handle(golem).orElseThrow().dormancyReason()).isEqualTo(DormancyReason.OWNER_UNKNOWN);
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    registry.on(new PlayerQuitEvent(owner, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
    platform.quit(OWNER);

    assertThat(handle.dormancyReason()).isEqualTo(DormancyReason.OWNER_OFFLINE);
    verify(golem).setAware(false);
    assertThat(GolemState.read(golem).orElseThrow().dormant()).isTrue();
    assertThat(registry.activeCount()).isZero();

    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner, (Component) null));

    assertThat(handle.isDormant()).isFalse();
    verify(golem, atLeastOnce()).setAware(true);
    assertThat(GolemState.read(golem).orElseThrow().dormant()).isFalse();
    assertThat(registry.activeCount()).isEqualTo(1);
  }

  @Test
  void offlineGuardiansStayAwakeWhenConfigured() {
    settings.set(new GolemSettings(true, true, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 0.5D, 0.5D));
    onlineOwner();
    IronGolem guardian = golem(new AtomicReference<>(at(0, 64, 0)));
    IronGolem companion = golem(new AtomicReference<>(at(1, 64, 0)));
    GolemHandle guard = registry.bind(owner, guardian, GolemBody.IRON, GolemCoreType.GUARDIAN);
    GolemHandle follower = registry.bind(owner, companion, GolemBody.IRON, GolemCoreType.BLANK);

    registry.on(new PlayerQuitEvent(owner, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));

    assertThat(guard.isDormant()).isFalse();
    assertThat(follower.dormancyReason()).isEqualTo(DormancyReason.OWNER_OFFLINE);
  }

  @Test
  void serverCapHoldsExtraGolemsDormantUntilRoomFrees() {
    settings.set(withCaps(32, 8, 1));
    GolemRoster roster = onlineOwner();
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(firstId, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.add(new GolemRoster.Binding(secondId, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    IronGolem first = persistedGolem(firstId, false, at(0, 64, 0));
    IronGolem second = persistedGolem(secondId, false, at(1, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(first, second)));

    assertThat(registry.handle(first).orElseThrow().isDormant()).isFalse();
    assertThat(registry.handle(second).orElseThrow().dormancyReason()).isEqualTo(DormancyReason.SERVER_CAP);
    assertThat(registry.activeCount()).isEqualTo(1);

    registry.on(new EntitiesUnloadEvent(mock(Chunk.class), List.of(first)));
    registry.onTick();

    assertThat(registry.handle(second).orElseThrow().isDormant()).isFalse();
    assertThat(registry.activeCount()).isEqualTo(1);
  }

  @Test
  void deathDropsTheRolledLoadoutAndRemovesTheEntry() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 1D, 0D));
    GolemRoster roster = onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 2)));
    List<ItemStack> drops = new ArrayList<>();
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.getEntity()).thenReturn(golem);
    when(death.getDrops()).thenReturn(drops);

    registry.on(death);

    assertThat(drops).containsExactly(coreItem);
    assertThat(roster.golem(handle.id())).isEmpty();
    assertThat(registry.handle(golem)).isEmpty();
    assertThat(registry.activeCount()).isZero();
  }

  @Test
  void aDyingGolemDropsItsStoredEssenceAsExperience() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(140.6D);
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.getEntity()).thenReturn(golem);
    when(death.getDrops()).thenReturn(new ArrayList<>());
    when(death.getDroppedExp()).thenReturn(5);

    registry.on(death);

    verify(death).setDroppedExp(145);
  }

  @Test
  void releasingAGolemGivesItsStoredEssenceToTheOnlineOwner() {
    onlineOwner();
    platform.players.put(OWNER, owner);
    when(owner.isOnline()).thenReturn(true);
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(60.7D);

    assertThat(registry.release(handle)).isTrue();

    verify(owner).giveExp(60);
    verify(world, never()).spawn(any(Location.class), eq(ExperienceOrb.class), ArgumentMatchers.<Consumer<ExperienceOrb>>any());
  }

  @Test
  void releasingAGolemWhileItsOwnerIsAwayDropsTheEssenceAsAnExperienceOrb() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(60D);
    ArgumentCaptor<Consumer<ExperienceOrb>> spawn = consumerCaptor();

    assertThat(registry.release(handle)).isTrue();

    verify(world).spawn(any(Location.class), eq(ExperienceOrb.class), spawn.capture());
    ExperienceOrb orb = mock(ExperienceOrb.class);
    spawn.getValue().accept(orb);
    verify(orb).setExperience(60);
    verify(owner, never()).giveExp(anyInt());
  }

  @Test
  void aRefusedGiftThatAlsoRetiresDropsTheReleasedEssenceOnlyOnce() {
    onlineOwner();
    platform.players.put(OWNER, owner);
    when(owner.isOnline()).thenReturn(true);
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(60D);
    platform.refuseRetiring = true;

    assertThat(registry.release(handle)).isTrue();

    verify(world, times(1)).spawn(any(Location.class), eq(ExperienceOrb.class), ArgumentMatchers.<Consumer<ExperienceOrb>>any());
    verify(owner, never()).giveExp(anyInt());
  }

  @Test
  void anOwnerWhoLeavesWhileTheGolemRefusesTheDropStillGetsTheEssenceDroppedOnceAtTheGolemsLastLocation() {
    GolemHandle handle = releasableGatherer(60D);
    Entity golem = handle.entity();
    platform.deferring.add(owner);

    assertThat(registry.release(handle)).isTrue();
    platform.refusing.add(golem);
    platform.retireDelayed();

    assertThat(platform.regionTasks).extracting(FakeGolemPlatform.RegionTask::location).containsExactly(at(0, 64, 0));
    verify(world, never()).spawn(any(Location.class), eq(ExperienceOrb.class), ArgumentMatchers.<Consumer<ExperienceOrb>>any());
    platform.runRegionTasks();

    assertDroppedOnceAt(at(0, 64, 0), 60);
  }

  @Test
  void anOwnerWhoLeavesWhileTheGolemRetiresBeforeTheDropStillGetsTheEssenceDroppedOnceAtTheGolemsLastLocation() {
    GolemHandle handle = releasableGatherer(60D);
    Entity golem = handle.entity();
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
    Entity golem = handle.entity();
    platform.deferring.add(owner);

    assertThat(registry.release(handle)).isTrue();
    platform.refusing.add(golem);
    platform.refuseRegions = true;
    try (MockedStatic<Adapt> adapt = mockStatic(Adapt.class)) {
      platform.retireDelayed();

      adapt.verify(() -> Adapt.warn(contains("60 experience points")), times(1));
    }
    verify(world, never()).spawn(any(Location.class), eq(ExperienceOrb.class), ArgumentMatchers.<Consumer<ExperienceOrb>>any());
    verify(owner, never()).giveExp(anyInt());
  }

  @Test
  void aGolemWithNoEssenceDropsNoExtraExperience() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);

    assertThat(registry.release(handle)).isTrue();

    verify(world, never()).spawn(any(Location.class), eq(ExperienceOrb.class), ArgumentMatchers.<Consumer<ExperienceOrb>>any());
  }

  @Test
  void statsAndPoolWaitForTheDebouncedFlush() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.state().setCarried(List.of("first", "second", "second", "unreadable"));

    assertThat(handle.removeCarried("second")).isTrue();
    assertThat(handle.removeCarried("absent")).isFalse();

    assertThat(GolemState.read(golem).orElseThrow().carried()).containsExactly("first", "second", "unreadable");
  }

  @Test
  void anUnloadedHandleNeverHandsOutCarriedEntries() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.state().setCarried(List.of("first"));
    handle.flush();
    registry.on(new EntitiesUnloadEvent(mock(Chunk.class), List.of(golem)));

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
    IronGolem golem = persistedGolem(missing, false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    assertThat(GolemState.has(golem)).isFalse();
    assertThat(registry.handle(golem)).isEmpty();
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }

  @Test
  void unloadFlushesPendingStatsAndRemembersWhereTheGolemWent() {
    GolemRoster roster = onlineOwner();
    AtomicReference<Location> location = new AtomicReference<>(at(0, 64, 0));
    IronGolem golem = golem(location);
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.addKills(3);
    location.set(at(40, 70, -40));
    platform.now += 60_000L;

    registry.on(new EntitiesUnloadEvent(mock(Chunk.class), List.of(golem)));

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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    handle.addItemsMoved(64);

    registry.on(new WorldSaveEvent(world));

    assertThat(GolemState.read(golem).orElseThrow().stats().itemsMoved()).isEqualTo(64L);
  }

  @Test
  void entriesWhoseChunkLoadsWithoutTheGolemBecomeMissing() {
    GolemRoster roster = onlineOwner();
    UUID absent = UUID.randomUUID();
    UUID arriving = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(absent, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(5, 5), 0L));
    roster.add(new GolemRoster.Binding(arriving, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(5, 5), 0L));
    registry.on(new PlayerJoinEvent(owner, (Component) null));

    registry.on(new ChunkLoadEvent(chunkAt(5, 5), false));
    platform.now += 2_000L;
    registry.on(new EntitiesLoadEvent(chunkAt(5, 5), List.of(persistedGolem(arriving, false, at(80, 64, 80)))));
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

    registry.on(new PlayerJoinEvent(owner, (Component) null));
    platform.now += GolemMissingWatch.GRACE_MILLIS;
    registry.onTick();

    assertThat(roster.golem(absent).orElseThrow().status()).isEqualTo(GolemRoster.Status.MISSING);
  }

  @Test
  void aSecondLoadedCopyOfAGolemReverts() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    IronGolem original = persistedGolem(id, false, at(0, 64, 0));
    IronGolem copy = persistedGolem(id, false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(original)));
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(copy)));

    assertThat(GolemState.has(original)).isTrue();
    assertThat(registry.handle(original)).isPresent();
    assertThat(GolemState.has(copy)).isFalse();
    assertThat(registry.handle(copy)).isEmpty();
  }

  @Test
  void shutdownFlushesEveryLoadedGolem() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
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
    IronGolem golem = persistedGolem(id, false, at(0, 64, 0));
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));
    GolemHandle handle = registry.handle(golem).orElseThrow();

    assertThat(handle.isValidated()).isFalse();
    assertThat(registry.release(handle)).isFalse();

    platform.online.add(OWNER);
    registry.on(new PlayerJoinEvent(owner, (Component) null));
    assertThat(handle.isValidated()).isFalse();
    assertThat(registry.release(handle)).isFalse();

    assertThat(GolemState.has(golem)).isTrue();
    assertThat(registry.handle(golem)).contains(handle);
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }

  @Test
  void releaseIsRefusedWhenTheRosterEntryIsNotActive() {
    GolemRoster roster = onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    roster.transition(handle.id(), GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);

    assertThat(registry.release(handle)).isFalse();

    assertThat(GolemState.has(golem)).isTrue();
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }

  @Test
  void quitClearsValidationAndJoinRestoresIt() {
    GolemRoster roster = onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    assertThat(handle.isValidated()).isTrue();

    registry.on(new PlayerQuitEvent(owner, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
    assertThat(handle.isValidated()).isFalse();
    assertThat(roster.golem(handle.id()).orElseThrow().status()).isEqualTo(GolemRoster.Status.ACTIVE);
    assertThat(registry.release(handle)).isFalse();
    assertThat(GolemState.has(golem)).isTrue();
    platform.quit(OWNER);

    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner, (Component) null));
    assertThat(handle.isValidated()).isTrue();
  }

  @Test
  void unvalidatedGolemsDieWithVanillaLootOnlyAndLeaveTheEntryToGoMissing() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 1D, 1D));
    GolemRoster roster = new GolemRoster();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, GolemRoster.chunkKey(0, 0), 0L));
    IronGolem golem = persistedGolem(id, false, at(0, 64, 0));
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));
    List<ItemStack> drops = new ArrayList<>();
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.getEntity()).thenReturn(golem);
    when(death.getDrops()).thenReturn(drops);

    registry.on(death);

    assertThat(drops).isEmpty();
    assertThat(registry.handle(golem)).isEmpty();
    platform.join(OWNER, roster);
    platform.loadedChunks.add(WORLD + ":" + GolemRoster.chunkKey(0, 0));
    registry.on(new PlayerJoinEvent(owner, (Component) null));
    platform.now += GolemMissingWatch.GRACE_MILLIS;
    registry.onTick();
    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.MISSING);
  }

  @Test
  void entitiesAddedToAWorldAreAdoptedOneTickLaterAndOnlyOnce() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    IronGolem golem = persistedGolem(id, false, at(0, 64, 0));
    GolemRegistry.PaperEntityListener listener = companion(GolemRegistry.PaperEntityListener.class);

    listener.on(new EntityAddToWorldEvent(golem, world));
    listener.on(new EntityAddToWorldEvent(golem, world));

    assertThat(registry.handle(golem)).isEmpty();
    assertThat(platform.delayed).hasSize(2).allSatisfy(task -> assertThat(task.delayTicks()).isEqualTo(1));

    platform.runDelayed();
    GolemHandle handle = registry.handle(golem).orElseThrow();
    assertThat(handle.isValidated()).isTrue();

    listener.on(new EntityAddToWorldEvent(golem, world));
    registry.adopt(golem);
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    assertThat(platform.delayed).isEmpty();
    assertThat(registry.handle(golem)).containsSame(handle);
    assertThat(registry.loadedOf(OWNER)).containsExactly(handle);
    assertThat(registry.activeCount()).isEqualTo(1);
  }

  @Test
  void companionsGuardBoundGolemsAndBlockCopperTransport() {
    assertThat(registry.createCompanionListeners())
        .hasAtLeastOneElementOfType(GolemGuardListener.class)
        .hasAtLeastOneElementOfType(CopperTransportGuard.class)
        .hasAtLeastOneElementOfType(GolemRegistry.PaperEntityListener.class);
  }

  @Test
  void serverCapWakesQueuedGolemsInArrivalOrder() {
    settings.set(withCaps(32, 8, 1));
    GolemRoster roster = onlineOwner();
    IronGolem first = activeGolem(roster, at(0, 64, 0));
    IronGolem second = activeGolem(roster, at(1, 64, 0));
    IronGolem third = activeGolem(roster, at(2, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(first)));
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(second)));
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(third)));
    registry.on(new EntitiesUnloadEvent(mock(Chunk.class), List.of(first)));
    registry.onTick();

    assertThat(registry.handle(second).orElseThrow().isDormant()).isFalse();
    assertThat(registry.handle(third).orElseThrow().dormancyReason()).isEqualTo(DormancyReason.SERVER_CAP);
  }

  @Test
  void newcomersCannotJumpTheServerCapQueue() {
    settings.set(withCaps(32, 8, 1));
    GolemRoster roster = onlineOwner();
    IronGolem first = activeGolem(roster, at(0, 64, 0));
    IronGolem queued = activeGolem(roster, at(1, 64, 0));
    IronGolem newcomer = activeGolem(roster, at(2, 64, 0));
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(first, queued)));
    registry.on(new EntitiesUnloadEvent(mock(Chunk.class), List.of(first)));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(newcomer)));

    assertThat(registry.handle(newcomer).orElseThrow().dormancyReason()).isEqualTo(DormancyReason.SERVER_CAP);
    registry.onTick();
    assertThat(registry.handle(queued).orElseThrow().isDormant()).isFalse();
    assertThat(registry.handle(newcomer).orElseThrow().dormancyReason()).isEqualTo(DormancyReason.SERVER_CAP);
  }

  @Test
  void loweringTheServerCapSleepsTheNewestAwakeGolems() {
    GolemRoster roster = onlineOwner();
    IronGolem oldest = activeGolem(roster, at(0, 64, 0));
    IronGolem middle = activeGolem(roster, at(1, 64, 0));
    IronGolem newest = activeGolem(roster, at(2, 64, 0));
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(oldest, middle, newest)));
    assertThat(registry.activeCount()).isEqualTo(3);

    settings.set(withCaps(32, 8, 1));
    registry.enforceCap();

    assertThat(registry.activeCount()).isEqualTo(1);
    assertThat(registry.handle(oldest).orElseThrow().isDormant()).isFalse();
    assertThat(registry.handle(middle).orElseThrow().dormancyReason()).isEqualTo(DormancyReason.SERVER_CAP);
    assertThat(registry.handle(newest).orElseThrow().dormancyReason()).isEqualTo(DormancyReason.SERVER_CAP);
    verify(newest).setAware(false);

    settings.set(GolemSettings.defaults());
    registry.onTick();
    assertThat(registry.activeCount()).isEqualTo(3);
  }

  @Test
  void bindAndReleaseRequireTheGolemsOwningThread() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
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
    AtomicReference<Location> location = new AtomicReference<>(at(0, 64, 0));
    IronGolem golem = golem(location);
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    location.set(at(40, 64, 40));
    handle.setMode(GolemMode.STAY);
    location.set(at(200, 64, 200));
    platform.ownsEntities = false;
    platform.now += 10_000L;

    registry.on(new PlayerQuitEvent(owner, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));

    GolemRoster.GolemEntry entry = roster.golem(handle.id()).orElseThrow();
    assertThat(entry.chunkKey()).isEqualTo(GolemRoster.chunkKey(2, 2));
    assertThat(entry.lastSeen()).isEqualTo(platform.now);
    assertThat(handle.isValidated()).isFalse();
    assertThat(handle.isDormant()).isFalse();

    platform.quit(OWNER);
    platform.runDelayed();
    assertThat(handle.dormancyReason()).isEqualTo(DormancyReason.OWNER_OFFLINE);
  }

  @Test
  void startupSweepsAdoptLoadedGolemsOfOnlineOwners() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    long chunk = GolemRoster.chunkKey(1, 1);
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, WORLD, chunk, 0L));
    IronGolem golem = persistedGolem(id, true, at(20, 64, 20));
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    golem.getPersistentDataContainer().set(GolemRitualMarker.KEY, PersistentDataType.BYTE, (byte) 1);
    platform.scale(golem).add(new AttributeModifier(GolemScale.RITUAL_KEY, -0.3D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

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
    IronGolem stranded = golem(new AtomicReference<>(at(20, 64, 20)));
    stranded.getPersistentDataContainer().set(GolemRitualMarker.KEY, PersistentDataType.BYTE, (byte) 0);
    platform.scale(stranded).add(new AttributeModifier(GolemScale.RITUAL_KEY, -0.3D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    platform.loadChunk(WORLD, chunk, stranded);

    registry.joinOnlineOwners();

    verify(stranded).setAware(false);
    assertThat(platform.scale(stranded)).isEmpty();
    assertThat(GolemRitualMarker.has(stranded)).isFalse();
  }

  @Test
  void handlesOfSilentlyRemovedEntitiesArePruned() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    when(golem.isValid()).thenReturn(false);

    registry.onTick();

    assertThat(registry.handle(golem)).isEmpty();
    assertThat(registry.activeCount()).isZero();
  }

  @Test
  void worldSaveFlushesOnlyThatWorldsGolems() {
    GolemRoster roster = onlineOwner();
    World nether = mock(World.class);
    UUID netherId = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    lenient().when(nether.getUID()).thenReturn(netherId);
    IronGolem here = activeGolem(roster, at(0, 64, 0));
    IronGolem there = activeGolem(roster, new Location(nether, 0.5D, 64D, 0.5D));
    lenient().when(there.getWorld()).thenReturn(nether);
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(here, there)));
    registry.handle(here).orElseThrow().addKills(1);
    registry.handle(there).orElseThrow().addKills(1);

    registry.on(new WorldSaveEvent(world));

    assertThat(GolemState.read(here).orElseThrow().stats().kills()).isEqualTo(1L);
    assertThat(GolemState.read(there).orElseThrow().stats().kills()).isZero();
  }

  @Test
  void bindWakesTheGolemBrain() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));

    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);

    assertThat(lifecycle.events).containsExactly(new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.AWAKE, handle));
  }

  @Test
  void dormancySleepsTheGolemBrainAndWakingStartsItAgain() {
    GolemRoster roster = onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    lifecycle.events.clear();

    registry.on(new PlayerQuitEvent(owner, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
    platform.quit(OWNER);
    platform.join(OWNER, roster);
    registry.on(new PlayerJoinEvent(owner, (Component) null));

    assertThat(lifecycle.events).containsExactly(
        new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.ASLEEP, handle),
        new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.AWAKE, handle));
  }

  @Test
  void golemLoadingWhileItsOwnerIsOfflineNeverWakesABrain() {
    IronGolem golem = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    assertThat(lifecycle.kinds()).containsExactly(RecordingGolemLifecycle.Kind.ASLEEP);
  }

  @Test
  void unloadDetachesTheGolemBrain() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    lifecycle.events.clear();

    registry.on(new EntitiesUnloadEvent(mock(Chunk.class), List.of(golem)));

    assertThat(lifecycle.events).containsExactly(new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.DETACHED, handle));
  }

  @Test
  void releaseDetachesTheGolemBrainWhileTheGolemIsStillBound() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    lifecycle.events.clear();

    assertThat(registry.release(handle)).isTrue();

    assertThat(lifecycle.events).containsExactly(new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.DETACHED, handle));
    assertThat(lifecycle.boundOnDetach).containsExactly(true);
  }

  @Test
  void deathDetachesTheGolemBrain() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    lifecycle.events.clear();
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.getEntity()).thenReturn(golem);
    when(death.getDrops()).thenReturn(new ArrayList<>());

    registry.on(death);

    assertThat(lifecycle.events).containsExactly(new RecordingGolemLifecycle.Event(RecordingGolemLifecycle.Kind.DETACHED, handle));
  }

  @Test
  void shutdownSleepsAndDetachesEveryGolemBrain() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
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
    IronGolem unknown = persistedGolem(UUID.randomUUID(), false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(unknown)));

    assertThat(lifecycle.kinds()).containsExactly(RecordingGolemLifecycle.Kind.DETACHED);
    assertThat(roster.golems()).isEmpty();
  }

  @Test
  void aShatteringDeathDropsCargoAndEssenceAndLeavesAShatteredCoreInsteadOfRollingTheLoadout() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 1D, 1D));
    GolemRoster roster = onlineOwner();
    Location at = at(4, 64, 4);
    IronGolem golem = golem(new AtomicReference<>(at));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 2)));
    handle.setEssence(30D);
    handle.setChannels(0x0003);
    handle.state().setCarried(List.of("cargo"));
    handle.addKills(4L);
    AtomicReference<PackedGolem> packed = new AtomicReference<>();
    ItemStack shatteredCore = mock(ItemStack.class);
    items.when(() -> GolemItems.shatteredCore(any(PackedGolem.class))).thenAnswer(invocation -> {
      packed.set(invocation.getArgument(0));
      return shatteredCore;
    });
    RecordingMender mender = new RecordingMender();
    mender.shatters = true;
    GolemMend.register(mender);
    try {
      List<ItemStack> drops = new ArrayList<>();
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    items.when(() -> GolemItems.shatteredCore(any(PackedGolem.class))).thenReturn(mock(ItemStack.class));
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    RecordingMender mender = new RecordingMender();
    GolemMend.register(mender);
    try {
      List<ItemStack> drops = new ArrayList<>();
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
    IronGolem golem = persistedGolem(id, false, at(0, 64, 0));
    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));
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
    IronGolem golem = persistedGolem(id, false, at(0, 64, 0));

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(golem)));

    assertThat(GolemState.has(golem)).isFalse();
    assertThat(registry.handle(golem)).isEmpty();
    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }

  @Test
  void shatteredEntriesAreReleasableFromTheRoster() {
    GolemRoster roster = onlineOwner();
    UUID shattered = UUID.randomUUID();
    UUID packed = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(shattered, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.add(new GolemRoster.Binding(packed, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(shattered, GolemRoster.Status.ACTIVE, GolemRoster.Status.SHATTERED);
    roster.transition(packed, GolemRoster.Status.ACTIVE, GolemRoster.Status.PACKED);
    int changesBefore = platform.changes(OWNER);

    assertThat(registry.releaseEntry(OWNER, packed)).isFalse();
    assertThat(registry.releaseEntry(OWNER, shattered)).isTrue();

    assertThat(roster.golem(shattered)).isEmpty();
    assertThat(roster.golem(packed)).isPresent();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore + 1);
  }

  @Test
  void restoreRebindsAFreshBodyUnderTheShatteredIdAfterSavingTheRoster() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    Component name = Component.text("Old Clank");
    GolemSnapshot snapshot = snapshot(id, GolemBody.IRON, GolemCoreType.GUARDIAN, GolemSnapshot.serializeName(name));
    PackedGolem item = shattered(roster, snapshot, GolemRoster.chunkKey(9, 9));
    IronGolem fresh = golem(new AtomicReference<>(at(3, 64, -20)));
    List<Boolean> boundAtSave = new ArrayList<>();
    platform.rosterListener = changed -> boundAtSave.add(GolemState.has(fresh));
    int changesBefore = platform.changes(OWNER);

    Optional<GolemHandle> restored = registry.restore(owner, fresh, new GolemRegistry.Restore(item, snapshot, GolemRoster.Status.SHATTERED));

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
    ArgumentCaptor<Component> shownName = ArgumentCaptor.forClass(Component.class);
    verify(fresh).customName(shownName.capture());
    assertThat(shownName.getValue()).isEqualTo(name);
    assertThat(platform.scale(fresh)).singleElement().satisfies(modifier -> assertThat(modifier.getKey()).isEqualTo(GolemScale.MODIFIER_KEY));
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
    IronGolem fresh = golem(new AtomicReference<>(at(0, 64, 0)));

    registry.restore(owner, fresh, new GolemRegistry.Restore(item, snapshot, GolemRoster.Status.SHATTERED));

    ArgumentCaptor<Component> shownName = ArgumentCaptor.forClass(Component.class);
    verify(fresh).customName(shownName.capture());
    assertThat(PlainTextComponentSerializer.plainText().serialize(shownName.getValue())).isEqualTo("Steve's Iron Golem");
  }

  @Test
  void restoreLeavesTheBodyAloneWhenTheEntryIsNotInTheExpectedState() {
    GolemRoster roster = onlineOwner();
    UUID active = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(active, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    GolemSnapshot activeSnapshot = snapshot(active, GolemBody.IRON, GolemCoreType.BLANK, null);
    GolemSnapshot unknown = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    IronGolem fresh = golem(new AtomicReference<>(at(0, 64, 0)));
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
    GolemSnapshot older = GolemSnapshot.capture(GolemState.restored(snapshot, GolemState.Anchor.of(WORLD, 0, 64, 0)),
        new GolemSnapshot.Traits(null, 3D, 0, false), platform.now - 1L);
    PackedGolem staleCopy = new PackedGolem(snapshot.id(), OWNER, older.toJson());
    IronGolem fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    int changesBefore = platform.changes(OWNER);

    assertThat(registry.restore(owner, fresh, new GolemRegistry.Restore(staleCopy, older, GolemRoster.Status.SHATTERED))).isEmpty();
    assertThat(registry.restore(owner, fresh, new GolemRegistry.Restore(item, snapshot, GolemRoster.Status.PACKED))).isEmpty();

    assertThat(GolemState.has(fresh)).isFalse();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);
    GolemRoster.GolemEntry entry = roster.golem(snapshot.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(entry.holds(item.state())).isTrue();
    assertThat(entry.holds(staleCopy.state())).isFalse();
  }

  @Test
  void restoreRefusesASnapshotWhoseBodyOrCoreDiffersFromTheEntry() {
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
    IronGolem fresh = golem(new AtomicReference<>(at(0, 64, 0)));

    assertThat(registry.restore(owner, fresh, new GolemRegistry.Restore(otherCoreItem, otherCore, GolemRoster.Status.SHATTERED))).isEmpty();
    assertThat(registry.restore(owner, fresh, new GolemRegistry.Restore(otherBodyItem, otherBody, GolemRoster.Status.SHATTERED))).isEmpty();

    assertThat(GolemState.has(fresh)).isFalse();
    assertThat(roster.golem(otherCoreId).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(roster.golem(otherBodyId).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
  }

  @Test
  void aFailedRestoreRollsTheRosterBackToTheSealedEntry() {
    GolemRoster roster = onlineOwner();
    GolemSnapshot snapshot = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem item = shattered(roster, snapshot, 0L);
    IronGolem fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    doThrow(new IllegalStateException("persistence refused")).when(fresh).setPersistent(true);
    int changesBefore = platform.changes(OWNER);

    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(item, snapshot, GolemRoster.Status.SHATTERED)))
        .hasMessage("persistence refused");

    assertThat(GolemState.has(fresh)).isFalse();
    assertThat(registry.handle(fresh)).isEmpty();
    GolemRoster.GolemEntry entry = roster.golem(snapshot.id()).orElseThrow();
    assertThat(entry.status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(entry.holds(item.state())).isTrue();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore + 2);
  }

  @Test
  void aRestoreThatFailsAfterAdoptionRevertsTheBodyAndRestoresTheEntry() {
    GolemRoster roster = onlineOwner();
    GolemSnapshot snapshot = snapshot(UUID.randomUUID(), GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem item = shattered(roster, snapshot, 0L);
    IronGolem fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    lifecycle.awakeFailure = new IllegalStateException("brain refused");

    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(item, snapshot, GolemRoster.Status.SHATTERED)))
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
    IronGolem fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    platform.stat(fresh, GolemStat.MAX_HEALTH, 150D);
    GolemHandle handle = registry.restore(owner, fresh, new GolemRegistry.Restore(item, snapshot, GolemRoster.Status.SHATTERED)).orElseThrow();
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
    IronGolem fresh = golem(new AtomicReference<>(at(0, 64, 0)));
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

    IronGolem bound = persistedGolem(UUID.randomUUID(), false, at(40, 64, 40));
    assertThat(registry.restoreBlock(bound, snapshot(id, GolemBody.IRON, GolemCoreType.BLANK, null))).contains(GolemRegistry.BindBlock.ALREADY_BOUND);
  }

  @Test
  void restoreNeedsTheGolemsThreadAndAnOpenBody() {
    GolemRoster roster = onlineOwner();
    UUID id = UUID.randomUUID();
    GolemSnapshot iron = snapshot(id, GolemBody.IRON, GolemCoreType.BLANK, null);
    PackedGolem item = shattered(roster, iron, 0L);
    IronGolem fresh = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemSnapshot frost = snapshot(id, GolemBody.FROST, GolemCoreType.BLANK, null);

    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(item, frost, GolemRoster.Status.SHATTERED)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(new PackedGolem(UUID.randomUUID(), OWNER, item.state()), iron,
        GolemRoster.Status.SHATTERED))).isInstanceOf(IllegalStateException.class);
    platform.ownsEntities = false;
    assertThatThrownBy(() -> registry.restore(owner, fresh, new GolemRegistry.Restore(item, iron, GolemRoster.Status.SHATTERED)))
        .isInstanceOf(IllegalStateException.class);

    assertThat(roster.golem(id).orElseThrow().status()).isEqualTo(GolemRoster.Status.SHATTERED);
    assertThat(GolemState.has(fresh)).isFalse();
  }

  @Test
  void snapshotsReadTheLiveHealthNameAndCopperState() {
    onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    when(golem.getHealth()).thenReturn(33D);
    when(golem.customName()).thenReturn(Component.text("Plate"));
    platform.patina.put(golem.getUniqueId(), 2);
    platform.waxedGolems.add(golem.getUniqueId());

    GolemSnapshot snapshot = registry.snapshot(handle);

    assertThat(snapshot.id()).isEqualTo(handle.id());
    assertThat(snapshot.health()).isEqualTo(33D);
    assertThat(snapshot.name()).contains(Component.text("Plate"));
    assertThat(snapshot.copperStage()).isEqualTo(2);
    assertThat(snapshot.waxed()).isTrue();
    assertThat(snapshot.takenAt()).isEqualTo(platform.now);
  }

  @Test
  void designatingAMasterworkSavesTheRosterMarkBeforeTheGolemChanges() {
    GolemRoster roster = onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
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
  void designationIsRefusedForBoneBodiesTwiceWithoutTheLevelOrWithoutTheRoster() {
    GolemRoster roster = onlineOwner();
    GolemHandle handle = registry.bind(owner, golem(new AtomicReference<>(at(0, 64, 0))), GolemBody.IRON, GolemCoreType.GUARDIAN);
    Skeleton skeleton = GolemTestEntities.entity(Skeleton.class);
    GolemHandle bone = new GolemHandle(skeleton, GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.BONE, GolemCoreType.GUARDIAN,
        GolemState.Anchor.of(WORLD, 0, 64, 0))), platform);

    assertThat(registry.designate(bone, 1)).isEqualTo(GolemRegistry.Designation.BODY);
    assertThat(registry.designate(handle, 0)).isEqualTo(GolemRegistry.Designation.LOCKED);
    platform.rosters.remove(OWNER);
    assertThat(registry.designate(handle, 1)).isEqualTo(GolemRegistry.Designation.UNAVAILABLE);
    platform.rosters.put(OWNER, roster);
    assertThat(registry.designate(handle, 1)).isEqualTo(GolemRegistry.Designation.DESIGNATED);
    assertThat(registry.designate(handle, 2)).isEqualTo(GolemRegistry.Designation.ALREADY);
    assertThat(roster.masterworkCount()).isEqualTo(1);
  }

  @Test
  void removingTheDesignationClearsTheGolemBeforeReturningTheHeartTheSecondCoreAndTheExtraRunes() {
    GolemRoster roster = onlineOwner();
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    registry.designate(handle, 1);
    ItemStack heart = mock(ItemStack.class);
    ItemStack lumber = mock(ItemStack.class);
    ItemStack bulwark = mock(ItemStack.class);
    ItemStack tide = mock(ItemStack.class);
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

    Optional<List<ItemStack>> returned = registry.undesignate(handle, 2);

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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.BLANK);
    items.when(GolemItems::masterworkHeart).thenReturn(mock(ItemStack.class));
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    ItemStack heart = mock(ItemStack.class);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    registry.designate(handle, 1);

    assertThat(registry.release(handle)).isTrue();

    verify(world).dropItemNaturally(any(Location.class), eq(heart));
    verify(world).dropItemNaturally(any(Location.class), eq(coreItem));
  }

  @Test
  void aDyingMasterworkDropsItsHeartAtTheHeartChance() {
    settings.set(new GolemSettings(true, false, 1500, 8, 32, Map.of(), Map.of(), GolemSettings.defaults().allowedCores(), 0D, 0D));
    GolemRoster roster = onlineOwner();
    ItemStack heart = mock(ItemStack.class);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    FakeMastery mastery = new FakeMastery();
    GolemMastery.register(mastery);
    try {
      IronGolem lucky = golem(new AtomicReference<>(at(0, 64, 0)));
      GolemHandle first = registry.bind(owner, lucky, GolemBody.IRON, GolemCoreType.GUARDIAN);
      registry.designate(first, 2);
      List<ItemStack> kept = new ArrayList<>();
      registry.on(death(lucky, kept, 0));

      mastery.heartDropChance = 0D;
      IronGolem unlucky = golem(new AtomicReference<>(at(2, 64, 0)));
      GolemHandle second = registry.bind(owner, unlucky, GolemBody.IRON, GolemCoreType.GUARDIAN);
      registry.designate(second, 2);
      List<ItemStack> lost = new ArrayList<>();
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GUARDIAN);
    ItemStack heart = mock(ItemStack.class);
    items.when(GolemItems::masterworkHeart).thenReturn(heart);
    AtomicReference<PackedGolem> packed = new AtomicReference<>();
    items.when(() -> GolemItems.shatteredCore(any(PackedGolem.class))).thenAnswer(invocation -> {
      packed.set(invocation.getArgument(0));
      return mock(ItemStack.class);
    });
    registry.designate(handle, 1);
    RecordingMender mender = new RecordingMender();
    mender.shatters = true;
    GolemMend.register(mender);
    try {
      List<ItemStack> drops = new ArrayList<>();
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
  void aSightingCorrectsTheRosterMarkFromTheGolemsOwnState() {
    GolemRoster roster = onlineOwner();
    UUID stale = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(stale, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.markMasterwork(stale, true);
    IronGolem plain = persistedGolem(stale, false, at(0, 64, 0));
    UUID missed = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(missed, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    IronGolem masterwork = persistedGolem(missed, false, at(2, 64, 0));
    GolemState designated = GolemState.read(masterwork).orElseThrow();
    designated.setMasterwork(true);
    designated.write(masterwork);

    registry.on(new EntitiesLoadEvent(mock(Chunk.class), List.of(plain, masterwork)));

    assertThat(roster.golem(stale).orElseThrow().masterwork()).isFalse();
    assertThat(roster.golem(missed).orElseThrow().masterwork()).isTrue();
  }

  private <T extends Listener> T companion(Class<T> type) {
    for (Listener listener : registry.createCompanionListeners()) {
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
    IronGolem golem = golem(new AtomicReference<>(at(0, 64, 0)));
    GolemHandle handle = registry.bind(owner, golem, GolemBody.IRON, GolemCoreType.GATHERER);
    handle.setEssence(essence);
    return handle;
  }

  private EntityDeathEvent death(IronGolem golem, List<ItemStack> drops, int experience) {
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    lenient().when(death.getEntity()).thenReturn(golem);
    lenient().when(death.getDrops()).thenReturn(drops);
    lenient().when(death.getDroppedExp()).thenReturn(experience);
    return death;
  }

  private GolemSnapshot snapshot(UUID id, GolemBody body, GolemCoreType core, String name) {
    GolemState state = GolemState.create(new GolemState.Seed(id, OWNER, body, core, GolemState.Anchor.of(WORLD, 5, 64, 5)));
    state.setRunes(List.of(new GolemRune(GolemRuneType.BULWARK, 3)));
    state.stats().addKills(12L);
    return GolemSnapshot.capture(state, new GolemSnapshot.Traits(name, 0D, 0, false), platform.now);
  }

  private PackedGolem shattered(GolemRoster roster, GolemSnapshot snapshot, long chunkKey) {
    roster.add(new GolemRoster.Binding(snapshot.id(), snapshot.body(), snapshot.primaryCore(), WORLD, chunkKey, 0L));
    PackedGolem item = new PackedGolem(snapshot.id(), OWNER, snapshot.toJson());
    roster.stow(snapshot.id(), GolemRoster.Status.SHATTERED, item.state());
    return item;
  }

  private static GolemRegistry.Restore restoreOf(GolemSnapshot snapshot) {
    return new GolemRegistry.Restore(new PackedGolem(snapshot.id(), OWNER, snapshot.toJson()), snapshot, GolemRoster.Status.SHATTERED);
  }

  private void assertDroppedOnceAt(Location location, int experience) {
    ArgumentCaptor<Consumer<ExperienceOrb>> spawn = consumerCaptor();
    verify(world, times(1)).spawn(eq(location), eq(ExperienceOrb.class), spawn.capture());
    ExperienceOrb orb = mock(ExperienceOrb.class);
    spawn.getValue().accept(orb);
    verify(orb).setExperience(experience);
    verify(owner, never()).giveExp(anyInt());
  }

  private GolemRoster onlineOwner() {
    GolemRoster roster = platform.rosters.getOrDefault(OWNER, new GolemRoster());
    platform.join(OWNER, roster);
    return roster;
  }

  private IronGolem golem(AtomicReference<Location> location) {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    lenient().when(golem.getLocation()).thenAnswer(invocation -> location.get().clone());
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.isValid()).thenReturn(true);
    track(golem, location.get());
    return golem;
  }

  private IronGolem activeGolem(GolemRoster roster, Location location) {
    UUID id = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(id, GolemBody.IRON, GolemCoreType.BLANK, location.getWorld().getUID(), 0L, 0L));
    return persistedGolem(id, false, location);
  }

  private IronGolem persistedGolem(UUID id, boolean dormant, Location location) {
    IronGolem golem = golem(new AtomicReference<>(location));
    GolemState state = GolemState.create(new GolemState.Seed(id, OWNER, GolemBody.IRON, GolemCoreType.BLANK, GolemState.Anchor.at(location)));
    state.setDormant(dormant);
    state.write(golem);
    return golem;
  }

  private void track(Entity entity, Location location) {
    long key = GolemRoster.chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    chunkEntities.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entity);
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x + 0.5D, y, z + 0.5D);
  }

  private Chunk chunk(Location location) {
    return chunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }

  private Chunk chunkAt(int x, int z) {
    Chunk chunk = mock(Chunk.class);
    long key = GolemRoster.chunkKey(x, z);
    lenient().when(chunk.getX()).thenReturn(x);
    lenient().when(chunk.getZ()).thenReturn(z);
    lenient().when(chunk.getWorld()).thenReturn(world);
    lenient().when(chunk.getEntities()).thenAnswer(invocation -> Optional.ofNullable(chunkEntities.get(key)).orElse(List.of()).toArray(new Entity[0]));
    return chunk;
  }

  private static GolemSettings withCaps(int perPlayer, int perChunk, int server) {
    GolemSettings defaults = GolemSettings.defaults();
    return new GolemSettings(true, false, server, perChunk, perPlayer, defaults.bodyScales(), defaults.bodyCarry(), defaults.allowedCores(), 0.5D, 0.5D);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Consumer<ExperienceOrb>> consumerCaptor() {
    return ArgumentCaptor.forClass((Class<Consumer<ExperienceOrb>>) (Class<?>) Consumer.class);
  }
}
