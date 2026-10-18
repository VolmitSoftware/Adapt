package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemPacking;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemSnapshot;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.DormantGolem;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.OwnerBoundDrop;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemStasisTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000c1a1");
  private static final double MAX_HEALTH = 100D;
  private static final long SECOND = 1_000L;

  private final Deque<Runnable> golemTicks = new ArrayDeque<>();
  private final AtomicBoolean playerReachable = new AtomicBoolean(true);
  private GolemStasis adaptation;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<GolemItems> items;
  private MockedStatic<OwnerBoundDrop> drops;
  private World world;
  private Player player;
  private PlayerInventory inventory;
  private ItemStack crook;
  private GolemRegistry registry;
  private ItemStack dormantStack;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    player = mock(Player.class);
    inventory = mock(PlayerInventory.class);
    crook = mock(ItemStack.class);
    lenient().when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(player.getName()).thenReturn("Alex");
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(player.getWorld()).thenReturn(world);
    lenient().when(player.getLocation()).thenReturn(new Location(world, 0.5D, 64D, 0.5D));
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.isValid()).thenReturn(true);
    lenient().when(inventory.getItemInMainHand()).thenReturn(crook);
    lenient().when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
    registry = mock(GolemRegistry.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.empty());
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt())).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      if (invocation.getArgument(0) == player && !playerReachable.get()) {
        return false;
      }
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt(), any(Runnable.class))).thenAnswer(invocation -> {
      golemTicks.add(invocation.getArgument(1));
      return true;
    });
    scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(MAX_HEALTH);
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
    items = mockStatic(GolemItems.class, CALLS_REAL_METHODS);
    dormantStack = mock(ItemStack.class);
    items.when(() -> GolemItems.dormantGolem(any(DormantGolem.class))).thenReturn(dormantStack);
    items.when(() -> GolemItems.kindOf(crook)).thenReturn(Optional.of(GolemItemKind.CROOK));
    drops = mockStatic(OwnerBoundDrop.class);
    adaptation = new GolemStasis();
  }

  @AfterEach
  void close() {
    drops.close();
    items.close();
    bukkit.close();
    vitals.close();
    scheduling.close();
    runtime.close();
  }

  @Test
  void defaultsMatchTheBrief() {
    GolemStasis.Config config = new GolemStasis.Config();

    assertThat(config.channelSecondsByLevel).containsExactly(2D, 0D, 0D);
    assertThat(config.channelAbortDistance).isEqualTo(4D);
    assertThat(config.packedHealLevel).isEqualTo(2);
    assertThat(config.packedHealAmount).isEqualTo(1D);
    assertThat(config.packedHealIntervalSeconds).isEqualTo(10);
    assertThat(config.recallLevel).isEqualTo(3);
    assertThat(config.recallCooldownSeconds).isEqualTo(120);
    assertThat(config.awakenRiseTicks).isEqualTo(20);
    assertThat(config.requires).containsExactly("golem-animus-binding:2");
    assertThat(config.maxLevel).isEqualTo(3);
  }

  @Test
  void configurationLoadsWithThreeLevelsAndRegistersItsPackerOnlyAtRuntime() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-stasis");
    assertThat(adaptation.getMaxLevel()).isEqualTo(3);
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
    }
    assertThat(GolemPacking.packer()).isEmpty();

    adaptation.onRuntimeActivated();
    try {
      assertThat(GolemPacking.packer()).isPresent();
    } finally {
      adaptation.unregister();
    }
    assertThat(GolemPacking.packer()).isEmpty();
  }

  @Test
  void loadingClampsTheChannelDistancesIntervalsCooldownAndRise() {
    String toml = "channelSecondsByLevel = [-2.0, 99.0, 0.5]\nchannelAbortDistance = -1.0\npackedHealAmount = -3.0\n"
        + "packedHealIntervalSeconds = 0\nrecallCooldownSeconds = -5\nawakenRiseTicks = -1\n";
    assertThat(adaptation.reloadConfigSnapshot(toml, dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemStasis.Config config = adaptation.getConfig();
    assertThat(config.channelSecondsByLevel).containsExactly(0D, 30D, 0.5D);
    assertThat(config.channelAbortDistance).isZero();
    assertThat(config.packedHealAmount).isZero();
    assertThat(config.packedHealIntervalSeconds).isEqualTo(1);
    assertThat(config.recallCooldownSeconds).isZero();
    assertThat(config.awakenRiseTicks).isZero();
  }

  @Test
  void everyLevelShowsItsPackingHealingAndRecall() {
    String awaken = "+ Place a Dormant Golem to Awaken It";
    assertThat(lore(1)).containsExactly("* 2 Seconds to Pack a Golem", awaken);
    assertThat(lore(2)).containsExactly("+ Instant Packing", awaken, "+ 1 Health Healed While Packed", "* 10 Seconds Between Packed Heals");
    assertThat(lore(3)).containsExactly("+ Instant Packing", awaken, "+ 1 Health Healed While Packed", "* 10 Seconds Between Packed Heals",
        "+ Pocket Roster: Recall Golems from the Roster", "* 120 Seconds Between Recalls");
  }

  @Test
  void levelOneStartsAChannelWhileHigherLevelsPackAtOnce() {
    Packable packable = packable();
    GolemStasis channeling = learned(1);
    doNothing().when(channeling).channel(any(), any(), anyInt());
    when(registry.pack(packable.handle)).thenReturn(Optional.empty());

    channeling.pack(player, packable.handle);
    verify(channeling).channel(player, packable.handle, 40);
    verify(registry, never()).pack(any());

    GolemStasis instant = learned(2);
    instant.pack(player, packable.handle);
    verify(instant, never()).channel(any(), any(), anyInt());
    verify(registry).pack(packable.handle);
  }

  @Test
  void aPackSealsTheEntryThenGivesTheDormantGolemThenSavesThePlayer() {
    Packable packable = packable();
    GolemStasis learned = learned(2);

    learned.pack(player, packable.handle);

    InOrder order = inOrder(registry, inventory, player);
    order.verify(registry).pack(packable.handle);
    order.verify(inventory).addItem(dormantStack);
    order.verify(player).saveData();
    verify(learned).packFx(packable.packing.at(), GolemBody.IRON);
    ArgumentCaptor<DormantGolem> dormant = ArgumentCaptor.forClass(DormantGolem.class);
    items.verify(() -> GolemItems.dormantGolem(dormant.capture()));
    assertThat(dormant.getValue().packed()).isEqualTo(packable.packing.item());
    assertThat(dormant.getValue().block()).isEqualTo(Material.IRON_BLOCK);
    assertThat(dormant.getValue().health()).isEqualTo(40D);
    assertThat(dormant.getValue().maxHealth()).isEqualTo(MAX_HEALTH);
    drops.verifyNoInteractions();
  }

  @Test
  void aFullInventoryDropsTheDormantGolemBoundToItsOwner() {
    Packable packable = packable();
    Map<Integer, ItemStack> leftover = new HashMap<>();
    leftover.put(0, dormantStack);
    when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(leftover));

    learned(3).pack(player, packable.handle);

    drops.verify(() -> OwnerBoundDrop.drop(player.getLocation(), dormantStack, player.getUniqueId()));
    verify(player).saveData();
  }

  @Test
  void anOwnerWhoLeftGetsTheDormantGolemDroppedWhereTheGolemStood() {
    Packable packable = packable();
    playerReachable.set(false);

    learned(2).pack(player, packable.handle);

    drops.verify(() -> OwnerBoundDrop.drop(packable.packing.at(), dormantStack, player.getUniqueId()), times(1));
    verify(inventory, never()).addItem(any(ItemStack[].class));
  }

  @Test
  void aRefusedPackKeepsTheGolemAndHandsOutNothing() {
    Packable packable = packable();
    when(registry.pack(packable.handle)).thenReturn(Optional.empty());

    learned(2).pack(player, packable.handle);

    verify(inventory, never()).addItem(any(ItemStack[].class));
    verify(player, never()).saveData();
    drops.verifyNoInteractions();
  }

  @Test
  void onlyTheOwnerWithStasisCanPack() {
    Packable packable = packable();
    when(packable.handle.owner()).thenReturn(UUID.randomUUID());

    learned(3).pack(player, packable.handle);
    UUID playerId = player.getUniqueId();
    when(packable.handle.owner()).thenReturn(playerId);
    learned(0).pack(player, packable.handle);

    verify(registry, never()).pack(any());
  }

  @Test
  void theLevelOneChannelPacksAfterItsDuration() {
    Packable packable = packable();
    GolemStasis learned = learned(1);

    learned.pack(player, packable.handle);
    int pulses = drain();

    assertThat(pulses).isEqualTo(40);
    verify(registry).pack(packable.handle);
    verify(player).saveData();
  }

  @Test
  void damageMovingAwayOrSwappingItemsBreaksTheChannel() {
    Packable hurt = packable();
    GolemStasis learned = learned(1);
    learned.pack(player, hurt.handle);
    pulse(5);
    learned.on(new EntityDamageEvent(hurt.golem, EntityDamageEvent.DamageCause.FALL, mockDamageSource(), 1D));
    drain();

    Packable far = packable();
    learned.pack(player, far.handle);
    pulse(5);
    when(far.golem.getLocation()).thenReturn(new Location(world, 9D, 64D, 0.5D));
    drain();

    Packable swapped = packable();
    learned.pack(player, swapped.handle);
    pulse(5);
    when(inventory.getHeldItemSlot()).thenReturn(4);
    drain();

    verify(registry, never()).pack(any());
    verify(player, never()).saveData();
  }

  @Test
  void aSecondPackWhileChannelingIsRefused() {
    Packable first = packable();
    Packable second = packable();
    GolemStasis learned = learned(1);

    learned.pack(player, first.handle);
    learned.pack(player, second.handle);
    drain();

    verify(registry).pack(first.handle);
    verify(registry, never()).pack(second.handle);
  }

  @Test
  void aReadyAwakeningSpawnsThenSetsHealthThenConsumesTheItemThenSaves() {
    Awakening awakening = awakening(1, GolemRoster.Status.PACKED);

    awakening.adaptation.awaken(awakening.event);

    InOrder order = inOrder(registry, awakening.golem, inventory, player);
    order.verify(registry).awaken(eq(player), any(Location.class), eq(new GolemRegistry.Restore(awakening.item, GolemRoster.Status.PACKED)), any());
    order.verify(awakening.golem).setHealth(40D);
    order.verify(inventory).setItem(EquipmentSlot.HAND, null);
    order.verify(player).saveData();
    verify(awakening.adaptation).rise(awakening.golem, GolemBody.IRON);
    verify(awakening.adaptation).awakenFx(awakening.golem);
    ArgumentCaptor<Location> spot = ArgumentCaptor.forClass(Location.class);
    verify(registry).awaken(eq(player), spot.capture(), any(), any());
    assertThat(spot.getValue().getX()).isEqualTo(4.5D);
    assertThat(spot.getValue().getY()).isEqualTo(70D);
    assertThat(spot.getValue().getZ()).isEqualTo(-2.5D);
  }

  @Test
  void levelTwoHealsAPackedGolemForTheTimeItSpentPacked() {
    Awakening awakening = awakening(2, GolemRoster.Status.PACKED);

    awakening.adaptation.awaken(awakening.event);

    verify(awakening.golem).setHealth(46D);
  }

  @Test
  void theAwakenPrepareRestoresCopperAndMarksIronGolemsPlayerBuilt() {
    Awakening awakening = awakening(1, GolemRoster.Status.PACKED);

    awakening.adaptation.awaken(awakening.event);

    ArgumentCaptor<Consumer<Mob>> prepare = consumerCaptor();
    verify(registry).awaken(eq(player), any(Location.class), any(), prepare.capture());
    IronGolem body = mock(IronGolem.class);
    prepare.getValue().accept(body);
    verify(body).setPlayerCreated(true);
  }

  @Test
  void aDormantGolemWhoseEntryIsNoLongerPackedCrumbles() {
    Awakening awakening = awakening(3, GolemRoster.Status.ACTIVE);

    awakening.adaptation.awaken(awakening.event);

    verify(registry, never()).awaken(any(), any(), any(), any());
    verify(inventory).setItem(EquipmentSlot.HAND, null);
    verify(awakening.adaptation).crumbleFx(player);
    verify(player, never()).saveData();
  }

  @Test
  void anotherPlayersDormantGolemOrAMissingLevelIsRefusedAndKept() {
    Awakening foreign = awakening(3, GolemRoster.Status.PACKED);
    PackedGolem theirs = new PackedGolem(foreign.item.golemId(), UUID.randomUUID(), foreign.item.state());
    items.when(() -> GolemItems.packedGolemOf(foreign.held)).thenReturn(Optional.of(theirs));
    foreign.adaptation.awaken(foreign.event);

    Awakening unlearned = awakening(0, GolemRoster.Status.PACKED);
    unlearned.adaptation.awaken(unlearned.event);

    verify(registry, never()).awaken(any(), any(), any(), any());
    verify(inventory, never()).setItem(any(EquipmentSlot.class), any());
  }

  @Test
  void aBlockedSpotOrAFullServerKeepsTheDormantGolem() {
    Awakening blocked = awakening(3, GolemRoster.Status.PACKED);
    doReturn(false).when(blocked.adaptation).spaceClear(blocked.event);
    blocked.adaptation.awaken(blocked.event);

    Awakening full = awakening(3, GolemRoster.Status.PACKED);
    when(registry.awakenBlock(any(Location.class), any(GolemSnapshot.class))).thenReturn(Optional.of(GolemRegistry.BindBlock.SERVER_CAP));
    full.adaptation.awaken(full.event);

    verify(registry, never()).awaken(any(), any(), any(), any());
    verify(inventory, never()).setItem(any(EquipmentSlot.class), any());
  }

  @Test
  void aLostRaceForTheEntryCrumblesTheDormantGolem() {
    Awakening awakening = awakening(1, GolemRoster.Status.PACKED);
    when(registry.awaken(any(), any(), any(), any())).thenReturn(Optional.empty());

    awakening.adaptation.awaken(awakening.event);

    verify(inventory).setItem(EquipmentSlot.HAND, null);
    verify(awakening.adaptation).crumbleFx(player);
    verify(player, never()).saveData();
  }

  @Test
  void aFailedAwakeningKeepsTheDormantGolem() {
    Awakening awakening = awakening(1, GolemRoster.Status.PACKED);
    when(registry.awaken(any(), any(), any(), any())).thenThrow(new IllegalStateException("spawn refused"));

    awakening.adaptation.awaken(awakening.event);

    verify(inventory, never()).setItem(any(EquipmentSlot.class), any());
    verify(player, never()).saveData();
    verify(awakening.adaptation, never()).rise(any(), any());
  }

  @Test
  void pocketRosterRecallsALoadedGolemAndThenCoolsDown() {
    Packable first = packable();
    Packable second = packable();
    GolemStasis learned = learned(3);

    learned.recall(player, first.handle);
    learned.recall(player, second.handle);

    verify(registry).pack(first.handle);
    verify(registry, never()).pack(second.handle);
    verify(player, times(1)).saveData();
  }

  @Test
  void aFailedRecallFreesTheCooldown() {
    Packable refused = packable();
    Packable next = packable();
    when(registry.pack(refused.handle)).thenReturn(Optional.empty());
    GolemStasis learned = learned(3);

    learned.recall(player, refused.handle);
    learned.recall(player, next.handle);

    verify(registry).pack(next.handle);
  }

  @Test
  void recallNeedsLevelThreeAndAGolemInYourWorld() {
    Packable elsewhere = packable();
    when(elsewhere.golem.getWorld()).thenReturn(mock(World.class));
    learned(3).recall(player, elsewhere.handle);

    Packable locked = packable();
    learned(2).recall(player, locked.handle);

    Packable retired = packable();
    when(retired.handle.isRetired()).thenReturn(true);
    learned(3).recall(player, retired.handle);

    verify(registry, never()).pack(any());
  }

  private Packable packable() {
    UUID golemId = UUID.randomUUID();
    UUID playerId = player.getUniqueId();
    IronGolem golem = mock(IronGolem.class);
    lenient().when(golem.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.getLocation()).thenReturn(new Location(world, 2.5D, 64D, 0.5D));
    lenient().when(golem.getHeight()).thenReturn(1D);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(golemId);
    lenient().when(handle.owner()).thenReturn(playerId);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.body()).thenReturn(GolemBody.IRON);
    GolemState state = GolemState.create(new GolemState.Seed(golemId, player.getUniqueId(), GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemState.Anchor.of(WORLD, 2, 64, 0)));
    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 40D, 0, false), 1L);
    GolemRegistry.Packing packing = new GolemRegistry.Packing(new PackedGolem(golemId, player.getUniqueId(), snapshot.toJson()), snapshot,
        MAX_HEALTH, new Location(world, 2.5D, 64D, 0.5D));
    lenient().when(registry.pack(handle)).thenReturn(Optional.of(packing));
    return new Packable(handle, golem, packing);
  }

  private Awakening awakening(int level, GolemRoster.Status status) {
    UUID golemId = UUID.randomUUID();
    GolemState state = GolemState.create(new GolemState.Seed(golemId, player.getUniqueId(), GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 40D, 0, false), System.currentTimeMillis() - 65 * SECOND);
    PackedGolem packed = new PackedGolem(golemId, player.getUniqueId(), snapshot.toJson());
    GolemRoster roster = new GolemRoster();
    roster.add(new GolemRoster.Binding(golemId, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    roster.stow(golemId, GolemRoster.Status.PACKED, packed.state());
    if (status == GolemRoster.Status.ACTIVE) {
      roster.unstow(golemId, GolemRoster.Status.PACKED, packed.state());
    }
    ItemStack held = mock(ItemStack.class);
    lenient().when(held.getType()).thenReturn(Material.IRON_BLOCK);
    lenient().when(held.getAmount()).thenReturn(1);
    lenient().when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(held);
    items.when(() -> GolemItems.packedGolemOf(held)).thenReturn(Optional.of(packed));
    Block block = mock(Block.class);
    lenient().when(block.getLocation()).thenReturn(new Location(world, 4D, 70D, -3D));
    lenient().when(block.getWorld()).thenReturn(world);
    BlockPlaceEvent event = new BlockPlaceEvent(block, mock(BlockState.class), mock(Block.class), held, player, true, EquipmentSlot.HAND);
    event.setCancelled(true);
    IronGolem golem = mock(IronGolem.class);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(registry.awakenBlock(any(Location.class), any(GolemSnapshot.class))).thenReturn(Optional.empty());
    lenient().when(registry.awaken(any(), any(), any(), any())).thenReturn(Optional.of(handle));
    GolemAnimusBinding binding = mock(GolemAnimusBinding.class);
    lenient().when(binding.bodyUnlocked(player, GolemBody.IRON)).thenReturn(true);
    GolemStasis learned = learned(level);
    lenient().doReturn(Optional.of(binding)).when(learned).binding();
    lenient().doReturn(Optional.of(roster)).when(learned).roster(player);
    lenient().doReturn(true).when(learned).spaceClear(event);
    lenient().doReturn(true).when(learned).worldAllows(any(Player.class), any(Location.class));
    return new Awakening(learned, event, held, packed, snapshot, golem);
  }

  private GolemStasis learned(int level) {
    GolemStasis learned = spy(adaptation);
    lenient().doReturn(level).when(learned).getActiveLevel(player);
    lenient().doNothing().when(learned).packFx(any(), any());
    lenient().doNothing().when(learned).awakenFx(any());
    lenient().doNothing().when(learned).crumbleFx(any());
    lenient().doNothing().when(learned).rise(any(), any());
    lenient().doReturn(null).when(learned).channelFx(any());
    return learned;
  }

  private int drain() {
    int pulses = 0;
    while (!golemTicks.isEmpty()) {
      golemTicks.poll().run();
      pulses++;
    }
    return pulses;
  }

  private void pulse(int count) {
    for (int index = 0; index < count && !golemTicks.isEmpty(); index++) {
      golemTicks.poll().run();
    }
  }

  private static org.bukkit.damage.DamageSource mockDamageSource() {
    return mock(org.bukkit.damage.DamageSource.class);
  }

  private List<String> lore(int level) {
    List<String> lines = new ArrayList<>();
    Element stats = mock(Element.class, RETURNS_SELF);
    when(stats.addLore(anyString())).thenAnswer(invocation -> {
      lines.add(invocation.<String>getArgument(0).replaceAll("§[0-9a-fk-orx]", ""));
      return stats;
    });
    adaptation.addStats(level, stats);
    return lines;
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Consumer<Mob>> consumerCaptor() {
    return ArgumentCaptor.forClass((Class<Consumer<Mob>>) (Class<?>) Consumer.class);
  }

  private record Packable(GolemHandle handle, IronGolem golem, GolemRegistry.Packing packing) {
  }

  private record Awakening(GolemStasis adaptation, BlockPlaceEvent event, ItemStack held, PackedGolem item, GolemSnapshot snapshot, IronGolem golem) {
  }
}
