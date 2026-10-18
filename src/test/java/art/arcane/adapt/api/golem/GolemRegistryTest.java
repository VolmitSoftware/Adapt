package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.item.GolemItems;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
  void releasingAGolemReturnsItsStoredEssenceAsAnExperienceOrb() {
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

    assertThat(registry.releaseMissing(OWNER, active)).isFalse();
    assertThat(registry.releaseMissing(OWNER, UUID.randomUUID())).isFalse();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore);

    assertThat(registry.releaseMissing(OWNER, missing)).isTrue();

    assertThat(roster.golem(missing)).isEmpty();
    assertThat(roster.golem(active)).isPresent();
    assertThat(platform.changes(OWNER)).isEqualTo(changesBefore + 1);
    assertThat(registry.releaseMissing(OWNER, missing)).isFalse();
  }

  @Test
  void releasingAMissingEntryNeedsTheOwnersRoster() {
    GolemRoster roster = onlineOwner();
    UUID missing = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(missing, GolemBody.IRON, GolemCoreType.BLANK, WORLD, 0L, 0L));
    roster.transition(missing, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    platform.quit(OWNER);

    assertThat(registry.releaseMissing(OWNER, missing)).isFalse();
    assertThat(roster.golem(missing)).isPresent();
  }

  @Test
  void aReleasedMissingGolemThatTurnsUpRevertsWithoutDrops() {
    GolemRoster roster = onlineOwner();
    UUID missing = UUID.randomUUID();
    roster.add(new GolemRoster.Binding(missing, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    roster.transition(missing, GolemRoster.Status.ACTIVE, GolemRoster.Status.MISSING);
    registry.releaseMissing(OWNER, missing);
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
