package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemBrainSettings;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemPacking;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.overseer.Overseer;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PrePlayerAttackEntityEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrookInteractionsTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final double LEASH_RADIUS = 48D;

  private final AtomicBoolean golemSchedulerAccepts = new AtomicBoolean(true);
  private final AtomicBoolean golemTaskRetires = new AtomicBoolean(false);
  private final AtomicReference<CrookGestures<GolemHandle>> openedGestures = new AtomicReference<>();
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemGui> golemGui;
  private WorldRef world;
  private PlayerRef player;
  private ItemRef crook;
  private MobRef golem;
  private GolemHandle handle;
  private Adaptation<AdaptationConfig> policy;
  private Overseer.Commander commander;
  private PlayerInventoryRef inventory;
  private CrookInteractions interactions;

  @BeforeEach
  void setUp() {
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    crook = mock(ItemRef.class);
    player = mock(PlayerRef.class);
    inventory = mock(PlayerInventoryRef.class);
    lenient().when(inventory.mainHand()).thenReturn(crook);
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(player.uuid()).thenReturn(OWNER);
    golem = mock(MobRef.class);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.location()).thenReturn(new Loc(world, 0D, 64D, 0D));
    GolemState state = mock(GolemState.class);
    lenient().when(state.home()).thenReturn(GolemAnchor.of(WORLD, 0, 64, 0));
    handle = mock(GolemHandle.class);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.state()).thenReturn(state);
    policy = policyAdaptation();
    lenient().when(policy.canAccessChest(eq(player), any(Loc.class))).thenReturn(true);

    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenReturn(true);
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      if (invocation.getArgument(0) != golem) {
        return true;
      }
      if (!golemSchedulerAccepts.get()) {
        return false;
      }
      invocation.<Runnable>getArgument(golemTaskRetires.get() ? 2 : 1).run();
      return true;
    });
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.of(GolemItemKind.CROOK));
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(new GolemBrainSettings(5, 32D, LEASH_RADIUS, 8, 10));
    runtime.when(GolemancyRuntime::protectionPolicy).thenReturn(Optional.of(policy));
    golemGui = mockStatic(GolemGui.class);
    golemGui.when(() -> GolemGui.open(eq(player), eq(handle), any())).thenAnswer(invocation -> {
      openedGestures.set(invocation.getArgument(2));
      return null;
    });
    commander = mock(Overseer.Commander.class);
    Overseer.setCommander(commander);
    interactions = new CrookInteractions(new NodeAreaSelector());
  }

  @AfterEach
  void tearDown() {
    Overseer.clearCommander(commander);
    golemGui.close();
    runtime.close();
    items.close();
    scheduling.close();
  }

  @Test
  void leftClickingAirWithTheCrookSendsTheCurrentSignal() {
    leftClickAir(crook, false);

    verify(commander).send(player);
    verify(commander, never()).cycle(any(PlayerRef.class));
  }

  @Test
  void sneakLeftClickingAirWithTheCrookCyclesTheSignal() {
    leftClickAir(crook, true);

    verify(commander).cycle(player);
    verify(commander, never()).send(any(PlayerRef.class));
  }

  @Test
  void theAirClickThatFollowsAnAttackOnAnEntityOrNodeSendsNothing() {
    interactions.on(new PrePlayerAttackEntityEvent(player, golem, true));

    leftClickAir(crook, false);
    leftClickAir(crook, true);

    verify(commander, never()).send(any(PlayerRef.class));
    verify(commander, never()).cycle(any(PlayerRef.class));
  }

  @Test
  void attacksWithoutTheCrookInHandLeaveTheNextAirClickAlone() {
    ItemRef sword = mock(ItemRef.class);
    items.when(() -> GolemItems.kindOf(sword)).thenReturn(Optional.empty());
    when(inventory.mainHand()).thenReturn(sword);
    interactions.on(new PrePlayerAttackEntityEvent(player, golem, true));
    when(inventory.mainHand()).thenReturn(crook);

    leftClickAir(crook, false);

    verify(commander).send(player);
  }

  @Test
  void leftClickingAirWithAnotherItemSendsNothing() {
    ItemRef sword = mock(ItemRef.class);
    items.when(() -> GolemItems.kindOf(sword)).thenReturn(Optional.empty());

    leftClickAir(sword, false);

    verify(commander, never()).send(any(PlayerRef.class));
  }

  @Test
  void withoutOverseerLeftClicksAreIgnored() {
    Overseer.clearCommander(commander);

    leftClickAir(crook, false);

    verify(commander, never()).send(any(PlayerRef.class));
  }

  private void leftClickAir(ItemRef held, boolean sneaking) {
    lenient().when(player.isSneaking()).thenReturn(sneaking);
    interactions.on(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND, held, null, null, null, null, null));
  }

  @Test
  void aChestWithinTheConfiguredLeashRadiusLinksAndEndsLinkMode() {
    armLinkMode();

    PlayerInteractEvent link = clickChest(40);

    verify(link).setUseBlock(UseResult.DENY);
    verify(handle).setHomeChest(GolemAnchor.of(WORLD, 40, 64, 0));
    verify(clickChest(40), never()).setUseBlock(any(UseResult.class));
  }

  @Test
  void aChestBeyondTheConfiguredLeashRadiusKeepsLinkModeArmed() {
    armLinkMode();

    clickChest(50);

    verify(handle, never()).setHomeChest(any(GolemAnchor.class));
    verify(clickChest(50)).setUseBlock(UseResult.DENY);
  }

  @Test
  void aProtectedChestKeepsLinkModeArmed() {
    when(policy.canAccessChest(eq(player), any(Loc.class))).thenReturn(false);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemAnchor.class));
    verify(clickChest(4)).setUseBlock(UseResult.DENY);
  }

  @Test
  void aRetiredGolemEndsLinkMode() {
    when(handle.isRetired()).thenReturn(true);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemAnchor.class));
    verify(clickChest(4), never()).setUseBlock(any(UseResult.class));
  }

  @Test
  void anInvalidGolemEndsLinkMode() {
    when(golem.isValid()).thenReturn(false);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemAnchor.class));
    verify(clickChest(4), never()).setUseBlock(any(UseResult.class));
  }

  @Test
  void aGolemTaskRetiredBeforeItRunsEndsLinkMode() {
    golemTaskRetires.set(true);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemAnchor.class));
    verify(clickChest(4), never()).setUseBlock(any(UseResult.class));
  }

  @Test
  void aGolemSchedulerRefusalEndsLinkMode() {
    golemSchedulerAccepts.set(false);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemAnchor.class));
    verify(clickChest(4), never()).setUseBlock(any(UseResult.class));
  }

  private void armLinkMode() {
    interactions.on(new PlayerInteractEntityEvent(player, golem, Hand.MAIN_HAND, null));
    CrookGestures<GolemHandle> gestures = openedGestures.get();
    assertThat(gestures).isNotNull();
    gestures.armLink(OWNER, handle, System.currentTimeMillis());
  }

  @Test
  void sneakRightClickingYourGolemWithStasisPacksItInsteadOfOpeningTheGui() {
    GolemPacking.Packer packer = mock(GolemPacking.Packer.class);
    when(packer.packs(player)).thenReturn(true);
    GolemPacking.register(packer);
    try {
      when(player.isSneaking()).thenReturn(true);
      PlayerInteractEntityEvent main = new PlayerInteractEntityEvent(player, golem, Hand.MAIN_HAND, null);
      PlayerInteractEntityEvent off = new PlayerInteractEntityEvent(player, golem, Hand.OFF_HAND, null);

      interactions.on(main);
      interactions.on(off);

      assertThat(main.isCancelled()).isTrue();
      assertThat(off.isCancelled()).isTrue();
      verify(packer).pack(player, handle);
      golemGui.verify(() -> GolemGui.open(any(), any(), any()), never());
    } finally {
      GolemPacking.unregister(packer);
    }
  }

  @Test
  void withoutStasisOrWithoutSneakingTheCrookOpensTheGolemGui() {
    GolemPacking.Packer packer = mock(GolemPacking.Packer.class);
    when(packer.packs(player)).thenReturn(false);
    GolemPacking.register(packer);
    try {
      when(player.isSneaking()).thenReturn(true);
      interactions.on(new PlayerInteractEntityEvent(player, golem, Hand.MAIN_HAND, null));
      when(player.isSneaking()).thenReturn(false);
      when(packer.packs(player)).thenReturn(true);
      interactions.on(new PlayerInteractEntityEvent(player, golem, Hand.MAIN_HAND, null));

      verify(packer, never()).pack(any(), any());
      golemGui.verify(() -> GolemGui.open(eq(player), eq(handle), any()), times(2));
    } finally {
      GolemPacking.unregister(packer);
    }
    when(player.isSneaking()).thenReturn(true);
    interactions.on(new PlayerInteractEntityEvent(player, golem, Hand.MAIN_HAND, null));
    golemGui.verify(() -> GolemGui.open(eq(player), eq(handle), any()), times(3));
  }

  @Test
  void withoutAProtectionPolicyNoContainerLinks() {
    runtime.when(GolemancyRuntime::protectionPolicy).thenReturn(Optional.empty());
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemAnchor.class));
  }

  @SuppressWarnings("unchecked")
  private static Adaptation<AdaptationConfig> policyAdaptation() {
    return mock(Adaptation.class);
  }

  private PlayerInteractEvent clickChest(int x) {
    BlockRef block = mock(BlockRef.class);
    when(block.container()).thenReturn(mock(InventoryRef.class));
    lenient().when(block.location()).thenReturn(new Loc(world, x, 64D, 0D));
    PlayerInteractEvent event = spy(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, crook, block, null, null, null, null));
    interactions.on(event);
    return event;
  }
}
