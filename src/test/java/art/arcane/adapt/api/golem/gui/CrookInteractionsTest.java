package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.protection.ProtectorRegistry;
import art.arcane.adapt.util.common.scheduling.J;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
  private Protector protector;
  private CrookInteractions interactions;

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    crook = mock(ItemStack.class);
    player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
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
    protector = mock(Protector.class);
    lenient().when(protector.canAccessChest(eq(player), any(Location.class), isNull())).thenReturn(true);
    ProtectorRegistry protectors = mock(ProtectorRegistry.class);
    lenient().when(protectors.getDefaultProtectors()).thenReturn(List.of(protector));
    lenient().when(plugin.getProtectorRegistry()).thenReturn(protectors);

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
    golemGui = mockStatic(GolemGui.class);
    golemGui.when(() -> GolemGui.open(eq(player), eq(handle), any())).thenAnswer(invocation -> {
      openedGestures.set(invocation.getArgument(2));
      return null;
    });
    interactions = new CrookInteractions();
  }

  @AfterEach
  void tearDown() {
    golemGui.close();
    runtime.close();
    items.close();
    scheduling.close();
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
    when(protector.canAccessChest(eq(player), any(Location.class), isNull())).thenReturn(false);
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
