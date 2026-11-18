package art.arcane.adapt.api.data;

import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeCodec;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.api.golem.plinth.PlinthSlotCodec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.world.WorldRef;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BlockSliceTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

  @Test
  void aSliceNeedsAnIdATypeAndACodec() {
    BlockSlice.Codec<String> codec = new BlockSlice.Codec<>() {
      @Override
      public void write(String value, DataOutputStream out) throws IOException {
        out.writeUTF(value);
      }

      @Override
      public String read(DataInputStream in) throws IOException {
        return in.readUTF();
      }
    };

    assertThatThrownBy(() -> new BlockSlice<>(null, String.class, codec)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new BlockSlice<>("adapt:text", null, codec)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new BlockSlice<String>("adapt:text", String.class, null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void theGolemSlicesKeepTheirReleasedMantleIds() {
    assertThat(NodeCodec.SLICE.id()).isEqualTo("adapt:node");
    assertThat(NodeCodec.SLICE.type()).isEqualTo(NodeFaces.class);
    assertThat(PlinthSlotCodec.SLICE.id()).isEqualTo("adapt:plinth_slot");
    assertThat(PlinthSlotCodec.SLICE.type()).isEqualTo(PlinthSlotItem.class);
  }

  @Test
  void theNodeSliceCodecRoundTripsEveryFace() throws IOException {
    NodeFaces faces = NodeFaces.EMPTY
        .with(NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build())
        .with(NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.NORTH).channel(DyeColor.RED).priority(5)
            .filterTags(List.of("#minecraft:logs")).whitelist(false).build());

    NodeFaces read = roundTrip(NodeCodec.SLICE, faces);

    assertThat(read).isEqualTo(faces);
  }

  @Test
  void thePlinthSliceCodecKeepsTheIdAndTheStoredItemBytes() throws IOException {
    UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    byte[] bytes = "an item".getBytes(StandardCharsets.US_ASCII);

    PlinthSlotItem read = roundTrip(PlinthSlotCodec.SLICE, PlinthSlotItem.stored(id, bytes));

    assertThat(read.id()).isEqualTo(id);
    assertThat(read.stored().bytes()).isEqualTo(bytes);
    assertThat(roundTrip(PlinthSlotCodec.SLICE, PlinthSlotItem.stored(id, new byte[0])).lost()).isTrue();
  }

  @Test
  void theNeutralStoreKeepsNothingAndAlwaysReportsSaved() {
    WorldRef world = mock(WorldRef.class);
    NodeFaces faces = NodeFaces.EMPTY.with(NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build());
    List<NodeFaces> visited = new ArrayList<>();

    BlockStorePort.NONE.set(world, 1, 64, 1, NodeCodec.SLICE, faces);
    BlockStorePort.NONE.iterateChunk(world, 0, 0, NodeCodec.SLICE, (int x, int y, int z, NodeFaces value) -> visited.add(value));

    assertThat(BlockStorePort.NONE.get(world, 1, 64, 1, NodeCodec.SLICE)).isNull();
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
