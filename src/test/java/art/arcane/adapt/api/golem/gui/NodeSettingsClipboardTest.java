package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NodeSettingsClipboardTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  @AfterEach
  void cleanup() {
    NodeSettingsClipboard.forget(OWNER);
  }

  @Test
  void commonSettingsCopyWithoutReplacingDestinationIdentityOrRoleOptions() {
    NodeRecord source = NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.NORTH).channel(DyeColor.RED).priority(5)
        .redstone(RedstoneMode.POWERED).area(new NodeCuboid(-3, -2, 0, 12, 2, 0)).filterTags(List.of("#stone"))
        .whitelist(false).data(Map.of("silk", "true")).build();
    NodeRecord destination = NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.SOUTH).data(Map.of("fortune", "true")).build();
    assertThat(NodeSettingsClipboard.copy(OWNER, source)).isTrue();
    NodeRecord pasted = NodeSettingsClipboard.pasted(OWNER, destination, 5, 15, 18).orElseThrow();
    assertThat(pasted.owner()).isEqualTo(OWNER);
    assertThat(pasted.face()).isEqualTo(BlockFace.SOUTH);
    assertThat(pasted.type()).isEqualTo(NodeType.BREAKER);
    assertThat(pasted.data()).containsExactlyEntriesOf(destination.data());
    assertThat(pasted.channel()).isEqualTo(DyeColor.RED);
    assertThat(pasted.area()).isEqualTo(source.area());
    assertThat(pasted.priority()).isEqualTo(5);
    assertThat(pasted.redstone()).isEqualTo(RedstoneMode.POWERED);
    assertThat(pasted.whitelist()).isFalse();
    assertThat(pasted.filterTags()).containsExactly("#stone");
  }

  @Test
  void anotherOwnerOrTypeCannotPaste() {
    NodeRecord source = NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.UP).build();
    NodeSettingsClipboard.copy(OWNER, source);
    assertThat(NodeSettingsClipboard.pasted(OWNER, NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP).build(), 5, 15, 18)).isEmpty();
    assertThat(NodeSettingsClipboard.pasted(UUID.randomUUID(), source, 5, 15, 18)).isEmpty();
    assertThat(NodeSettingsClipboard.copy(UUID.randomUUID(), source)).isFalse();
    NodeSettingsClipboard.forget(OWNER);
    assertThat(NodeSettingsClipboard.pasted(OWNER, source, 5, 15, 18)).isEmpty();
  }

  @Test
  void lockedSettingsOrReducedCapacityRejectTheWholeCopy() {
    NodeRecord destination = NodeRecord.builder(OWNER, NodeType.BREAKER, BlockFace.UP).build();
    for (NodeRecord source : List.of(destination.toBuilder().priority(5).build(),
        destination.toBuilder().redstone(RedstoneMode.POWERED).build(), destination.toBuilder().whitelist(false).build(),
        destination.toBuilder().filterTags(List.of("#stone")).build(), destination.toBuilder().area(new NodeCuboid(0, 0, 0, 15, 0, 0)).build())) {
      NodeSettingsClipboard.copy(OWNER, source);
      assertThat(NodeSettingsClipboard.pasted(OWNER, destination, 1, 8, 3)).isEmpty();
      assertThat(destination.priority()).isEqualTo(NodeRecord.DEFAULT_PRIORITY);
    }
    NodeSettingsClipboard.copy(OWNER, destination.toBuilder().filterTags(List.of("#stone", "#logs")).build());
    assertThat(NodeSettingsClipboard.pasted(OWNER, destination, 5, 15, 1)).isEmpty();
  }
}
