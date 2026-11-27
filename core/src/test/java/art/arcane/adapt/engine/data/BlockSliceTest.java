package art.arcane.adapt.engine.data;

import art.arcane.adapt.api.world.BlockSlice;

import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeFaces;
import art.arcane.adapt.content.golem.node.NodeCodec;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.content.golem.plinth.PlinthSlotCodec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.engine.addon.GolemData;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BlockSliceTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

  @BeforeAll
  static void declareGolemData() {
    GolemData.declare();
  }

  @Test
  void theGolemSlicesKeepTheirReleasedMantleIds() {
    assertThat(NodeCodec.slice().id()).isEqualTo(Id.adapt("node"));
    assertThat(NodeCodec.slice().type()).isEqualTo(NodeFaces.class);
    assertThat(PlinthSlotCodec.slice().id()).isEqualTo(Id.adapt("plinth_slot"));
    assertThat(PlinthSlotCodec.slice().type()).isEqualTo(PlinthSlotItem.class);
  }

  @Test
  void theNodeSliceCodecRoundTripsEveryFace() throws IOException {
    NodeFaces faces = NodeFaces.EMPTY
        .with(NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build())
        .with(NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.NORTH).channel(DyeColor.RED).priority(5)
            .filterTags(List.of("#minecraft:logs")).whitelist(false).build());

    NodeFaces read = roundTrip(NodeCodec.slice(), faces);

    assertThat(read).isEqualTo(faces);
  }

  @Test
  void thePlinthSliceCodecKeepsTheIdAndTheStoredItemBytes() throws IOException {
    UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    byte[] bytes = "an item".getBytes(StandardCharsets.US_ASCII);

    PlinthSlotItem read = roundTrip(PlinthSlotCodec.slice(), PlinthSlotItem.stored(id, bytes));

    assertThat(read.id()).isEqualTo(id);
    assertThat(read.stored().bytes()).isEqualTo(bytes);
    assertThat(roundTrip(PlinthSlotCodec.slice(), PlinthSlotItem.stored(id, new byte[0])).lost()).isTrue();
  }

  @Test
  void theNeutralStoreKeepsNothingAndAlwaysReportsSaved() {
    WorldRef world = mock(WorldRef.class);
    NodeFaces faces = NodeFaces.EMPTY.with(NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build());
    List<NodeFaces> visited = new ArrayList<>();

    BlockStorePort.NONE.set(world, 1, 64, 1, NodeCodec.slice(), faces);
    BlockStorePort.NONE.iterateChunk(world, 0, 0, NodeCodec.slice(), (int x, int y, int z, NodeFaces value) -> visited.add(value));

    assertThat(BlockStorePort.NONE.get(world, 1, 64, 1, NodeCodec.slice())).isNull();
    assertThat(visited).isEmpty();
    assertThat(BlockStorePort.NONE.savePlate(world, 1, 1)).isTrue();
    assertThat(BlockStorePort.current()).isSameAs(BlockStorePort.NONE);
  }

  private static <T> T roundTrip(BlockSlice<T> slice, T value) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      slice.codec().write(value, out);
    }
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return slice.codec().read(in);
    }
  }
}
