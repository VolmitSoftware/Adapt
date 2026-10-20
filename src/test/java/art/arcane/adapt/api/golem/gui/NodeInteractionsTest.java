package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRenderer;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.event.ProtectionProbe;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

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

class NodeInteractionsTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final NodeKey KEY = new NodeKey(WORLD, 3, 64, 3, BlockFace.UP);

  private final NodeAreaSelector areas = new NodeAreaSelector();
  private final NodeTagPrompt prompt = new NodeTagPrompt(mock(NodeTagLookup.class), areas);
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemItems> items;
  private MockedStatic<J> scheduling;
  private MockedStatic<Bukkit> bukkit;
  private MockedStatic<NodeGui> gui;
  private NodeService service;
  private Interaction hitbox;
  private ItemStack crook;
  private ItemStack dye;
  private ItemStack air;
  private int level = 1;
  private NodeRecord record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build();
  private NodeInteractions interactions;

  @BeforeEach
  void setUp() {
    World world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    hitbox = mock(Interaction.class);
    NodeRenderer renderer = mock(NodeRenderer.class);
    lenient().when(renderer.nodeOf(hitbox)).thenReturn(Optional.of(KEY));
    service = mock(NodeService.class);
    lenient().when(service.renderer()).thenReturn(renderer);
    lenient().when(service.node(KEY)).thenAnswer(invocation -> Optional.of(record));
    lenient().when(service.update(any(UUID.class), eq(KEY), any())).thenAnswer(invocation -> {
      UnaryOperator<NodeRecord> change = invocation.getArgument(2);
      record = change.apply(record);
      return Optional.of(record);
    });
    NodeWeavingPolicy policy = mock(NodeWeavingPolicy.class);
    lenient().when(policy.level(any(Player.class))).thenAnswer(invocation -> level);
    lenient().when(policy.filterSlots(any(Integer.class))).thenReturn(3);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
    crook = stack(Material.IRON_HOE);
    dye = stack(Material.RED_DYE);
    air = stack(Material.AIR);
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.kindOf(any(ItemStack.class))).thenReturn(Optional.empty());
    items.when(() -> GolemItems.kindOf(crook)).thenReturn(Optional.of(GolemItemKind.CROOK));
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.getWorld(WORLD)).thenReturn(world);
    gui = mockStatic(NodeGui.class);
    gui.when(() -> NodeGui.level(any(Player.class))).thenAnswer(invocation -> level);
    gui.when(() -> NodeGui.filterSlots(any(Integer.class))).thenReturn(3);
    interactions = new NodeInteractions(prompt, areas);
  }

  @AfterEach
  void tearDown() {
    gui.close();
    bukkit.close();
    scheduling.close();
    items.close();
    runtime.close();
  }

  @Test
  void protectionProbeClicksLeaveNodesAlone() {
    Player owner = player(OWNER, crook, air, false);
    PlayerInteractEntityEvent probe = ProtectionProbe.entityInteract(owner, hitbox);

    interactions.on(probe);

    assertThat(probe.isCancelled()).isFalse();
    gui.verify(() -> NodeGui.open(any(Player.class), any(NodeKey.class), any(NodeTagPrompt.class), any(NodeAreaSelector.class)), never());
  }

  @Test
  void rightClickingYourNodeWithTheCrookOpensItsMenu() {
    Player owner = player(OWNER, crook, air, false);
    PlayerInteractEntityEvent click = rightClick(owner, hitbox, EquipmentSlot.HAND);

    interactions.on(click);

    verify(click).setCancelled(true);
    gui.verify(() -> NodeGui.open(owner, KEY, prompt, areas));
  }

  @Test
  void theOffHandEchoIsCancelledWithoutActingTwice() {
    Player owner = player(OWNER, crook, air, false);
    PlayerInteractEntityEvent click = rightClick(owner, hitbox, EquipmentSlot.OFF_HAND);

    interactions.on(click);

    verify(click).setCancelled(true);
    gui.verify(() -> NodeGui.open(any(Player.class), any(NodeKey.class), any(NodeTagPrompt.class), any(NodeAreaSelector.class)), never());
  }

  @Test
  void entitiesThatAreNotNodeHitboxesAreLeftAlone() {
    Player owner = player(OWNER, crook, air, false);
    PlayerInteractEntityEvent click = rightClick(owner, mock(Zombie.class), EquipmentSlot.HAND);

    interactions.on(click);

    verify(click, never()).setCancelled(true);
  }

  @Test
  void strangersCannotConfigureSomeoneElsesNode() {
    Player stranger = player(STRANGER, crook, air, false);

    interactions.on(rightClick(stranger, hitbox, EquipmentSlot.HAND));
    interactions.attacked(stranger, hitbox, mock(Cancellable.class));

    gui.verify(() -> NodeGui.open(any(Player.class), any(NodeKey.class), any(NodeTagPrompt.class), any(NodeAreaSelector.class)), never());
    verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());
  }

  @Test
  void leftClickingWithTheCrookCyclesTheChannelAndCancelsTheAttack() {
    Player owner = player(OWNER, crook, air, false);
    Cancellable attack = mock(Cancellable.class);

    interactions.attacked(owner, hitbox, attack);
    interactions.attacked(owner, hitbox, mock(Cancellable.class));

    verify(attack).setCancelled(true);
    assertThat(record.channel()).isEqualTo(DyeColor.ORANGE);
  }

  @Test
  void aDyeWithTheCrookInTheOffHandSetsThatChannel() {
    Player owner = player(OWNER, dye, crook, false);

    interactions.on(rightClick(owner, hitbox, EquipmentSlot.HAND));

    assertThat(record.channel()).isEqualTo(DyeColor.RED);
  }

  @Test
  void anyOtherItemWithTheCrookInTheOffHandTogglesItsGhostInTheFilter() {
    ItemStack held = stack(Material.WHEAT);
    Player owner = player(OWNER, held, crook, false);
    ItemStack ghost = stack(Material.WHEAT);
    try (MockedStatic<NodeFilters> filters = mockStatic(NodeFilters.class,
        invocation -> invocation.getMethod().getName().equals("ghost") ? ghost : invocation.callRealMethod())) {

      interactions.on(rightClick(owner, hitbox, EquipmentSlot.HAND));
      assertThat(record.filterItems()).containsExactly(ghost);

      interactions.on(rightClick(owner, hitbox, EquipmentSlot.HAND));
      assertThat(record.filterItems()).isEmpty();
      filters.verify(() -> NodeFilters.ghost(held), times(2));
    }
  }

  @Test
  void sneakRightClickCyclesPriorityOnlyFromLevelThree() {
    Player owner = player(OWNER, crook, air, true);

    interactions.on(rightClick(owner, hitbox, EquipmentSlot.HAND));
    assertThat(record.priority()).isEqualTo(NodeRecord.DEFAULT_PRIORITY);
    verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());

    level = 3;
    interactions.on(rightClick(owner, hitbox, EquipmentSlot.HAND));
    assertThat(record.priority()).isEqualTo(NodeRecord.DEFAULT_PRIORITY + 1);
  }

  @Test
  void sneakLeftClickRemovesTheNodeThroughTheService() {
    Player owner = player(OWNER, crook, air, true);
    when(service.remove(owner, KEY)).thenReturn(true);

    interactions.attacked(owner, hitbox, mock(Cancellable.class));

    verify(service).remove(owner, KEY);
    verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());
  }

  private Player player(UUID id, ItemStack mainHand, ItemStack offHand, boolean sneaking) {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    lenient().when(player.getUniqueId()).thenReturn(id);
    lenient().when(player.isSneaking()).thenReturn(sneaking);
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(inventory.getItemInMainHand()).thenReturn(mainHand);
    lenient().when(inventory.getItemInOffHand()).thenReturn(offHand);
    return player;
  }

  private static PlayerInteractEntityEvent rightClick(Player player, Entity target, EquipmentSlot hand) {
    PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
    lenient().when(event.getPlayer()).thenReturn(player);
    lenient().when(event.getRightClicked()).thenReturn(target);
    lenient().when(event.getHand()).thenReturn(hand);
    return event;
  }

  private static ItemStack stack(Material type) {
    ItemStack item = mock(ItemStack.class);
    lenient().when(item.getType()).thenReturn(type);
    lenient().when(item.clone()).thenReturn(item);
    lenient().when(item.isSimilar(item)).thenReturn(true);
    return item;
  }
}
