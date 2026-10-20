package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemPacking;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.overseer.Overseer;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.event.ProtectionProbe;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
  private MockedStatic<J> scheduling;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemGui> golemGui;
  private World world;
  private Player player;
  private ItemStack crook;
  private IronGolem golem;
  private GolemHandle handle;
  private Adaptation<Object> policy;
  private Overseer.Commander commander;
  private PlayerInventory inventory;
  private CrookInteractions interactions;

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    crook = mock(ItemStack.class);
    player = mock(Player.class);
    inventory = mock(PlayerInventory.class);
    lenient().when(inventory.getItemInMainHand()).thenReturn(crook);
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(player.getUniqueId()).thenReturn(OWNER);
    golem = mock(IronGolem.class);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.getLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    GolemState state = mock(GolemState.class);
    lenient().when(state.home()).thenReturn(GolemState.Anchor.of(WORLD, 0, 64, 0));
    handle = mock(GolemHandle.class);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.state()).thenReturn(state);
    policy = policyAdaptation();
    lenient().when(policy.canAccessChest(eq(player), any(Location.class))).thenReturn(true);

    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
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
    items.when(() -> GolemItems.kindOf(any(ItemStack.class))).thenReturn(Optional.of(GolemItemKind.CROOK));
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
  void protectionProbeClicksNeverOpenTheGolemGui() {
    PlayerInteractEntityEvent probe = ProtectionProbe.entityInteract(player, golem);

    interactions.on(probe);

    assertThat(probe.isCancelled()).isFalse();
    golemGui.verify(() -> GolemGui.open(any(), any(), any()), never());
  }

  @Test
  void leftClickingAirWithTheCrookSendsTheCurrentSignal() {
    leftClickAir(crook, false);

    verify(commander).send(player);
    verify(commander, never()).cycle(any(Player.class));
  }

  @Test
  void sneakLeftClickingAirWithTheCrookCyclesTheSignal() {
    leftClickAir(crook, true);

    verify(commander).cycle(player);
    verify(commander, never()).send(any(Player.class));
  }

  @Test
  void theAirClickThatFollowsAnAttackOnAnEntityOrNodeSendsNothing() {
    interactions.attacked(player);

    leftClickAir(crook, false);
    leftClickAir(crook, true);

    verify(commander, never()).send(any(Player.class));
    verify(commander, never()).cycle(any(Player.class));
  }

  @Test
  void attacksWithoutTheCrookInHandLeaveTheNextAirClickAlone() {
    ItemStack sword = mock(ItemStack.class);
    items.when(() -> GolemItems.kindOf(sword)).thenReturn(Optional.empty());
    when(inventory.getItemInMainHand()).thenReturn(sword);
    interactions.attacked(player);
    when(inventory.getItemInMainHand()).thenReturn(crook);

    leftClickAir(crook, false);

    verify(commander).send(player);
  }

  @Test
  void leftClickingAirWithAnotherItemSendsNothing() {
    ItemStack sword = mock(ItemStack.class);
    items.when(() -> GolemItems.kindOf(sword)).thenReturn(Optional.empty());

    leftClickAir(sword, false);

    verify(commander, never()).send(any(Player.class));
  }

  @Test
  void withoutOverseerLeftClicksAreIgnored() {
    Overseer.clearCommander(commander);

    leftClickAir(crook, false);

    verify(commander, never()).send(any(Player.class));
  }

  private void leftClickAir(ItemStack held, boolean sneaking) {
    lenient().when(player.isSneaking()).thenReturn(sneaking);
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);
    when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    when(event.getItem()).thenReturn(held);
    lenient().when(event.getPlayer()).thenReturn(player);
    lenient().when(event.getAction()).thenReturn(Action.LEFT_CLICK_AIR);
    interactions.on(event);
  }

  @Test
  void aChestWithinTheConfiguredLeashRadiusLinksAndEndsLinkMode() {
    armLinkMode();

    PlayerInteractEvent link = clickChest(40);

    verify(link).setUseInteractedBlock(Event.Result.DENY);
    verify(handle).setHomeChest(GolemState.Anchor.of(WORLD, 40, 64, 0));
    verify(clickChest(40), never()).setUseInteractedBlock(any(Event.Result.class));
  }

  @Test
  void aChestBeyondTheConfiguredLeashRadiusKeepsLinkModeArmed() {
    armLinkMode();

    clickChest(50);

    verify(handle, never()).setHomeChest(any(GolemState.Anchor.class));
    verify(clickChest(50)).setUseInteractedBlock(Event.Result.DENY);
  }

  @Test
  void aProtectedChestKeepsLinkModeArmed() {
    when(policy.canAccessChest(eq(player), any(Location.class))).thenReturn(false);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemState.Anchor.class));
    verify(clickChest(4)).setUseInteractedBlock(Event.Result.DENY);
  }

  @Test
  void aRetiredGolemEndsLinkMode() {
    when(handle.isRetired()).thenReturn(true);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemState.Anchor.class));
    verify(clickChest(4), never()).setUseInteractedBlock(any(Event.Result.class));
  }

  @Test
  void anInvalidGolemEndsLinkMode() {
    when(golem.isValid()).thenReturn(false);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemState.Anchor.class));
    verify(clickChest(4), never()).setUseInteractedBlock(any(Event.Result.class));
  }

  @Test
  void aGolemTaskRetiredBeforeItRunsEndsLinkMode() {
    golemTaskRetires.set(true);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemState.Anchor.class));
    verify(clickChest(4), never()).setUseInteractedBlock(any(Event.Result.class));
  }

  @Test
  void aGolemSchedulerRefusalEndsLinkMode() {
    golemSchedulerAccepts.set(false);
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemState.Anchor.class));
    verify(clickChest(4), never()).setUseInteractedBlock(any(Event.Result.class));
  }

  private void armLinkMode() {
    PlayerInteractEntityEvent open = mock(PlayerInteractEntityEvent.class);
    when(open.getPlayer()).thenReturn(player);
    when(open.getRightClicked()).thenReturn(golem);
    when(open.getHand()).thenReturn(EquipmentSlot.HAND);
    interactions.on(open);
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
      PlayerInteractEntityEvent main = new PlayerInteractEntityEvent(player, golem, EquipmentSlot.HAND);
      PlayerInteractEntityEvent off = new PlayerInteractEntityEvent(player, golem, EquipmentSlot.OFF_HAND);

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
      interactions.on(new PlayerInteractEntityEvent(player, golem, EquipmentSlot.HAND));
      when(player.isSneaking()).thenReturn(false);
      when(packer.packs(player)).thenReturn(true);
      interactions.on(new PlayerInteractEntityEvent(player, golem, EquipmentSlot.HAND));

      verify(packer, never()).pack(any(), any());
      golemGui.verify(() -> GolemGui.open(eq(player), eq(handle), any()), times(2));
    } finally {
      GolemPacking.unregister(packer);
    }
    when(player.isSneaking()).thenReturn(true);
    interactions.on(new PlayerInteractEntityEvent(player, golem, EquipmentSlot.HAND));
    golemGui.verify(() -> GolemGui.open(eq(player), eq(handle), any()), times(3));
  }

  @Test
  void withoutAProtectionPolicyNoContainerLinks() {
    runtime.when(GolemancyRuntime::protectionPolicy).thenReturn(Optional.empty());
    armLinkMode();

    clickChest(4);

    verify(handle, never()).setHomeChest(any(GolemState.Anchor.class));
  }

  @SuppressWarnings("unchecked")
  private static Adaptation<Object> policyAdaptation() {
    return mock(Adaptation.class);
  }

  private PlayerInteractEvent clickChest(int x) {
    Block block = mock(Block.class);
    when(block.getState()).thenReturn(mock(Chest.class));
    lenient().when(block.getLocation()).thenReturn(new Location(world, x, 64D, 0D));
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);
    when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    when(event.getItem()).thenReturn(crook);
    when(event.getPlayer()).thenReturn(player);
    when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
    when(event.getClickedBlock()).thenReturn(block);
    interactions.on(event);
    return event;
  }
}
