package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.testkit.FakeItem;
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
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.OwnerBoundDrop;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
  private static final ItemRef EMPTY = FakeItem.empty();

  private final Deque<Runnable> golemTicks = new ArrayDeque<>();
  private final AtomicBoolean playerReachable = new AtomicBoolean(true);
  private GolemStasis adaptation;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<Tasks> scheduling;
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<GolemItems> items;
  private MockedStatic<OwnerBoundDrop> drops;
  private WorldRef world;
  private PlayerRef player;
  private PlayerInventoryRef inventory;
  private ItemRef crook;
  private GolemRegistry registry;
  private ItemRef dormantStack;

  @BeforeEach
  void construct() {
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    player = mock(PlayerRef.class);
    inventory = mock(PlayerInventoryRef.class);
    crook = mock(ItemRef.class);
    lenient().when(player.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(player.name()).thenReturn("Alex");
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(player.world()).thenReturn(world);
    lenient().when(player.location()).thenReturn(new Loc(world, 0.5D, 64D, 0.5D));
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.isValid()).thenReturn(true);
    lenient().when(inventory.mainHand()).thenReturn(crook);
    lenient().when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of());
    registry = mock(GolemRegistry.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.empty());
    scheduling = mockStatic(Tasks.class);
    scheduling.when(() -> Tasks.isOwnedByCurrentRegion(any(EntityRef.class))).thenReturn(true);
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenReturn(true);
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class), anyInt())).thenReturn(true);
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      if (invocation.getArgument(0) == player && !playerReachable.get()) {
        return false;
      }
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class), anyInt(), any(Runnable.class))).thenAnswer(invocation -> {
      golemTicks.add(invocation.getArgument(1));
      return true;
    });
    scheduling.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(MAX_HEALTH);
    ItemOps itemOps = mock(ItemOps.class);
    lenient().when(itemOps.empty()).thenReturn(EMPTY);
    lenient().when(AdaptCore.platform().host().items()).thenReturn(itemOps);
    items = mockStatic(GolemItems.class, CALLS_REAL_METHODS);
    dormantStack = mock(ItemRef.class);
    items.when(() -> GolemItems.kindOf(crook)).thenReturn(Optional.of(GolemItemKind.CROOK));
    drops = mockStatic(OwnerBoundDrop.class);
    adaptation = new GolemStasis();
  }

  @AfterEach
  void close() {
    drops.close();
    items.close();
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
    assertThat(config.recallCooldownSeconds).isEqualTo(60);
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
  void loadingCapsTheRecallCooldownAtSixtySeconds() {
    assertThat(adaptation.reloadConfigSnapshot("recallCooldownSeconds = 500\n", dataFolder.toPath().resolve("cap.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().recallCooldownSeconds).isEqualTo(60);
  }

  @Test
  void everyLevelShowsItsPackingHealingAndRecall() {
    String awaken = "+ Place a Dormant Golem to Awaken It";
    assertThat(lore(1)).containsExactly("* 2 Seconds to Pack a Golem", awaken);
    assertThat(lore(2)).containsExactly("+ Instant Packing", awaken, "+ 1 Health Healed While Packed", "* 10 Seconds Between Packed Heals");
    assertThat(lore(3)).containsExactly("+ Instant Packing", awaken, "+ 1 Health Healed While Packed", "* 10 Seconds Between Packed Heals",
        "+ Pocket Roster: Recall Golems from the Roster", "* 60 Seconds Between Recalls");
  }

  @Test
  void levelOneStartsAChannelWhileHigherLevelsPackAtOnce() {
    Packable packable = packable();
    GolemStasis channeling = learned(1);
    doNothing().when(channeling).channel(any(), any(), anyInt());
    when(registry.pack(packable.handle)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    channeling.pack(player, packable.handle);
    verify(channeling).channel(player, packable.handle, 40);
    verify(registry, never()).pack(any());

    GolemStasis instant = learned(2);
    instant.pack(player, packable.handle);
    verify(instant, never()).channel(any(), any(), anyInt());
    verify(registry).pack(packable.handle);
  }

  @Test
  void aPackDelegatesItsDurableHandoverBeforeEffects() {
    Packable packable = packable();
    GolemStasis learned = learned(2);

    learned.pack(player, packable.handle);

    InOrder order = inOrder(registry, inventory, player);
    order.verify(registry).pack(packable.handle);
    order.verify(registry).deliverPacked(player, packable.packing.packed(), dormantStack);
    verify(learned).packFx(packable.packing.at(), GolemBody.IRON);
    drops.verifyNoInteractions();
  }

  @Test
  void theDormantGolemIsHandedOverBeforeAnyPackEffectsRun() {
    Packable packable = packable();
    GolemStasis learned = learned(2);
    doThrow(new IllegalStateException("effects failed")).when(learned).packFx(any(), any());

    learned.pack(player, packable.handle);

    verify(registry).deliverPacked(player, packable.packing.packed(), dormantStack);
  }

  @Test
  void anErrorWhilePackingStillFreesTheRecallCooldown() {
    Packable failing = packable();
    Packable next = packable();
    when(registry.pack(failing.handle)).thenThrow(new LinkageError("missing method"));
    GolemStasis learned = learned(3);

    assertThatThrownBy(() -> learned.recall(player, failing.handle)).isInstanceOf(LinkageError.class);
    learned.recall(player, next.handle);

    verify(registry).pack(next.handle);
  }




  @Test
  void aRefusedPackKeepsTheGolemAndHandsOutNothing() {
    Packable packable = packable();
    when(registry.pack(packable.handle)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

    learned(2).pack(player, packable.handle);

    verify(inventory, never()).addItem(any(ItemRef.class));
    verify(player, never()).saveData();
    drops.verifyNoInteractions();
  }

  @Test
  void boneGolemsCanPackAndRecall() {
    Packable bone = packable();
    when(bone.handle.body()).thenReturn(GolemBody.BONE);
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    try (MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class)) {
      learned(3).pack(player, bone.handle);
      learned(3).recall(player, bone.handle);
    }

    verify(registry, times(2)).pack(bone.handle);
  }

  @Test
  void onlyTheOwnerWithStasisCanPack() {
    Packable packable = packable();
    when(packable.handle.owner()).thenReturn(UUID.randomUUID());

    learned(3).pack(player, packable.handle);
    UUID playerId = player.uuid();
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
    verify(packable.golem, times(40)).setTarget(null);
    verify(registry).pack(packable.handle);
    verify(registry).deliverPacked(player, packable.packing.packed(), dormantStack);
  }

  @Test
  void damageMovingAwayOrSwappingItemsBreaksTheChannel() {
    Packable hurt = packable();
    GolemStasis learned = learned(1);
    learned.pack(player, hurt.handle);
    pulse(5);
    learned.on(new EntityDamageEvent(hurt.golem, DamageCause.FALL, Id.minecraft("fall"), 1D, FinalDamage.IDENTITY));
    drain();

    Packable far = packable();
    learned.pack(player, far.handle);
    pulse(5);
    when(far.golem.location()).thenReturn(new Loc(world, 9D, 64D, 0.5D));
    drain();

    Packable swapped = packable();
    learned.pack(player, swapped.handle);
    pulse(5);
    when(inventory.heldSlot()).thenReturn(4);
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
    order.verify(registry).awaken(eq(player), any(Loc.class), eq(new GolemRegistry.Restore(awakening.item, GolemRoster.Status.PACKED)), any());
    order.verify(awakening.golem).setHealth(40D);
    order.verify(inventory).setHand(Hand.MAIN_HAND, EMPTY);
    order.verify(player).saveData();
    verify(awakening.adaptation).rise(awakening.golem, GolemBody.IRON);
    verify(awakening.adaptation).awakenFx(awakening.golem);
    ArgumentCaptor<Loc> spot = ArgumentCaptor.forClass(Loc.class);
    verify(registry).awaken(eq(player), spot.capture(), any(), any());
    assertThat(spot.getValue().x()).isEqualTo(4.5D);
    assertThat(spot.getValue().y()).isEqualTo(70D);
    assertThat(spot.getValue().z()).isEqualTo(-2.5D);
  }

  @Test
  void levelTwoHealsAPackedGolemForTheTimeItSpentPacked() {
    Awakening awakening = awakening(2, GolemRoster.Status.PACKED);

    awakening.adaptation.awaken(awakening.event);

    verify(awakening.golem).setHealth(46D);
  }

  @Test
  void theAwakenPrepareRestoresWhetherAPlayerBuiltTheIronGolem() {
    Awakening built = awakening(1, GolemRoster.Status.PACKED);
    built.adaptation.awaken(built.event);
    ArgumentCaptor<Consumer<MobRef>> prepare = consumerCaptor();
    verify(registry).awaken(eq(player), any(Loc.class), any(), prepare.capture());
    MobRef builtBody = mock(MobRef.class);
    when(builtBody.type()).thenReturn(Id.minecraft("iron_golem"));
    prepare.getValue().accept(builtBody);

    Awakening village = awakening(1, GolemRoster.Status.PACKED, false);
    village.adaptation.awaken(village.event);
    ArgumentCaptor<Consumer<MobRef>> villagePrepare = consumerCaptor();
    verify(registry, times(2)).awaken(eq(player), any(Loc.class), any(), villagePrepare.capture());
    MobRef villageBody = mock(MobRef.class);
    when(villageBody.type()).thenReturn(Id.minecraft("iron_golem"));
    villagePrepare.getValue().accept(villageBody);

    verify(builtBody).setPlayerCreated(true);
    verify(villageBody).setPlayerCreated(false);
  }

  @Test
  void aPlacementTheServerWillNotBuildIsRefusedAndKept() {
    Awakening awakening = awakening(3, GolemRoster.Status.PACKED);
    awakening.event.setBuild(false);

    awakening.adaptation.awaken(awakening.event);

    verify(registry, never()).awaken(any(), any(), any(), any());
    verify(inventory, never()).setHand(any(Hand.class), any());
  }

  @Test
  void anAwakeningInADisabledWorldIsRefusedBeforeTheLevelCheck() {
    Awakening awakening = awakening(0, GolemRoster.Status.PACKED);
    doReturn(false).when(awakening.adaptation).worldEnabled(any(Loc.class));

    awakening.adaptation.awaken(awakening.event);

    verify(registry, never()).awaken(any(), any(), any(), any());
    verify(inventory, never()).setHand(any(Hand.class), any());
  }

  @Test
  void anErrorWhileAwakeningKeepsTheDormantGolem() {
    Awakening awakening = awakening(1, GolemRoster.Status.PACKED);
    when(registry.awaken(any(), any(), any(), any())).thenThrow(new LinkageError("missing spawn method"));

    assertThatThrownBy(() -> awakening.adaptation.awaken(awakening.event)).isInstanceOf(LinkageError.class);

    verify(inventory, never()).setHand(any(Hand.class), any());
    verify(player, never()).saveData();
  }

  @Test
  void aDormantGolemWhoseEntryIsNoLongerPackedCrumbles() {
    Awakening awakening = awakening(3, GolemRoster.Status.ACTIVE);

    awakening.adaptation.awaken(awakening.event);

    verify(registry, never()).awaken(any(), any(), any(), any());
    verify(inventory).setHand(Hand.MAIN_HAND, EMPTY);
    verify(awakening.adaptation).crumbleFx(player);
    verify(player).saveData();
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
    verify(inventory, never()).setHand(any(Hand.class), any());
  }

  @Test
  void aRevokedBoneReturnsItsItemsEvenWhenStasisIsUnlearned() {
    Awakening awakening = awakening(0, GolemRoster.Status.PACKED);
    UUID id = awakening.item.golemId();
    GolemState state = GolemState.create(new GolemState.Seed(id, player.uuid(), GolemBody.BONE, GolemCoreType.GUARDIAN,
        GolemAnchor.of(WORLD, 0, 64, 0)));
    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 20D, 0, false, true), 1L);
    PackedGolem packed = new PackedGolem(id, player.uuid(), snapshot.toJson());
    GolemRoster roster = new GolemRoster();
    roster.add(new GolemRoster.Binding(id, GolemBody.BONE, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    roster.stow(id, GolemRoster.Status.PACKED, packed.state());
    roster.revokeBones();
    doReturn(Optional.of(roster)).when(awakening.adaptation).roster(player);
    when(awakening.held.type()).thenReturn(Id.minecraft("bone_block"));
    items.when(() -> GolemItems.packedGolemOf(awakening.held)).thenReturn(Optional.of(packed));

    awakening.adaptation.awaken(awakening.event);

    verify(registry).dissolveRevoked(packed, player.location());
    verify(registry, never()).awaken(any(), any(), any(), any());
    verify(inventory).setHand(Hand.MAIN_HAND, EMPTY);
    verify(player).saveData();
  }

  @Test
  void aBlockedSpotOrAFullServerKeepsTheDormantGolem() {
    Awakening blocked = awakening(3, GolemRoster.Status.PACKED);
    doReturn(false).when(blocked.adaptation).spaceClear(blocked.event);
    blocked.adaptation.awaken(blocked.event);

    Awakening full = awakening(3, GolemRoster.Status.PACKED);
    when(registry.awakenBlock(any(Loc.class), any(GolemSnapshot.class))).thenReturn(Optional.of(GolemRegistry.BindBlock.SERVER_CAP));
    full.adaptation.awaken(full.event);

    verify(registry, never()).awaken(any(), any(), any(), any());
    verify(inventory, never()).setHand(any(Hand.class), any());
  }

  @Test
  void aLostRaceForTheEntryCrumblesTheDormantGolem() {
    Awakening awakening = awakening(1, GolemRoster.Status.PACKED);
    when(registry.awaken(any(), any(), any(), any())).thenReturn(Optional.empty());

    awakening.adaptation.awaken(awakening.event);

    verify(inventory).setHand(Hand.MAIN_HAND, EMPTY);
    verify(awakening.adaptation).crumbleFx(player);
    verify(player).saveData();
  }

  @Test
  void aFailedAwakeningKeepsTheDormantGolem() {
    Awakening awakening = awakening(1, GolemRoster.Status.PACKED);
    when(registry.awaken(any(), any(), any(), any())).thenThrow(new IllegalStateException("spawn refused"));

    awakening.adaptation.awaken(awakening.event);

    verify(inventory, never()).setHand(any(Hand.class), any());
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
    verify(registry).deliverPacked(player, first.packing.packed(), dormantStack);
  }

  @Test
  void aFailedRecallFreesTheCooldown() {
    Packable refused = packable();
    Packable next = packable();
    when(registry.pack(refused.handle)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
    GolemStasis learned = learned(3);

    learned.recall(player, refused.handle);
    learned.recall(player, next.handle);

    verify(registry).pack(next.handle);
  }

  @Test
  void recallNeedsLevelThreeAndAGolemInYourWorld() {
    Packable elsewhere = packable();
    when(elsewhere.golem.world()).thenReturn(mock(WorldRef.class));
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
    UUID playerId = player.uuid();
    MobRef golem = mock(MobRef.class);
    lenient().when(golem.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.location()).thenReturn(new Loc(world, 2.5D, 64D, 0.5D));
    lenient().when(golem.height()).thenReturn(1D);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(golemId);
    lenient().when(handle.owner()).thenReturn(playerId);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.body()).thenReturn(GolemBody.IRON);
    GolemState state = GolemState.create(new GolemState.Seed(golemId, player.uuid(), GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemAnchor.of(WORLD, 2, 64, 0)));
    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 40D, 0, false, true), 1L);
    GolemRegistry.Packing packing = new GolemRegistry.Packing(new PackedGolem(golemId, playerId, snapshot.toJson()), snapshot, dormantStack,
        new Loc(world, 2.5D, 64D, 0.5D));
    lenient().when(registry.pack(handle)).thenReturn(CompletableFuture.completedFuture(Optional.of(packing)));
    return new Packable(handle, golem, packing);
  }

  private Awakening awakening(int level, GolemRoster.Status status) {
    return awakening(level, status, true);
  }

  private Awakening awakening(int level, GolemRoster.Status status, boolean playerCreated) {
    UUID golemId = UUID.randomUUID();
    GolemState state = GolemState.create(new GolemState.Seed(golemId, player.uuid(), GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemAnchor.of(WORLD, 0, 64, 0)));
    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 40D, 0, false, playerCreated),
        System.currentTimeMillis() - 65 * SECOND);
    PackedGolem packed = new PackedGolem(golemId, player.uuid(), snapshot.toJson());
    GolemRoster roster = new GolemRoster();
    roster.add(new GolemRoster.Binding(golemId, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    roster.stow(golemId, GolemRoster.Status.PACKED, packed.state());
    if (status == GolemRoster.Status.ACTIVE) {
      roster.unstow(golemId, GolemRoster.Status.PACKED, packed.state());
    }
    ItemRef held = mock(ItemRef.class);
    lenient().when(held.type()).thenReturn(Id.minecraft("iron_block"));
    lenient().when(held.amount()).thenReturn(1);
    lenient().when(inventory.hand(Hand.MAIN_HAND)).thenReturn(held);
    lenient().when(inventory.mainHand()).thenReturn(held);
    items.when(() -> GolemItems.packedGolemOf(held)).thenReturn(Optional.of(packed));
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.location()).thenReturn(new Loc(world, 4D, 70D, -3D));
    lenient().when(block.world()).thenReturn(world);
    BlockPlaceEvent event = new BlockPlaceEvent(player, block, mock(BlockStateRef.class), mock(BlockRef.class), held, Hand.MAIN_HAND);
    event.setCancelled(true);
    MobRef golem = mock(MobRef.class);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(registry.awakenBlock(any(Loc.class), any(GolemSnapshot.class))).thenReturn(Optional.empty());
    lenient().when(registry.awaken(any(), any(), any(), any())).thenReturn(Optional.of(handle));
    GolemAnimusBinding binding = mock(GolemAnimusBinding.class);
    lenient().when(binding.bodyUnlocked(player, GolemBody.IRON)).thenReturn(true);
    GolemStasis learned = learned(level);
    lenient().doReturn(Optional.of(binding)).when(learned).binding();
    lenient().doReturn(Optional.of(roster)).when(learned).roster(player);
    lenient().doReturn(true).when(learned).spaceClear(event);
    lenient().doReturn(true).when(learned).worldEnabled(any(Loc.class));
    lenient().doReturn(true).when(learned).placeAllowed(any(PlayerRef.class), any(Loc.class));
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

  private List<String> lore(int level) {
    List<String> lines = new ArrayList<>();
    StatLines stats = new StatLines();
    adaptation.addStats(level, stats);
    for (String line : stats.lines()) {
      lines.add(line.replaceAll("§[0-9a-fk-orx]", ""));
    }
    return lines;
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Consumer<MobRef>> consumerCaptor() {
    return ArgumentCaptor.forClass((Class<Consumer<MobRef>>) (Class<?>) Consumer.class);
  }

  private record Packable(GolemHandle handle, MobRef golem, GolemRegistry.Packing packing) {
  }

  private record Awakening(GolemStasis adaptation, BlockPlaceEvent event, ItemRef held, PackedGolem item, GolemSnapshot snapshot, MobRef golem) {
  }
}
