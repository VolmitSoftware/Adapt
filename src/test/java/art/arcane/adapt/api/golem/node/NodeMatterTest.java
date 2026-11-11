package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.data.AdaptSlices;
import art.arcane.adapt.api.data.StoredItem;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.nms.NMS;
import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class NodeMatterTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  private final Map<ItemStack, String> encoded = new HashMap<>();
  private final Map<String, ItemStack> decoded = new HashMap<>();
  private MockedStatic<NMS> nms;

  @BeforeAll
  static void registerSlices() {
    AdaptSlices.register();
  }

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
        .channel(DyeColor.LIGHT_BLUE).priority(5).redstone(RedstoneMode.UNPOWERED).area(new NodeCuboid(-4, 0, -32, 9, 1, 6))
        .filterItems(List.of(cobble, wheat)).filterTags(List.of("#logs", "#minecraft:wool")).whitelist(false)
        .data(Map.of("overflow", "true", "restock", "16")).build();
    NodeRecord guard = NodeRecord.builder(OTHER, NodeType.GUARD, BlockFace.UP).build();
    NodeFaces faces = NodeFaces.EMPTY.with(deposit).with(guard);

    NodeFaces read = roundTrip(faces);

    assertThat(read).isEqualTo(faces);
    assertThat(read.get(BlockFace.NORTH)).isEqualTo(deposit);
    assertThat(read.get(BlockFace.NORTH).filterItems()).containsExactly(cobble, wheat);
    assertThat(read.get(BlockFace.NORTH).area()).isEqualTo(deposit.area());
    assertThat(read.get(BlockFace.UP)).isEqualTo(guard);
    assertThat(read.get(BlockFace.UP).area()).isEqualTo(NodeRecord.DEFAULT_AREA);
    assertThat(read.get(BlockFace.UP).channel()).isNull();
    assertThat(read.get(BlockFace.SOUTH)).isNull();
  }

  @Test
  void anEmptyBlockRoundTripsToEmpty() throws IOException {
    assertThat(roundTrip(NodeFaces.EMPTY).isEmpty()).isTrue();
  }

  @Test
  void readingANodeDoesNotDecodeItsFilterItems() throws IOException {
    NodeRecord filter = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST)
        .filterItems(List.of(item("cobble"), item("wheat"))).filterTags(List.of("#logs")).build();
    byte[] bytes = write(NodeFaces.EMPTY.with(filter));

    NodeFaces read = read(bytes);

    assertThat(read.get(BlockFace.EAST).filterCount()).isEqualTo(3);
    assertThat(read.get(BlockFace.EAST).filterEntries()).extracting(StoredItem::bytes).containsExactly(ascii("cobble"), ascii("wheat"));
    assertThat(read).isEqualTo(NodeFaces.EMPTY.with(filter));
    assertThat(read.hashCode()).isEqualTo(NodeFaces.EMPTY.with(filter).hashCode());
    assertThat(read.toString()).contains("filter=3");
    assertThat(write(read)).isEqualTo(bytes);
    nms.verify(() -> NMS.deserializeStack(anyString()), never());
  }

  @Test
  void anUnreadableFilterItemKeepsTheNodeItsFilterSlotAndItsBytes() throws IOException {
    ItemStack lost = item("lost");
    ItemStack kept = item("kept");
    NodeRecord broken = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST).channel(DyeColor.RED).priority(4)
        .filterItems(List.of(lost, kept)).filterTags(List.of("#logs")).whitelist(false).data(Map.of("restock", "8")).build();
    NodeRecord intact = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.WEST).area(NodeCuboid.symmetric(2, 2, 2)).build();
    byte[] bytes = write(NodeFaces.EMPTY.with(broken).with(intact));
    decoded.remove("lost");

    NodeFaces read = read(bytes);

    assertThat(read.get(BlockFace.WEST)).isEqualTo(intact);
    NodeRecord degraded = read.get(BlockFace.EAST);
    assertThat(degraded.filterItems()).containsExactly(kept);
    assertThat(degraded.unreadableFilters()).isEqualTo(1);
    assertThat(degraded.filterCount()).isEqualTo(3);
    assertThat(degraded.filterEntries()).extracting(StoredItem::bytes).containsExactly(ascii("lost"), ascii("kept"));
    assertThat(degraded.channel()).isEqualTo(DyeColor.RED);
    assertThat(degraded.priority()).isEqualTo(4);
    assertThat(degraded.whitelist()).isFalse();
    assertThat(degraded.filterTags()).containsExactly("#logs");
    assertThat(degraded.data()).isEqualTo(Map.of("restock", "8"));
    assertThat(write(read)).isEqualTo(bytes);
  }

  @Test
  void anUndecodableFilterItemIsByteIdenticalThroughRepeatedSaves() throws IOException {
    NodeRecord broken = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST)
        .filterItems(List.of(item("kept"), item("lost"), item("also lost"))).build();
    byte[] original = write(NodeFaces.EMPTY.with(broken));
    decoded.remove("lost");
    decoded.remove("also lost");
    nms.clearInvocations();

    byte[] current = original;
    for (int cycle = 0; cycle < 5; cycle++) {
      NodeFaces faces = read(current);
      assertThat(faces.get(BlockFace.EAST).unreadableFilters()).as("cycle %d", cycle).isEqualTo(2);
      current = write(faces);
      assertThat(current).as("cycle %d", cycle).isEqualTo(original);
    }
    nms.verify(() -> NMS.serializeStack(any(ItemStack.class)), never());
  }

  @Test
  void aFilterItemDecodesAgainOnceALoaderCanReadIt() throws IOException {
    ItemStack modded = item("modded");
    NodeRecord filter = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST).filterItems(List.of(item("kept"), modded)).build();
    byte[] bytes = write(NodeFaces.EMPTY.with(filter));
    decoded.remove("modded");
    NodeFaces before = read(bytes);
    assertThat(before.get(BlockFace.EAST).unreadableFilters()).isEqualTo(1);
    byte[] saved = write(before);

    decoded.put("modded", modded);
    NodeRecord after = read(saved).get(BlockFace.EAST);

    assertThat(after.unreadableFilters()).isZero();
    assertThat(after.filterItems()).containsExactly(decoded.get("kept"), modded);
    assertThat(before.get(BlockFace.EAST).unreadableFilters()).as("the outcome holds until the node is loaded again").isEqualTo(1);
  }

  @Test
  void anUnreadableFilterItemIsWarnedAboutOncePerLoad() throws IOException {
    NodeRecord broken = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST).filterItems(List.of(item("lost"))).build();
    byte[] bytes = write(NodeFaces.EMPTY.with(broken));
    decoded.remove("lost");
    nms.clearInvocations();

    NodeFaces first;
    NodeFaces second;
    try (MockedStatic<Adapt> log = mockStatic(Adapt.class)) {
      first = read(bytes);
      log.verifyNoInteractions();
      NodeRecord record = first.get(BlockFace.EAST);
      for (int use = 0; use < 3; use++) {
        assertThat(record.filterItems()).isEmpty();
        assertThat(record.unreadableFilters()).isEqualTo(1);
      }
      NodeRecord edited = record.toBuilder().priority(1).filterItems(List.of()).build();
      assertThat(edited.unreadableFilters()).isEqualTo(1);
      assertThat(edited.filterEntries()).extracting(StoredItem::bytes).containsExactly(ascii("lost"));
      second = roundTrip(first);
      log.verify(() -> Adapt.warn(and(and(contains("filter node on face EAST"), contains(OWNER.toString())), contains("could not be read")),
          any(IllegalArgumentException.class)), times(1));
      log.verify(() -> Adapt.warn(anyString(), any(Throwable.class)), times(1));
      log.verify(() -> Adapt.warn(anyString()), never());
    }

    assertThat(second).isEqualTo(first);
    nms.verify(() -> NMS.deserializeStack(anyString()), times(1));
  }

  @Test
  void aStackThatDecodesToNothingCountsAsUnreadableAndKeepsItsBytes() throws IOException {
    NodeRecord broken = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST).filterItems(List.of(item("void"))).build();
    byte[] bytes = write(NodeFaces.EMPTY.with(broken));
    nms.when(() -> NMS.deserializeStack(anyString())).thenReturn(null);

    NodeFaces read = read(bytes);

    assertThat(read.get(BlockFace.EAST).unreadableFilters()).isEqualTo(1);
    assertThat(write(read)).isEqualTo(bytes);
  }

  @Test
  void aZeroLengthFilterMarkerStillLoadsAndStaysAsItIs() throws IOException {
    ItemStack kept = item("kept");
    byte[] bytes = block(BlockFace.UP, filterRecord(true, ascii("kept"), new byte[0]));

    NodeFaces read;
    try (MockedStatic<Adapt> log = mockStatic(Adapt.class)) {
      read = read(bytes);
      NodeRecord record = read.get(BlockFace.UP);
      assertThat(record.filterItems()).containsExactly(kept);
      assertThat(record.unreadableFilters()).isEqualTo(1);
      assertThat(record.filterCount()).isEqualTo(2);
      assertThat(record.filterEntries().get(1).isEmpty()).isTrue();
      log.verifyNoInteractions();
    }

    assertThat(write(read)).isEqualTo(bytes);
    nms.verify(() -> NMS.deserializeStack(anyString()), times(1));
  }

  @Test
  void recordsWithDifferentFilterBytesAreDifferentValues() throws IOException {
    ItemStack same = item("first");
    decoded.put("second", same);
    NodeFaces first = read(block(BlockFace.UP, filterRecord(true, ascii("first"))));
    NodeFaces second = read(block(BlockFace.UP, filterRecord(true, ascii("second"))));
    NodeFaces unreadable = read(block(BlockFace.UP, filterRecord(true, ascii("gone a"))));
    NodeFaces otherUnreadable = read(block(BlockFace.UP, filterRecord(true, ascii("gone b"))));

    assertThat(first.get(BlockFace.UP).filterItems()).isEqualTo(second.get(BlockFace.UP).filterItems());
    assertThat(first).isNotEqualTo(second);
    assertThat(unreadable).isNotEqualTo(otherUnreadable);
    assertThat(unreadable).isEqualTo(read(block(BlockFace.UP, filterRecord(true, ascii("gone a")))));
    assertThat(first).isEqualTo(read(block(BlockFace.UP, filterRecord(true, ascii("first")))));
  }

  @Test
  void blocksThatDifferOnlyInUnreadableBytesStayApartInASection() throws IOException {
    NodeFaces first = read(block(BlockFace.UP, filterRecord(false, ascii("gone a"))));
    NodeFaces second = read(block(BlockFace.UP, filterRecord(false, ascii("gone b"))));
    NodeMatter section = new NodeMatter(16, 16, 16);
    section.set(1, 2, 3, first);
    section.set(4, 5, 6, second);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      section.write(out);
    }
    NodeMatter reloaded = new NodeMatter(16, 16, 16);
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      assertThat(in.readUTF()).isEqualTo(AdaptSlices.NODE);
      reloaded.read(in);
    }

    assertThat(section.get(4, 5, 6).get(BlockFace.UP).filterEntries()).extracting(StoredItem::bytes).containsExactly(ascii("gone b"));
    assertThat(reloaded.get(1, 2, 3).get(BlockFace.UP).filterEntries()).extracting(StoredItem::bytes).containsExactly(ascii("gone a"));
    assertThat(reloaded.get(4, 5, 6).get(BlockFace.UP).filterEntries()).extracting(StoredItem::bytes).containsExactly(ascii("gone b"));
  }

  @Test
  void aCorruptRecordRejectsTheWholeBlock() throws IOException {
    NodeRecord broken = NodeRecord.builder(OWNER, NodeType.LUMBER, BlockFace.EAST).build();
    NodeRecord intact = NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.WEST).area(NodeCuboid.symmetric(2, 2, 2)).build();
    byte[] bytes = unknownType(write(NodeFaces.EMPTY.with(broken).with(intact)), NodeType.LUMBER);

    assertThatThrownBy(() -> read(bytes)).isInstanceOf(IOException.class).hasCauseInstanceOf(IOException.class);
  }

  @Test
  void aCorruptRecordThrowsInsteadOfPublishingAnEmptyValue() throws IOException {
    NodeRecord unknown = NodeRecord.builder(OWNER, NodeType.LUMBER, BlockFace.DOWN).build();
    NodeRecord next = NodeRecord.builder(OTHER, NodeType.BREAKER, BlockFace.SOUTH).priority(1).build();
    NodeMatter matter = new NodeMatter();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      matter.writeNode(NodeFaces.EMPTY.with(unknown), out);
      matter.writeNode(NodeFaces.EMPTY.with(next), out);
    }

    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(unknownType(bytes.toByteArray(), NodeType.LUMBER)))) {
      assertThatThrownBy(() -> matter.readNode(in)).isInstanceOf(IOException.class);
      assertThat(matter.readNode(in).get(BlockFace.SOUTH)).isEqualTo(next);
    }
  }

  @Test
  void aBlockClaimingMoreThanSixFacesIsRejected() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeByte(7);
    }

    assertThatThrownBy(() -> read(bytes.toByteArray())).isInstanceOf(IOException.class).hasMessageContaining("7 faces");
  }

  @Test
  void negativeAndHugeRecordLengthsAreRejectedBeforeAllocating() throws IOException {
    for (int length : new int[]{-1, NodeMatter.MAX_RECORD_BYTES + 1, Integer.MAX_VALUE}) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(bytes)) {
        out.writeByte(1);
        out.writeUTF("UP");
        out.writeInt(length);
      }

      assertThatThrownBy(() -> read(bytes.toByteArray())).as("length %s", length).isInstanceOf(IOException.class).hasMessageContaining(String.valueOf(length));
    }
  }

  @Test
  void aFilterItemLengthPastTheRecordRejectsTheWholeBlock() throws IOException {
    NodeRecord intact = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build();
    byte[] good = write(NodeFaces.EMPTY.with(intact));
    ByteArrayOutputStream record = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(record)) {
      out.writeLong(OWNER.getMostSignificantBits());
      out.writeLong(OWNER.getLeastSignificantBits());
      out.writeUTF("filter");
      out.writeUTF("");
      out.writeByte(3);
      out.writeUTF("ignore");
      writeArea(out, NodeCuboid.symmetric(1, 1, 1));
      out.writeBoolean(true);
      out.writeByte(1);
      out.writeInt(5000);
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeByte(2);
      out.writeUTF("EAST");
      out.writeInt(record.size());
      out.write(record.toByteArray());
      out.write(good, 1, good.length - 1);
    }

    assertThatThrownBy(() -> read(bytes.toByteArray())).isInstanceOf(IOException.class);
  }

  @Test
  void aStoredAreaThatMissesItsCenterOrPassesTheLimitRejectsTheWholeBlock() throws IOException {
    NodeRecord intact = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).build();
    byte[] good = write(NodeFaces.EMPTY.with(intact));
    for (NodeCuboid stored : new NodeCuboid[]{new NodeCuboid(1, 0, 0, 4, 2, 2), new NodeCuboid(-33, 0, 0, 0, 0, 0)}) {
      ByteArrayOutputStream record = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(record)) {
        out.writeLong(OWNER.getMostSignificantBits());
        out.writeLong(OWNER.getLeastSignificantBits());
        out.writeUTF("guard");
        out.writeUTF("");
        out.writeByte(3);
        out.writeUTF("ignore");
        writeArea(out, stored);
        out.writeBoolean(true);
        out.writeByte(0);
        out.writeByte(0);
        out.writeShort(0);
      }
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(bytes)) {
        out.writeByte(2);
        out.writeUTF("EAST");
        out.writeInt(record.size());
        out.write(record.toByteArray());
        out.write(good, 1, good.length - 1);
      }

      assertThatThrownBy(() -> read(bytes.toByteArray())).as("stored area %s", stored).isInstanceOf(IOException.class);
    }
  }

  @Test
  void canonicalWideStoredCoordinatesAreNormalizedUsingTheCurrentRuntimeBounds() throws IOException {
    NodeCuboid wide = NodeCuboid.symmetric(NodeRecord.MAX_RADIUS, NodeRecord.MAX_RADIUS, NodeRecord.MAX_RADIUS);
    ByteArrayOutputStream record = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(record)) {
      out.writeLong(OWNER.getMostSignificantBits());
      out.writeLong(OWNER.getLeastSignificantBits());
      out.writeUTF("guard");
      out.writeUTF("");
      out.writeByte(3);
      out.writeUTF("ignore");
      writeArea(out, wide);
      out.writeBoolean(true);
      out.writeByte(0);
      out.writeByte(0);
      out.writeShort(0);
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeByte(1);
      out.writeUTF("UP");
      out.writeInt(record.size());
      out.write(record.toByteArray());
    }
    NodeCuboid bounded = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).area(wide).build().area();
    assertThat(read(bytes.toByteArray()).get(BlockFace.UP).area()).isEqualTo(bounded);
  }

  @Test
  void theCodecIsTheSliceForNodeFaces() {
    assertThat(new NodeMatter().getType()).isEqualTo(NodeFaces.class);
  }

  // One block holding a single record, framed as every release has framed it.
  private static byte[] block(BlockFace face, byte[] record) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeByte(1);
      out.writeUTF(face.name());
      out.writeInt(record.length);
      out.write(record);
    }
    return bytes.toByteArray();
  }

  // A filter node record whose filter items are the given byte runs, each behind its length.
  private static byte[] filterRecord(boolean whitelist, byte[]... items) throws IOException {
    ByteArrayOutputStream record = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(record)) {
      out.writeLong(OWNER.getMostSignificantBits());
      out.writeLong(OWNER.getLeastSignificantBits());
      out.writeUTF("filter");
      out.writeUTF("");
      out.writeByte(3);
      out.writeUTF("ignore");
      writeArea(out, NodeRecord.DEFAULT_AREA);
      out.writeBoolean(whitelist);
      out.writeByte(items.length);
      for (byte[] item : items) {
        out.writeInt(item.length);
        out.write(item);
      }
      out.writeByte(0);
      out.writeShort(0);
    }
    return record.toByteArray();
  }

  private static byte[] ascii(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }

  private ItemStack item(String id) {
    ItemStack item = mock(ItemStack.class);
    when(item.clone()).thenReturn(item);
    encoded.put(item, id);
    decoded.put(id, item);
    return item;
  }

  // Overwrites the first stored id of that node type with one no release knows, keeping every length the same.
  private static byte[] unknownType(byte[] bytes, NodeType type) {
    byte[] id = type.id().getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index + id.length <= bytes.length; index++) {
      if (Arrays.equals(bytes, index, index + id.length, id, 0, id.length)) {
        byte[] patched = bytes.clone();
        Arrays.fill(patched, index, index + id.length, (byte) '?');
        return patched;
      }
    }
    throw new IllegalStateException("No " + type.id() + " node in the encoded block");
  }

  private static void writeArea(DataOutputStream out, NodeCuboid area) throws IOException {
    out.writeByte(area.minX());
    out.writeByte(area.minY());
    out.writeByte(area.minZ());
    out.writeByte(area.maxX());
    out.writeByte(area.maxY());
    out.writeByte(area.maxZ());
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
