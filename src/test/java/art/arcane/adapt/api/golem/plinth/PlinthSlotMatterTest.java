package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.nms.NMS;
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

class PlinthSlotMatterTest extends AdaptTestBase {
  private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

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
  void theIdAndItemSurviveARoundTrip() throws IOException {
    ItemStack sugar = item("sugar");

    PlinthSlotItem read = roundTrip(PlinthSlotItem.of(ID, sugar));

    assertThat(read.id()).isEqualTo(ID);
    assertThat(read.readable()).isTrue();
    assertThat(read.item()).isSameAs(sugar);
  }

  @Test
  void anUnreadableStackKeepsItsIdAndTheStreamStaysInStep() throws IOException {
    ItemStack sugar = item("sugar");
    ItemStack feather = item("feather");
    decoded.remove("sugar");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PlinthSlotMatter matter = new PlinthSlotMatter();
    UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      matter.writeNode(PlinthSlotItem.of(ID, sugar), out);
      matter.writeNode(PlinthSlotItem.of(second, feather), out);
    }

    PlinthSlotItem first;
    PlinthSlotItem next;
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      first = matter.readNode(in);
      next = matter.readNode(in);
    }

    assertThat(first.id()).isEqualTo(ID);
    assertThat(first.readable()).isFalse();
    assertThat(first.item()).isNull();
    assertThat(next.item()).isSameAs(feather);
  }

  @Test
  void anUnreadableRecordRoundTripsAsUnreadable() throws IOException {
    PlinthSlotItem read = roundTrip(PlinthSlotItem.unreadable(ID));

    assertThat(read.id()).isEqualTo(ID);
    assertThat(read.readable()).isFalse();
  }

  @Test
  void impossibleLengthsAreRejectedBeforeAllocating() throws IOException {
    assertThatThrownBy(() -> read(raw(-1))).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> read(raw(PlinthSlotMatter.MAX_ITEM_BYTES + 1))).isInstanceOf(IOException.class);
  }

  @Test
  void slotItemsAreEqualByIdOnly() {
    ItemStack sugar = item("sugar");
    ItemStack feather = item("feather");

    assertThat(PlinthSlotItem.of(ID, sugar)).isEqualTo(PlinthSlotItem.of(ID, feather));
    assertThat(PlinthSlotItem.of(ID, sugar)).isNotEqualTo(PlinthSlotItem.of(UUID.randomUUID(), sugar));
    assertThat(PlinthSlotItem.of(ID, sugar).hashCode()).isEqualTo(ID.hashCode());
  }

  private ItemStack item(String key) {
    ItemStack item = mock(ItemStack.class);
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
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      new PlinthSlotMatter().writeNode(item, out);
    }
    return read(bytes.toByteArray());
  }
}
