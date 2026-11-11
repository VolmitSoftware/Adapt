package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.data.AdaptSlices;
import art.arcane.adapt.nms.NMS;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PlinthSlotMatterTest extends AdaptTestBase {
  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

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
  void theIdAndItemSurviveARoundTrip() throws IOException {
    ItemStack sugar = item("sugar");

    PlinthSlotItem read = roundTrip(PlinthSlotItem.of(ID, sugar));

    assertThat(read.id()).isEqualTo(ID);
    assertThat(read.readable()).isTrue();
    assertThat(read.lost()).isFalse();
    assertThat(read.item()).isSameAs(sugar);
  }

  @Test
  void readingASlotDoesNotDecodeItsItem() throws IOException {
    PlinthSlotItem read = roundTrip(PlinthSlotItem.of(ID, item("sugar")));

    assertThat(read.stored().bytes()).isEqualTo(ascii("sugar"));
    assertThat(write(read)).isEqualTo(frame(ID, ascii("sugar")));
    nms.verify(() -> NMS.deserializeStack(anyString()), never());
  }

  @Test
  void anItemThatCannotBeDecodedKeepsItsBytesAndTheStreamStaysInStep() throws IOException {
    UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    ItemStack feather = item("feather");
    byte[] frames = join(frame(ID, ascii("an item this server cannot read")), frame(second, ascii("feather")));
    PlinthSlotMatter matter = new PlinthSlotMatter();

    PlinthSlotItem first;
    PlinthSlotItem next;
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frames))) {
      first = matter.readNode(in);
      next = matter.readNode(in);
    }

    assertThat(first.id()).isEqualTo(ID);
    assertThat(first.readable()).isFalse();
    assertThat(first.item()).isNull();
    assertThat(first.lost()).isFalse();
    assertThat(first.stored().bytes()).isEqualTo(ascii("an item this server cannot read"));
    assertThat(next.item()).isSameAs(feather);
    assertThat(join(write(first), write(next))).isEqualTo(frames);
  }

  @Test
  void anUndecodableItemIsByteIdenticalThroughRepeatedSaves() throws IOException {
    byte[] original = frame(ID, ascii("an item this server cannot read"));

    byte[] current = original;
    for (int cycle = 0; cycle < 5; cycle++) {
      PlinthSlotItem slot = read(current);
      assertThat(slot.readable()).as("cycle %d", cycle).isFalse();
      current = write(slot);
      assertThat(current).as("cycle %d", cycle).isEqualTo(original);
    }
    nms.verify(() -> NMS.serializeStack(any(ItemStack.class)), never());
  }

  @Test
  void anItemDecodesAgainOnceALoaderCanReadIt() throws IOException {
    byte[] stored = frame(ID, ascii("modded"));
    PlinthSlotItem before = read(stored);
    assertThat(before.readable()).isFalse();
    byte[] saved = write(before);

    ItemStack modded = item("modded");
    PlinthSlotItem after = read(saved);

    assertThat(after.readable()).isTrue();
    assertThat(after.item()).isSameAs(modded);
    assertThat(before.readable()).as("the outcome holds until the value is loaded again").isFalse();
  }

  @Test
  void theZeroLengthMarkerLoadsAsLostAndIsWrittenBackAsItIs() throws IOException {
    byte[] marker = frame(ID, new byte[0]);

    PlinthSlotItem read = read(marker);

    assertThat(read.id()).isEqualTo(ID);
    assertThat(read.lost()).isTrue();
    assertThat(read.readable()).isFalse();
    assertThat(read.item()).isNull();
    assertThat(write(read)).isEqualTo(marker);
    nms.verify(() -> NMS.deserializeStack(anyString()), never());
  }

  @Test
  void impossibleLengthsAreRejectedBeforeAllocating() throws IOException {
    assertThatThrownBy(() -> read(raw(-1))).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> read(raw(PlinthSlotMatter.MAX_ITEM_BYTES + 1))).isInstanceOf(IOException.class);
  }

  @Test
  void slotItemsAreEqualByIdAndStoredBytes() throws IOException {
    ItemStack sugar = item("sugar");
    PlinthSlotItem placed = PlinthSlotItem.of(ID, sugar);

    assertThat(PlinthSlotItem.stored(ID, ascii("sugar"))).isEqualTo(PlinthSlotItem.stored(ID, ascii("sugar")));
    assertThat(PlinthSlotItem.stored(ID, ascii("sugar"))).isNotEqualTo(PlinthSlotItem.stored(ID, ascii("feather")));
    assertThat(PlinthSlotItem.stored(ID, ascii("sugar"))).isNotEqualTo(PlinthSlotItem.stored(UUID.randomUUID(), ascii("sugar")));
    assertThat(PlinthSlotItem.stored(ID, ascii("sugar"))).isNotEqualTo(PlinthSlotItem.stored(ID, new byte[0]));
    assertThat(placed).isEqualTo(PlinthSlotItem.of(ID, sugar));
    assertThat(placed).isNotEqualTo(PlinthSlotItem.of(ID, item("feather")));
    assertThat(placed.hashCode()).isEqualTo(ID.hashCode());
    assertThat(PlinthSlotItem.stored(ID, ascii("sugar")).hashCode()).isEqualTo(ID.hashCode());
  }

  @Test
  void aPlacedItemEqualsItsOwnSlotReadBackFromTheFile() throws IOException {
    PlinthSlotItem placed = PlinthSlotItem.of(ID, item("sugar"));

    PlinthSlotItem reloaded = roundTrip(placed);

    assertThat(reloaded).isEqualTo(placed);
    assertThat(placed).isEqualTo(reloaded);
  }

  @Test
  void twoSlotsWithOneIdAndDifferentBytesStayApartInASection() throws IOException {
    PlinthSlotItem first = PlinthSlotItem.stored(ID, ascii("first unreadable"));
    PlinthSlotItem second = PlinthSlotItem.stored(ID, ascii("second unreadable"));
    PlinthSlotMatter section = new PlinthSlotMatter(16, 16, 16);
    section.set(1, 2, 3, first);
    section.set(4, 5, 6, second);

    assertThat(section.get(1, 2, 3).stored().bytes()).isEqualTo(ascii("first unreadable"));
    assertThat(section.get(4, 5, 6).stored().bytes()).isEqualTo(ascii("second unreadable"));

    PlinthSlotMatter reloaded = reload(section);

    assertThat(reloaded.get(1, 2, 3).stored().bytes()).isEqualTo(ascii("first unreadable"));
    assertThat(reloaded.get(4, 5, 6).stored().bytes()).isEqualTo(ascii("second unreadable"));
    assertThat(reload(reloaded).get(4, 5, 6).stored().bytes()).isEqualTo(ascii("second unreadable"));
  }

  @Test
  void replacingASlotWithTheSameIdStoresTheNewItem() {
    PlinthSlotMatter section = new PlinthSlotMatter(16, 16, 16);
    section.set(1, 2, 3, PlinthSlotItem.stored(ID, ascii("old")));

    section.set(1, 2, 3, PlinthSlotItem.stored(ID, ascii("new")));

    assertThat(section.get(1, 2, 3).stored().bytes()).isEqualTo(ascii("new"));
  }

  private static PlinthSlotMatter reload(PlinthSlotMatter section) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      section.write(out);
    }
    PlinthSlotMatter reloaded = new PlinthSlotMatter(16, 16, 16);
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      assertThat(in.readUTF()).isEqualTo(AdaptSlices.PLINTH_SLOT);
      reloaded.read(in);
      assertThat(in.available()).isZero();
    }
    return reloaded;
  }

  private ItemStack item(String key) {
    ItemStack item = mock(ItemStack.class);
    when(item.clone()).thenReturn(item);
    encoded.put(item, key);
    decoded.put(key, item);
    return item;
  }

  private static byte[] raw(int length) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeLong(ID.getMostSignificantBits());
      out.writeLong(ID.getLeastSignificantBits());
      out.writeInt(length);
      out.write("abc".getBytes(StandardCharsets.US_ASCII));
    }
    return bytes.toByteArray();
  }

  private static PlinthSlotItem read(byte[] bytes) throws IOException {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      return new PlinthSlotMatter().readNode(in);
    }
  }

  private static PlinthSlotItem roundTrip(PlinthSlotItem item) throws IOException {
    return read(write(item));
  }

  private static byte[] write(PlinthSlotItem item) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      new PlinthSlotMatter().writeNode(item, out);
    }
    return bytes.toByteArray();
  }

  // A slot as every release has framed it: the id, the item length, then the item bytes.
  private static byte[] frame(UUID id, byte[] item) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeLong(id.getMostSignificantBits());
      out.writeLong(id.getLeastSignificantBits());
      out.writeInt(item.length);
      out.write(item);
    }
    return bytes.toByteArray();
  }

  private static byte[] join(byte[] first, byte[] second) {
    byte[] joined = new byte[first.length + second.length];
    System.arraycopy(first, 0, joined, 0, first.length);
    System.arraycopy(second, 0, joined, first.length, second.length);
    return joined;
  }

  private static byte[] ascii(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }
}
