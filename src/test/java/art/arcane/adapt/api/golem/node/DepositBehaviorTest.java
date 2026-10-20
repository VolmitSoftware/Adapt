package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
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

    assertThat(DepositBehavior.accepts(record, GolemTestItems.stack(Material.WHEAT, 1))).isTrue();
    assertThat(DepositBehavior.accepts(record, GolemTestItems.stack(Material.COBBLESTONE, 1))).isTrue();
  }

  @Test
  void aWhitelistAcceptsOnlyItsItemsAndABlacklistEverythingElse() {
    NodeRecord whitelist = deposit().filterItems(List.of(GolemTestItems.stack(Material.WHEAT, 1))).build();
    NodeRecord blacklist = whitelist.toBuilder().whitelist(false).build();

    assertThat(DepositBehavior.accepts(whitelist, GolemTestItems.stack(Material.WHEAT, 5))).isTrue();
    assertThat(DepositBehavior.accepts(whitelist, GolemTestItems.stack(Material.CARROT, 5))).isFalse();
    assertThat(DepositBehavior.accepts(blacklist, GolemTestItems.stack(Material.WHEAT, 5))).isFalse();
    assertThat(DepositBehavior.accepts(blacklist, GolemTestItems.stack(Material.CARROT, 5))).isTrue();
  }

  @Test
  void redstoneGatingAppliesToDeliveries() {
    Block unpowered = anchor(false);
    Block powered = anchor(true);
    NodeRecord whilePowered = deposit().redstone(RedstoneMode.POWERED).build();
    NodeRecord whileUnpowered = deposit().redstone(RedstoneMode.UNPOWERED).build();

    assertThat(NodeRules.active(whilePowered, unpowered)).isFalse();
    assertThat(NodeRules.active(whilePowered, powered)).isTrue();
    assertThat(NodeRules.active(whileUnpowered, powered)).isFalse();
    assertThat(NodeRules.active(whileUnpowered, unpowered)).isTrue();
  }

  @Test
  void anAnchorBesideAnUnloadedChunkReadsAsUnknownPowerAndGatesBothModesOff() {
    World world = mock(World.class);
    lenient().when(world.isChunkLoaded(0, 0)).thenReturn(true);
    Block edge = anchor(world, 15, true);
    NodeRecord whilePowered = deposit().redstone(RedstoneMode.POWERED).build();
    NodeRecord whileUnpowered = deposit().redstone(RedstoneMode.UNPOWERED).build();

    assertThat(NodeRules.power(edge)).isEqualTo(NodePower.UNKNOWN);
    assertThat(NodeRules.active(whilePowered, edge)).isFalse();
    assertThat(NodeRules.active(whileUnpowered, edge)).isFalse();
    verify(edge, never()).isBlockPowered();
    verify(edge, never()).isBlockIndirectlyPowered();

    when(world.isChunkLoaded(1, 0)).thenReturn(true);

    assertThat(NodeRules.power(edge)).isEqualTo(NodePower.ON);
    assertThat(NodeRules.active(whilePowered, edge)).isTrue();
  }

  @Test
  void anIgnoringDepositNeverReadsPower() {
    Block anchor = anchor(false);

    assertThat(NodeRules.active(deposit().build(), anchor)).isTrue();

    verify(anchor, never()).isBlockPowered();
    verify(anchor, never()).isBlockIndirectlyPowered();
  }

  @Test
  void aDepositDeliversOnlyIntoAnAnchorThatIsStillAContainer() {
    UUID worldId = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    NodeKey key = new NodeKey(worldId, 0, 64, 0, BlockFace.UP);
    GolemTask task = GolemTask.builder(key, NodeType.DEPOSIT, "deliver", GolemTarget.block(worldId, 0, 64, 0)).build();
    DepositBehavior behavior = new DepositBehavior(mock(Adaptation.class));
    Inventory inventory = GolemTestItems.inventory(27);

    GolemContext hopper = golem(worldId, Material.HOPPER, inventory, deposit().build());
    assertThat(behavior.act(hopper, task)).isEqualTo(GolemActResult.FAILED);
    verify(hopper, never()).deliver(any(Inventory.class), any());

    GolemContext chest = golem(worldId, Material.CHEST, inventory, deposit().build());
    when(chest.deliver(any(Inventory.class), any())).thenReturn(1);
    assertThat(behavior.act(chest, task)).isEqualTo(GolemActResult.DONE);
    verify(chest).deliver(any(Inventory.class), any());
  }

  @Test
  void aDepositThatMovedItemsOpensTheAnchorLidAndOneThatMovedNothingDoesNot() {
    UUID worldId = UUID.fromString("00000000-0000-0000-0000-0000000000f3");
    NodeKey key = new NodeKey(worldId, 0, 64, 0, BlockFace.UP);
    GolemTask task = GolemTask.builder(key, NodeType.DEPOSIT, "deliver", GolemTarget.block(worldId, 0, 64, 0)).build();
    DepositBehavior behavior = new DepositBehavior(mock(Adaptation.class));
    GolemContext golem = golem(worldId, Material.CHEST, GolemTestItems.inventory(27), deposit().build());
    Block anchor = golem.world().getBlockAt(0, 64, 0);

    when(golem.deliver(any(Inventory.class), any())).thenReturn(0);
    assertThat(behavior.act(golem, task)).isEqualTo(GolemActResult.FAILED);
    verify(golem, never()).visit(any(Block.class));

    when(golem.deliver(any(Inventory.class), any())).thenReturn(3);
    assertThat(behavior.act(golem, task)).isEqualTo(GolemActResult.DONE);
    verify(golem).visit(anchor);
  }

  private static GolemContext golem(UUID worldId, Material anchorType, Inventory inventory, NodeRecord record) {
    World world = mock(World.class);
    Block anchor = mock(Block.class);
    Location at = new Location(world, 0, 64, 0);
    lenient().when(world.getUID()).thenReturn(worldId);
    lenient().when(world.getBlockAt(0, 64, 0)).thenReturn(anchor);
    lenient().when(anchor.getType()).thenReturn(anchorType);
    lenient().when(anchor.getLocation()).thenReturn(at);
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

  private static Block anchor(boolean powered) {
    World world = mock(World.class);
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    return anchor(world, 8, powered);
  }

  private static Block anchor(World world, int x, boolean powered) {
    Block block = mock(Block.class);
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getZ()).thenReturn(8);
    lenient().when(block.isBlockPowered()).thenReturn(false);
    lenient().when(block.isBlockIndirectlyPowered()).thenReturn(powered);
    return block;
  }
}
