package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRenderer;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.golem.overseer.Overseer;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeAreaSelectorTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final NodeKey KEY = new NodeKey(WORLD, 10, 64, 10, BlockFace.UP);

  private final List<Runnable> watches = new ArrayList<>();
  private final AtomicReference<NodeRecord> record = new AtomicReference<>(NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build());
  private MockedStatic<J> scheduling;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<ViewerDisplayDirector> director;
  private MockedStatic<Adapt> messages;
  private MockedStatic<RosterGui> roster;
  private MockedStatic<GolemGui> golemGui;
  private MockedStatic<NodeGui> nodeGui;
  private World world;
  private Player player;
  private PlayerInventory inventory;
  private ItemStack crook;
  private ItemStack stick;
  private NodeService service;
  private Overseer.Commander commander;
  private NodeAreaSelector areas;

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    crook = mock(ItemStack.class);
    stick = mock(ItemStack.class);
    inventory = mock(PlayerInventory.class);
    lenient().when(inventory.getItemInMainHand()).thenReturn(crook);
    lenient().when(inventory.getItemInOffHand()).thenReturn(stick);
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(OWNER);
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(player.getWorld()).thenReturn(world);
    lenient().when(player.isOnline()).thenReturn(true);
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenReturn(true);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
      watches.add(invocation.getArgument(1));
      return true;
    });
    scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.kindOf(any(ItemStack.class))).thenReturn(Optional.empty());
    items.when(() -> GolemItems.kindOf(crook)).thenReturn(Optional.of(GolemItemKind.CROOK));
    service = mock(NodeService.class);
    lenient().when(service.node(KEY)).thenAnswer(invocation -> Optional.ofNullable(record.get()));
    lenient().when(service.update(eq(OWNER), eq(KEY), any())).thenAnswer(invocation -> {
      UnaryOperator<NodeRecord> change = invocation.getArgument(2);
      record.set(change.apply(record.get()));
      return Optional.of(record.get());
    });
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    lenient().when(policy.level(any(Player.class))).thenReturn(3);
    lenient().when(policy.maxAreaRadius(anyInt())).thenReturn(8);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
    bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(invocation -> mock(BlockData.class));
    director = mockStatic(ViewerDisplayDirector.class);
    director.when(() -> ViewerDisplayDirector.showLine(anyString(), anyString(), any(Player.class), any(Location.class), any(Location.class),
        any(BlockData.class), any(Color.class), anyDouble(), anyInt())).thenReturn(true);
    messages = mockStatic(Adapt.class);
    roster = mockStatic(RosterGui.class);
    golemGui = mockStatic(GolemGui.class);
    nodeGui = mockStatic(NodeGui.class);
    commander = mock(Overseer.Commander.class);
    Overseer.setCommander(commander);
    areas = new NodeAreaSelector();
  }

  @AfterEach
  void tearDown() {
    Overseer.clearCommander(commander);
    nodeGui.close();
    golemGui.close();
    roster.close();
    messages.close();
    director.close();
    bukkit.close();
    runtime.close();
    items.close();
    scheduling.close();
  }

  @Test
  void theMenuButtonClosesTheMenuAndStartsSelectingForTheOwnerOnly() {
    areas.toggle(player, KEY, NodeRecord.builder(STRANGER, NodeType.HARVEST, BlockFace.UP).build());
    assertThat(areas.selecting(player, KEY)).isFalse();
    verify(player, never()).closeInventory();

    areas.toggle(player, KEY, record.get());

    assertThat(areas.selecting(player, KEY)).isTrue();
    verify(player).closeInventory();
    assertThat(watches).hasSize(1);
  }

  @Test
  void leftAndRightClicksSetTheCornersAndSneakRightClickAppliesAndSaves() {
    areas.toggle(player, KEY, record.get());

    PlayerInteractEvent first = click(Action.LEFT_CLICK_BLOCK, block(14, 64, 8), false);
    PlayerInteractEvent second = click(Action.RIGHT_CLICK_BLOCK, block(9, 66, 11), false);
    PlayerInteractEvent apply = click(Action.RIGHT_CLICK_AIR, null, true);

    for (PlayerInteractEvent event : List.of(first, second, apply)) {
      verify(event).setUseInteractedBlock(Event.Result.DENY);
      verify(event).setUseItemInHand(Event.Result.DENY);
    }
    assertThat(record.get().area()).isEqualTo(new NodeCuboid(-1, -1, -2, 4, 1, 1));
    assertThat(areas.selecting(player, KEY)).isFalse();
    director.verify(() -> ViewerDisplayDirector.showLine(eq(NodeAreaSelector.CHANNEL), anyString(), eq(player), any(Location.class),
        any(Location.class), any(BlockData.class), any(Color.class), anyDouble(), anyInt()), times(12));
  }

  @Test
  void anInvalidSelectionIsNotSavedAndTheModeStaysOpen() {
    areas.toggle(player, KEY, record.get());
    click(Action.LEFT_CLICK_BLOCK, block(10, 65, 10), false);
    click(Action.RIGHT_CLICK_BLOCK, block(30, 65, 10), false);

    click(Action.RIGHT_CLICK_BLOCK, block(30, 65, 10), true);

    verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());
    assertThat(areas.selecting(player, KEY)).isTrue();
  }

  @Test
  void sneakLeftClickCancelsWithoutSaving() {
    areas.toggle(player, KEY, record.get());
    click(Action.LEFT_CLICK_BLOCK, block(12, 64, 8), false);
    click(Action.RIGHT_CLICK_BLOCK, block(9, 66, 11), false);

    click(Action.LEFT_CLICK_AIR, null, true);

    assertThat(areas.selecting(player, KEY)).isFalse();
    verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());
    director.verify(() -> ViewerDisplayDirector.clearViewer(NodeAreaSelector.CHANNEL, OWNER));
  }

  @Test
  void crookClicksInTheModeNeverReachSignalsTheRosterOrTheGolemGui() {
    CrookInteractions crookInteractions = new CrookInteractions(areas);
    areas.toggle(player, KEY, record.get());

    crookInteractions.on(click(Action.LEFT_CLICK_AIR, null, false));
    crookInteractions.on(click(Action.RIGHT_CLICK_AIR, null, true));
    assertThat(areas.selecting(player, KEY)).isTrue();
    IronGolem golem = mock(IronGolem.class);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.owner()).thenReturn(OWNER);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    PlayerInteractEntityEvent golemClick = entityClick(golem);
    areas.on(golemClick);
    crookInteractions.on(golemClick);
    crookInteractions.on(click(Action.LEFT_CLICK_AIR, null, true));

    assertThat(areas.selecting(player, KEY)).isFalse();
    verify(commander, never()).send(any(Player.class));
    verify(commander, never()).cycle(any(Player.class));
    roster.verify(() -> RosterGui.open(any(Player.class)), never());
    golemGui.verify(() -> GolemGui.open(any(), any(), any()), never());
    verify(golemClick).setCancelled(true);
  }

  @Test
  void crookClicksOnANodeInTheModeNeverOpenOrRemoveIt() {
    Interaction hitbox = mock(Interaction.class);
    NodeRenderer renderer = mock(NodeRenderer.class);
    lenient().when(renderer.nodeOf(hitbox)).thenReturn(Optional.of(KEY));
    lenient().when(service.renderer()).thenReturn(renderer);
    NodeInteractions nodeInteractions = new NodeInteractions(new NodeTagPrompt(mock(NodeTagLookup.class), areas), areas);
    areas.toggle(player, KEY, record.get());
    Block target = block(10, 64, 10);
    lenient().when(player.getTargetBlockExact(anyInt())).thenReturn(target);

    PlayerInteractEntityEvent open = entityClick(hitbox);
    areas.on(open);
    nodeInteractions.on(open);
    Cancellable attack = mock(Cancellable.class);
    lenient().when(player.isSneaking()).thenReturn(false);
    areas.attacked(player, attack);
    nodeInteractions.attacked(player, hitbox, attack);

    nodeGui.verify(() -> NodeGui.open(any(Player.class), any(NodeKey.class), any(NodeTagPrompt.class), any(NodeAreaSelector.class)), never());
    verify(service, never()).remove(any(Player.class), any(NodeKey.class));
    verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());
    verify(attack).setCancelled(true);
    assertThat(areas.selecting(player, KEY)).isTrue();
  }

  @Test
  void theClickThatEndsTheModeAndItsEchoStillNeverSignal() {
    CrookInteractions crookInteractions = new CrookInteractions(areas);
    areas.toggle(player, KEY, record.get());

    PlayerInteractEvent cancel = click(Action.LEFT_CLICK_AIR, null, true);
    crookInteractions.on(cancel);
    crookInteractions.on(click(Action.LEFT_CLICK_AIR, null, true));

    assertThat(areas.selecting(player, KEY)).isFalse();
    verify(commander, never()).cycle(any(Player.class));
  }

  @Test
  void outsideTheModeCrookClicksWorkAsUsual() {
    CrookInteractions crookInteractions = new CrookInteractions(areas);

    PlayerInteractEvent signal = click(Action.LEFT_CLICK_AIR, null, false);
    crookInteractions.on(signal);

    verify(signal, never()).setUseItemInHand(Event.Result.DENY);
    verify(commander).send(player);
  }

  @Test
  void clicksWithAnotherItemInHandAreLeftAloneDuringTheMode() {
    areas.toggle(player, KEY, record.get());
    when(inventory.getItemInMainHand()).thenReturn(stick);

    PlayerInteractEvent event = click(Action.LEFT_CLICK_BLOCK, block(12, 64, 8), false);

    verify(event, never()).setUseInteractedBlock(any());
    assertThat(areas.swallows(player)).isFalse();
  }

  @Test
  void blockBreaksWithTheCrookAreCancelledDuringTheMode() {
    areas.toggle(player, KEY, record.get());
    BlockBreakEvent breaking = new BlockBreakEvent(block(12, 64, 8), player);

    areas.on(breaking);

    assertThat(breaking.isCancelled()).isTrue();
  }

  @Test
  void quittingEndsTheMode() {
    areas.toggle(player, KEY, record.get());

    PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
    when(quit.getPlayer()).thenReturn(player);
    areas.on(quit);

    assertThat(areas.selecting(player, KEY)).isFalse();
    assertThat(areas.swallows(player)).isFalse();
  }

  @Test
  void removingTheNodeEndsTheModeAtTheNextWatch() {
    areas.toggle(player, KEY, record.get());
    record.set(null);

    watches.getFirst().run();

    assertThat(areas.selecting(player, KEY)).isFalse();
    assertThat(watches).hasSize(1);
  }

  @Test
  void theWatchKeepsRunningWhileTheModeLasts() {
    areas.toggle(player, KEY, record.get());

    watches.getFirst().run();

    assertThat(areas.selecting(player, KEY)).isTrue();
    assertThat(watches).hasSize(2);
  }

  private PlayerInteractEvent click(Action action, Block block, boolean sneaking) {
    lenient().when(player.isSneaking()).thenReturn(sneaking);
    ItemStack held = inventory.getItemInMainHand();
    PlayerInteractEvent event = mock(PlayerInteractEvent.class);
    lenient().when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    lenient().when(event.getItem()).thenReturn(held);
    lenient().when(event.getPlayer()).thenReturn(player);
    lenient().when(event.getAction()).thenReturn(action);
    lenient().when(event.getClickedBlock()).thenReturn(block);
    areas.on(event);
    return event;
  }

  private PlayerInteractEntityEvent entityClick(Entity target) {
    PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
    lenient().when(event.getPlayer()).thenReturn(player);
    lenient().when(event.getRightClicked()).thenReturn(target);
    lenient().when(event.getHand()).thenReturn(EquipmentSlot.HAND);
    return event;
  }

  private Block block(int x, int y, int z) {
    Block block = mock(Block.class);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getLocation()).thenReturn(new Location(world, x, y, z));
    return block;
  }
}
