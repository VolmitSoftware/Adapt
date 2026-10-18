package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.nms.NMS;
import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class NodeMatterTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  private final Map<ItemStack, String> encoded = new HashMap<>();
  private final Map<String, ItemStack> decoded = new HashMap<>();
  private MockedStatic<NMS> nms;

  @BeforeEach
  void setUp() {
    nms = mockStatic(NMS.class);
    nms.when(() -> NMS.serializeStack(any(ItemStack.class))).thenAnswer(invocation -> encoded.get(invocation.<ItemStack>getArgument(0)));
    nms.when(() -> NMS.deserializeStack(anyString())).thenAnswer(invocation -> {
      ItemStack item = decoded.get(invocation.<String>getArgument(0));
      if (item == null) {
        throw new IllegalArgumentException("corrupt stack");
      }
      return item;
    });
  }

  @AfterEach
  void tearDown() {
    nms.close();
  }

  @Test
  void everyFieldOfEveryFaceSurvivesARoundTrip() throws IOException {
    ItemStack cobble = item("cobble");
    ItemStack wheat = item("wheat");
    NodeRecord deposit = NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.NORTH)
        .channel(DyeColor.LIGHT_BLUE).priority(5).redstone(RedstoneMode.UNPOWERED).radii(4, 1, 6)
        .filterItems(List.of(cobble, wheat)).filterTags(List.of("#logs", "#minecraft:wool")).whitelist(false)
        .data(Map.of("overflow", "true", "restock", "16")).build();
    NodeRecord guard = NodeRecord.builder(OTHER, NodeType.GUARD, BlockFace.UP).build();
    NodeFaces faces = NodeFaces.EMPTY.with(deposit).with(guard);

    NodeFaces read = roundTrip(faces);

    assertThat(read).isEqualTo(faces);
    assertThat(read.get(BlockFace.NORTH)).isEqualTo(deposit);
    assertThat(read.get(BlockFace.NORTH).filterItems()).containsExactly(cobble, wheat);
    assertThat(read.get(BlockFace.UP)).isEqualTo(guard);
    assertThat(read.get(BlockFace.UP).channel()).isNull();
    assertThat(read.get(BlockFace.SOUTH)).isNull();
  }

  @Test
  void anEmptyBlockRoundTripsToEmpty() throws IOException {
    assertThat(roundTrip(NodeFaces.EMPTY).isEmpty()).isTrue();
  }

  @Test
  void aCorruptRecordIsSkippedWithoutLosingTheOtherFaces() throws IOException {
    ItemStack lost = item("lost");
    NodeRecord broken = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST).filterItems(List.of(lost)).build();
    NodeRecord intact = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.WEST).radii(2, 2, 2).build();
    NodeFaces faces = NodeFaces.EMPTY.with(broken).with(intact);
    byte[] bytes = write(faces);
    decoded.remove("lost");

    NodeFaces read = read(bytes);

    assertThat(read.get(BlockFace.EAST)).isNull();
    assertThat(read.get(BlockFace.WEST)).isEqualTo(intact);
  }

  @Test
  void theStreamStaysAlignedForTheNextValueAfterASkippedRecord() throws IOException {
    NodeRecord unknown = NodeRecord.builder(OWNER, NodeType.LUMBER, BlockFace.DOWN).filterItems(List.of(item("gone"))).build();
    NodeRecord next = NodeRecord.builder(OTHER, NodeType.BREAKER, BlockFace.SOUTH).priority(1).build();
    NodeMatter matter = new NodeMatter();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      matter.writeNode(NodeFaces.EMPTY.with(unknown), out);
      matter.writeNode(NodeFaces.EMPTY.with(next), out);
    }
    decoded.remove("gone");

    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      assertThat(matter.readNode(in).isEmpty()).isTrue();
      assertThat(matter.readNode(in).get(BlockFace.SOUTH)).isEqualTo(next);
    }
  }

  @Test
  void theCodecIsTheSliceForNodeFaces() {
    assertThat(new NodeMatter().getType()).isEqualTo(NodeFaces.class);
  }

  private ItemStack item(String id) {
    ItemStack item = mock(ItemStack.class);
    encoded.put(item, id);
    decoded.put(id, item);
    return item;
  }

  private NodeFaces roundTrip(NodeFaces faces) throws IOException {
    return read(write(faces));
  }

  private static byte[] write(NodeFaces faces) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      new NodeMatter().writeNode(faces, out);
    }
    return bytes.toByteArray();
  }

  private static NodeFaces read(byte[] bytes) throws IOException {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      return new NodeMatter().readNode(in);
    }
  }
}
