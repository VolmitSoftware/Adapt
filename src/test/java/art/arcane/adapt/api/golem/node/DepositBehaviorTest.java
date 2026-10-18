package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
  void anIgnoringDepositNeverReadsPower() {
    Block anchor = anchor(false);

    assertThat(NodeRules.active(deposit().build(), anchor)).isTrue();

    verify(anchor, never()).isBlockPowered();
    verify(anchor, never()).isBlockIndirectlyPowered();
  }

  private static NodeRecord.Builder deposit() {
    return NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.UP);
  }

  private static Block anchor(boolean powered) {
    Block block = mock(Block.class);
    lenient().when(block.isBlockPowered()).thenReturn(false);
    lenient().when(block.isBlockIndirectlyPowered()).thenReturn(powered);
    return block;
  }
}
