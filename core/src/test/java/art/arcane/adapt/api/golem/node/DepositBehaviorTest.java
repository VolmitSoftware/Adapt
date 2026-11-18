package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.value.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepositBehaviorTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

  @Test
  void anEmptyFilterAcceptsEveryItem() {
    NodeRecord record = deposit().build();

    assertThat(DepositBehavior.accepts(record, NodeTestItems.stack(Id.minecraft("wheat"), 1))).isTrue();
    assertThat(DepositBehavior.accepts(record, NodeTestItems.stack(Id.minecraft("cobblestone"), 1))).isTrue();
  }

  @Test
  void aWhitelistAcceptsOnlyItsItemsAndABlacklistEverythingElse() {
    NodeRecord whitelist = deposit().filterItems(List.of(NodeTestItems.stack(Id.minecraft("wheat"), 1))).build();
    NodeRecord blacklist = whitelist.toBuilder().whitelist(false).build();

    assertThat(DepositBehavior.accepts(whitelist, NodeTestItems.stack(Id.minecraft("wheat"), 5))).isTrue();
    assertThat(DepositBehavior.accepts(whitelist, NodeTestItems.stack(Id.minecraft("carrot"), 5))).isFalse();
    assertThat(DepositBehavior.accepts(blacklist, NodeTestItems.stack(Id.minecraft("wheat"), 5))).isFalse();
    assertThat(DepositBehavior.accepts(blacklist, NodeTestItems.stack(Id.minecraft("carrot"), 5))).isTrue();
  }

  @Test
  void redstoneGatingAppliesToDeliveries() {
    BlockRef unpowered = anchor(false);
    BlockRef powered = anchor(true);
    NodeRecord whilePowered = deposit().redstone(RedstoneMode.POWERED).build();
    NodeRecord whileUnpowered = deposit().redstone(RedstoneMode.UNPOWERED).build();

    assertThat(NodeRules.active(whilePowered, unpowered)).isFalse();
    assertThat(NodeRules.active(whilePowered, powered)).isTrue();
    assertThat(NodeRules.active(whileUnpowered, powered)).isFalse();
    assertThat(NodeRules.active(whileUnpowered, unpowered)).isTrue();
  }

  @Test
  void anAnchorBesideAnUnloadedChunkReadsAsUnknownPowerAndGatesBothModesOff() {
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.isChunkLoaded(0, 0)).thenReturn(true);
    BlockRef edge = anchor(world, 15, true);
    NodeRecord whilePowered = deposit().redstone(RedstoneMode.POWERED).build();
    NodeRecord whileUnpowered = deposit().redstone(RedstoneMode.UNPOWERED).build();

    assertThat(NodeRules.power(edge)).isEqualTo(NodePower.UNKNOWN);
    assertThat(NodeRules.active(whilePowered, edge)).isFalse();
    assertThat(NodeRules.active(whileUnpowered, edge)).isFalse();
    verify(edge, never()).isRedstonePowered();

    when(world.isChunkLoaded(1, 0)).thenReturn(true);

    assertThat(NodeRules.power(edge)).isEqualTo(NodePower.ON);
    assertThat(NodeRules.active(whilePowered, edge)).isTrue();
  }

  @Test
  void anIgnoringDepositNeverReadsPower() {
    BlockRef anchor = anchor(false);

    assertThat(NodeRules.active(deposit().build(), anchor)).isTrue();

    verify(anchor, never()).isRedstonePowered();
  }

  @Test
  void aDepositDeliversOnlyIntoAnAnchorThatIsStillAContainer() {
    UUID worldId = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    NodeKey key = new NodeKey(worldId, 0, 64, 0, BlockFace.UP);
    GolemTask task = GolemTask.builder(key, NodeType.DEPOSIT, "deliver", GolemTarget.block(worldId, 0, 64, 0)).build();
    DepositBehavior behavior = new DepositBehavior(mock(Adaptation.class));
    InventoryRef inventory = NodeTestItems.inventory(27);

    GolemContext hopper = golem(worldId, Id.minecraft("hopper"), inventory, deposit().build());
    assertThat(behavior.act(hopper, task)).isEqualTo(GolemActResult.FAILED);
    verify(hopper, never()).deliver(any(InventoryRef.class), any());

    GolemContext chest = golem(worldId, Id.minecraft("chest"), inventory, deposit().build());
    when(chest.deliver(any(InventoryRef.class), any())).thenReturn(1);
    assertThat(behavior.act(chest, task)).isEqualTo(GolemActResult.DONE);
    verify(chest).deliver(any(InventoryRef.class), any());
  }

  @Test
  void aDepositThatMovedItemsOpensTheAnchorLidAndOneThatMovedNothingDoesNot() {
    UUID worldId = UUID.fromString("00000000-0000-0000-0000-0000000000f3");
    NodeKey key = new NodeKey(worldId, 0, 64, 0, BlockFace.UP);
    GolemTask task = GolemTask.builder(key, NodeType.DEPOSIT, "deliver", GolemTarget.block(worldId, 0, 64, 0)).build();
    DepositBehavior behavior = new DepositBehavior(mock(Adaptation.class));
    GolemContext golem = golem(worldId, Id.minecraft("chest"), NodeTestItems.inventory(27), deposit().build());
    BlockRef anchor = golem.world().blockAt(0, 64, 0);

    when(golem.deliver(any(InventoryRef.class), any())).thenReturn(0);
    assertThat(behavior.act(golem, task)).isEqualTo(GolemActResult.FAILED);
    verify(golem, never()).visit(any(BlockRef.class));

    when(golem.deliver(any(InventoryRef.class), any())).thenReturn(3);
    assertThat(behavior.act(golem, task)).isEqualTo(GolemActResult.DONE);
    verify(golem).visit(anchor);
  }

  private static GolemContext golem(UUID worldId, Id anchorType, InventoryRef inventory, NodeRecord record) {
    WorldRef world = mock(WorldRef.class);
    BlockRef anchor = mock(BlockRef.class);
    Loc at = new Loc(world, 0, 64, 0);
    lenient().when(world.uid()).thenReturn(worldId);
    lenient().when(world.blockAt(0, 64, 0)).thenReturn(anchor);
    lenient().when(anchor.type()).thenReturn(anchorType);
    lenient().when(anchor.location()).thenReturn(at);
    GolemContext golem = mock(GolemContext.class);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.node()).thenReturn(Optional.of(record));
    lenient().when(golem.canAccessChest(at)).thenReturn(true);
    lenient().when(golem.container(anchor)).thenReturn(Optional.of(inventory));
    return golem;
  }

  private static NodeRecord.Builder deposit() {
    return NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP);
  }

  private static BlockRef anchor(boolean powered) {
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    return anchor(world, 8, powered);
  }

  private static BlockRef anchor(WorldRef world, int x, boolean powered) {
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.world()).thenReturn(world);
    lenient().when(block.x()).thenReturn(x);
    lenient().when(block.z()).thenReturn(8);
    lenient().when(block.isRedstonePowered()).thenReturn(powered);
    return block;
  }
}
