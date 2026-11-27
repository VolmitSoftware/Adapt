package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeFilters;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeRenderer;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.content.golem.node.NodeTagLookup;
import art.arcane.adapt.content.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PrePlayerAttackEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<NodeGui> gui;
  private NodeService service;
  private EntityRef hitbox;
  private ItemRef crook;
  private ItemRef dye;
  private ItemRef air;
  private int level = 1;
  private NodeRecord record = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build();
  private NodeInteractions interactions;

  @BeforeEach
  void setUp() {
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    hitbox = entity(Id.minecraft("interaction"));
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
    lenient().when(policy.level(any(PlayerRef.class))).thenAnswer(invocation -> level);
    lenient().when(policy.filterSlots(any(Integer.class))).thenReturn(3);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::nodes).thenReturn(Optional.of(service));
    runtime.when(GolemancyRuntime::nodeWeaving).thenReturn(Optional.of(policy));
    crook = stack(Id.minecraft("iron_hoe"));
    dye = stack(Id.minecraft("red_dye"));
    air = stack(Id.minecraft("air"));
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.empty());
    items.when(() -> GolemItems.kindOf(crook)).thenReturn(Optional.of(GolemItemKind.CROOK));
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    lenient().when(AdaptCore.platform().host().world(WORLD)).thenReturn(world);
    gui = mockStatic(NodeGui.class);
    gui.when(() -> NodeGui.level(any(PlayerRef.class))).thenAnswer(invocation -> level);
    gui.when(() -> NodeGui.filterSlots(any(Integer.class))).thenReturn(3);
    interactions = new NodeInteractions(prompt, areas);
  }

  @AfterEach
  void tearDown() {
    gui.close();
    scheduling.close();
    items.close();
    runtime.close();
  }

  @Test
  void rightClickingYourNodeWithTheCrookOpensItsMenu() {
    PlayerRef owner = player(OWNER, crook, air, false);
    PlayerInteractEntityEvent click = rightClick(owner, hitbox, Hand.MAIN_HAND);

    interactions.on(click);

    assertThat(click.isCancelled()).isTrue();
    gui.verify(() -> NodeGui.open(owner, KEY, prompt, areas));
  }

  @Test
  void theOffHandEchoIsCancelledWithoutActingTwice() {
    PlayerRef owner = player(OWNER, crook, air, false);
    PlayerInteractEntityEvent click = rightClick(owner, hitbox, Hand.OFF_HAND);

    interactions.on(click);

    assertThat(click.isCancelled()).isTrue();
    gui.verify(() -> NodeGui.open(any(PlayerRef.class), any(NodeKey.class), any(NodeTagPrompt.class), any(NodeAreaSelector.class)), never());
  }

  @Test
  void entitiesThatAreNotNodeHitboxesAreLeftAlone() {
    PlayerRef owner = player(OWNER, crook, air, false);
    PlayerInteractEntityEvent click = rightClick(owner, entity(Id.minecraft("zombie")), Hand.MAIN_HAND);

    interactions.on(click);

    assertThat(click.isCancelled()).isFalse();
  }

  @Test
  void strangersCannotConfigureSomeoneElsesNode() {
    PlayerRef stranger = player(STRANGER, crook, air, false);

    interactions.on(rightClick(stranger, hitbox, Hand.MAIN_HAND));
    interactions.on(attack(stranger, hitbox));

    gui.verify(() -> NodeGui.open(any(PlayerRef.class), any(NodeKey.class), any(NodeTagPrompt.class), any(NodeAreaSelector.class)), never());
    verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());
  }

  @Test
  void leftClickingWithTheCrookCyclesTheChannelAndCancelsTheAttack() {
    PlayerRef owner = player(OWNER, crook, air, false);
    PrePlayerAttackEntityEvent attack = attack(owner, hitbox);

    interactions.on(attack);
    interactions.on(attack(owner, hitbox));

    assertThat(attack.isCancelled()).isTrue();
    assertThat(record.channel()).isEqualTo(DyeColor.ORANGE);
  }

  @Test
  void aDyeWithTheCrookInTheOffHandSetsThatChannel() {
    PlayerRef owner = player(OWNER, dye, crook, false);

    interactions.on(rightClick(owner, hitbox, Hand.MAIN_HAND));

    assertThat(record.channel()).isEqualTo(DyeColor.RED);
  }

  @Test
  void anyOtherItemWithTheCrookInTheOffHandTogglesItsGhostInTheFilter() {
    ItemRef held = stack(Id.minecraft("wheat"));
    PlayerRef owner = player(OWNER, held, crook, false);
    ItemRef ghost = stack(Id.minecraft("wheat"));
    try (MockedStatic<NodeFilters> filters = mockStatic(NodeFilters.class,
        invocation -> invocation.getMethod().getName().equals("ghost") ? ghost : invocation.callRealMethod())) {

      interactions.on(rightClick(owner, hitbox, Hand.MAIN_HAND));
      assertThat(record.filterItems()).containsExactly(ghost);

      interactions.on(rightClick(owner, hitbox, Hand.MAIN_HAND));
      assertThat(record.filterItems()).isEmpty();
      filters.verify(() -> NodeFilters.ghost(held), times(2));
    }
  }

  @Test
  void sneakRightClickCyclesPriorityOnlyFromLevelThree() {
    PlayerRef owner = player(OWNER, crook, air, true);

    interactions.on(rightClick(owner, hitbox, Hand.MAIN_HAND));
    assertThat(record.priority()).isEqualTo(NodeRecord.DEFAULT_PRIORITY);
    verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());

    level = 3;
    interactions.on(rightClick(owner, hitbox, Hand.MAIN_HAND));
    assertThat(record.priority()).isEqualTo(NodeRecord.DEFAULT_PRIORITY + 1);
  }

  @Test
  void sneakLeftClickRemovesTheNodeThroughTheService() {
    PlayerRef owner = player(OWNER, crook, air, true);
    when(service.remove(owner, KEY)).thenReturn(true);

    interactions.on(attack(owner, hitbox));

    verify(service).remove(owner, KEY);
    verify(service, never()).update(any(UUID.class), any(NodeKey.class), any());
  }

  private PlayerRef player(UUID id, ItemRef mainHand, ItemRef offHand, boolean sneaking) {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    lenient().when(player.uuid()).thenReturn(id);
    lenient().when(player.isSneaking()).thenReturn(sneaking);
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(inventory.mainHand()).thenReturn(mainHand);
    lenient().when(inventory.offHand()).thenReturn(offHand);
    return player;
  }

  private static PlayerInteractEntityEvent rightClick(PlayerRef player, EntityRef target, Hand hand) {
    return new PlayerInteractEntityEvent(player, target, hand, Vec3.ZERO);
  }

  private static PrePlayerAttackEntityEvent attack(PlayerRef player, EntityRef target) {
    return new PrePlayerAttackEntityEvent(player, target, true);
  }

  private static EntityRef entity(Id type) {
    EntityRef entity = mock(EntityRef.class);
    lenient().when(entity.type()).thenReturn(type);
    return entity;
  }

  private static ItemRef stack(Id type) {
    ItemRef item = mock(ItemRef.class);
    lenient().when(item.type()).thenReturn(type);
    lenient().when(item.copy()).thenReturn(item);
    lenient().when(item.isSimilar(item)).thenReturn(true);
    return item;
  }
}
