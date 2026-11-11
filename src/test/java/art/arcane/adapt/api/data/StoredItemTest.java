package art.arcane.adapt.api.data;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.nms.NMS;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class StoredItemTest extends AdaptTestBase {
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
        throw new IllegalArgumentException("Material cannot be null");
      }
      return item;
    });
  }

  @AfterEach
  void tearDown() {
    nms.close();
  }

  @Test
  void storedBytesAreNotDecodedUntilTheItemIsAskedFor() throws IOException {
    item("sugar");

    StoredItem stored = StoredItem.stored(ascii("sugar"));
    assertThat(stored.length()).isEqualTo(5);
    assertThat(stored.bytes()).isEqualTo(ascii("sugar"));
    assertThat(written(stored)).isEqualTo(ascii("sugar"));
    assertThat(stored.isEmpty()).isFalse();
    assertThat(stored.sameItem(StoredItem.stored(ascii("sugar")))).isTrue();

    nms.verify(() -> NMS.deserializeStack(anyString()), never());
  }

  @Test
  void aStoredItemDecodesOnceAndHandsOutCopies() {
    ItemStack sugar = mock(ItemStack.class);
    ItemStack firstCopy = mock(ItemStack.class);
    ItemStack secondCopy = mock(ItemStack.class);
    when(sugar.clone()).thenReturn(firstCopy, secondCopy);
    decoded.put("sugar", sugar);
    StoredItem stored = StoredItem.stored(ascii("sugar"));

    assertThat(stored.readable()).isTrue();
    assertThat(stored.item()).isSameAs(firstCopy);
    assertThat(stored.item()).isSameAs(secondCopy);

    nms.verify(() -> NMS.deserializeStack("sugar"), times(1));
  }

  @Test
  void bytesThatDoNotDecodeStayExactlyAsStored() throws IOException {
    byte[] bytes = ascii("an item from another platform");
    StoredItem stored = StoredItem.stored(bytes);

    assertThat(stored.item()).isNull();
    assertThat(stored.readable()).isFalse();
    assertThat(stored.isEmpty()).isFalse();

    assertThat(stored.bytes()).isEqualTo(bytes);
    assertThat(written(stored)).isEqualTo(bytes);
    nms.verify(() -> NMS.deserializeStack(anyString()), times(1));
    nms.verify(() -> NMS.serializeStack(any(ItemStack.class)), never());
  }

  @Test
  void aStackThatDecodesToNothingIsUnreadableAndKeepsItsBytes() {
    nms.when(() -> NMS.deserializeStack(anyString())).thenReturn(null);
    StoredItem stored = StoredItem.stored(ascii("void"));

    assertThat(stored.readable()).isFalse();
    assertThat(stored.bytes()).isEqualTo(ascii("void"));
  }

  @Test
  void theStoredArrayCannotBeChangedFromOutside() {
    byte[] source = ascii("sugar");
    StoredItem stored = StoredItem.stored(source);

    source[0] = 'X';
    stored.bytes()[1] = 'Y';

    assertThat(stored.bytes()).isEqualTo(ascii("sugar"));
  }

  @Test
  void anUnreadableItemIsWarnedAboutOnce() {
    StoredItem stored = StoredItem.stored(ascii("broken"));

    try (MockedStatic<Adapt> log = mockStatic(Adapt.class)) {
      stored.warnUnreadable(() -> "first");
      stored.warnUnreadable(() -> "second");
      stored.item();
      stored.warnUnreadable(() -> "third");

      log.verify(() -> Adapt.warn(eq("first"), any(IllegalArgumentException.class)), times(1));
      log.verify(() -> Adapt.warn(anyString(), any(Throwable.class)), times(1));
    }
  }

  @Test
  void readableAndEmptyItemsAreNeverWarnedAbout() {
    item("sugar");

    try (MockedStatic<Adapt> log = mockStatic(Adapt.class)) {
      StoredItem.stored(ascii("sugar")).warnUnreadable(() -> "readable");
      StoredItem.stored(new byte[0]).warnUnreadable(() -> "empty");
      StoredItem.of(item("feather")).warnUnreadable(() -> "held");

      log.verifyNoInteractions();
    }
  }

  @Test
  void theZeroLengthMarkerIsEmptyAndNeverDecoded() throws IOException {
    StoredItem marker = StoredItem.stored(new byte[0]);

    assertThat(marker.isEmpty()).isTrue();
    assertThat(marker.readable()).isFalse();
    assertThat(marker.item()).isNull();
    assertThat(marker.length()).isZero();
    assertThat(written(marker)).isEmpty();
    nms.verify(() -> NMS.deserializeStack(anyString()), never());
  }

  @Test
  void aHeldItemIsEncodedOnceAndNeverDecoded() throws IOException {
    ItemStack sugar = item("sugar");
    StoredItem held = StoredItem.of(sugar);

    assertThat(held.item()).isSameAs(sugar);
    assertThat(held.isEmpty()).isFalse();
    nms.verify(() -> NMS.serializeStack(any(ItemStack.class)), never());

    assertThat(written(held)).isEqualTo(ascii("sugar"));
    assertThat(held.bytes()).isEqualTo(ascii("sugar"));
    assertThat(held.length()).isEqualTo(5);
    nms.verify(() -> NMS.serializeStack(sugar), times(1));
    nms.verify(() -> NMS.deserializeStack(anyString()), never());
  }

  @Test
  void aHeldItemKeepsItsOwnCopyOfTheStack() {
    ItemStack given = mock(ItemStack.class);
    ItemStack kept = mock(ItemStack.class);
    ItemStack handedOut = mock(ItemStack.class);
    when(given.clone()).thenReturn(kept);
    when(kept.clone()).thenReturn(handedOut);
    encoded.put(kept, "kept");

    StoredItem held = StoredItem.of(given);

    assertThat(held.item()).isSameAs(handedOut);
    assertThat(held.bytes()).isEqualTo(ascii("kept"));
    nms.verify(() -> NMS.serializeStack(given), never());
  }

  @Test
  void storedItemsAreTheSameOnlyWhenTheirBytesAre() {
    ItemStack sugar = item("sugar");
    decoded.put("sugar-again", sugar);
    StoredItem first = StoredItem.stored(ascii("sugar"));
    StoredItem second = StoredItem.stored(ascii("sugar-again"));

    assertThat(first.item()).isSameAs(second.item());
    assertThat(first.sameItem(second)).isFalse();
    assertThat(first.sameItem(StoredItem.stored(ascii("sugar")))).isTrue();
    assertThat(StoredItem.stored(new byte[0]).sameItem(StoredItem.stored(new byte[0]))).isTrue();
    assertThat(first.sameItem(StoredItem.stored(new byte[0]))).isFalse();
  }

  @Test
  void aSavedHeldItemIsTheSameAsItsBytesReadBack() throws IOException {
    StoredItem held = StoredItem.of(item("sugar"));
    StoredItem readBack = StoredItem.stored(ascii("sugar"));

    assertThat(held.sameItem(readBack)).isFalse();
    assertThat(readBack.sameItem(held)).isFalse();

    written(held);

    assertThat(held.sameItem(readBack)).isTrue();
    assertThat(readBack.sameItem(held)).isTrue();
    assertThat(held.sameItem(StoredItem.stored(ascii("feather")))).isFalse();
  }

  @Test
  void heldItemsCompareAsStacksUntilBothAreSaved() throws IOException {
    ItemStack sugar = item("sugar");
    ItemStack feather = item("feather");
    StoredItem saved = StoredItem.of(sugar);
    written(saved);

    assertThat(StoredItem.of(sugar).sameItem(StoredItem.of(sugar))).isTrue();
    assertThat(StoredItem.of(sugar).sameItem(StoredItem.of(feather))).isFalse();
    assertThat(saved.sameItem(StoredItem.of(sugar))).isTrue();
    assertThat(StoredItem.of(sugar).sameItem(saved)).isTrue();
    assertThat(saved.sameItem(StoredItem.of(feather))).isFalse();
    assertThat(saved.sameItem(saved)).isTrue();
  }

  private ItemStack item(String key) {
    ItemStack item = mock(ItemStack.class);
    when(item.clone()).thenReturn(item);
    encoded.put(item, key);
    decoded.put(key, item);
    return item;
  }

  private static byte[] ascii(String text) {
    return text.getBytes(StandardCharsets.US_ASCII);
  }

  private static byte[] written(StoredItem item) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(bytes)) {
      item.writeTo(out);
    }
    return bytes.toByteArray();
  }
}
