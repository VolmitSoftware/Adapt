package art.arcane.adapt.content.data;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StoredItemTest extends AdaptTestBase {
  private final Map<ItemRef, String> encoded = new HashMap<>();
  private final Map<String, ItemRef> decoded = new HashMap<>();
  private ItemOps items;

  @BeforeEach
  void setUp() {
    items = mock(ItemOps.class);
    when(core.getPlatform().host().items()).thenReturn(items);
    when(items.encode(any(ItemRef.class))).thenAnswer(invocation -> encoded.get(invocation.<ItemRef>getArgument(0)));
    when(items.decode(anyString())).thenAnswer(invocation -> {
      ItemRef item = decoded.get(invocation.<String>getArgument(0));
      if (item == null) {
        throw new IllegalArgumentException("Material cannot be null");
      }
      return item;
    });
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

    verify(items, never()).decode(anyString());
  }

  @Test
  void aStoredItemDecodesOnceAndHandsOutCopies() {
    ItemRef sugar = mock(ItemRef.class);
    ItemRef firstCopy = mock(ItemRef.class);
    ItemRef secondCopy = mock(ItemRef.class);
    when(sugar.copy()).thenReturn(firstCopy, secondCopy);
    decoded.put("sugar", sugar);
    StoredItem stored = StoredItem.stored(ascii("sugar"));

    assertThat(stored.readable()).isTrue();
    assertThat(stored.item()).isSameAs(firstCopy);
    assertThat(stored.item()).isSameAs(secondCopy);

    verify(items, times(1)).decode("sugar");
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
    verify(items, times(1)).decode(anyString());
    verify(items, never()).encode(any(ItemRef.class));
  }

  @Test
  void aStackThatDecodesToNothingIsUnreadableAndKeepsItsBytes() {
    doReturn(null).when(items).decode(anyString());
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

    PlatformLog log = mock(PlatformLog.class);
    try (MockedStatic<AdaptApi> api = mockStatic(AdaptApi.class, CALLS_REAL_METHODS)) {
      api.when(AdaptApi::log).thenReturn(log);
      stored.warnUnreadable(() -> "first");
      stored.warnUnreadable(() -> "second");
      stored.item();
      stored.warnUnreadable(() -> "third");

      verify(log, times(1)).error(eq("first"), any(IllegalArgumentException.class));
      verify(log, times(1)).error(anyString(), any(Throwable.class));
    }
  }

  @Test
  void readableAndEmptyItemsAreNeverWarnedAbout() {
    item("sugar");

    PlatformLog log = mock(PlatformLog.class);
    try (MockedStatic<AdaptApi> api = mockStatic(AdaptApi.class, CALLS_REAL_METHODS)) {
      api.when(AdaptApi::log).thenReturn(log);
      StoredItem.stored(ascii("sugar")).warnUnreadable(() -> "readable");
      StoredItem.stored(new byte[0]).warnUnreadable(() -> "empty");
      StoredItem.of(item("feather")).warnUnreadable(() -> "held");

      verifyNoInteractions(log);
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
    verify(items, never()).decode(anyString());
  }

  @Test
  void aHeldItemIsEncodedOnceAndNeverDecoded() throws IOException {
    ItemRef sugar = item("sugar");
    StoredItem held = StoredItem.of(sugar);

    assertThat(held.item()).isSameAs(sugar);
    assertThat(held.isEmpty()).isFalse();
    verify(items, never()).encode(any(ItemRef.class));

    assertThat(written(held)).isEqualTo(ascii("sugar"));
    assertThat(held.bytes()).isEqualTo(ascii("sugar"));
    assertThat(held.length()).isEqualTo(5);
    verify(items, times(1)).encode(sugar);
    verify(items, never()).decode(anyString());
  }

  @Test
  void aHeldItemKeepsItsOwnCopyOfTheStack() {
    ItemRef given = mock(ItemRef.class);
    ItemRef kept = mock(ItemRef.class);
    ItemRef handedOut = mock(ItemRef.class);
    when(given.copy()).thenReturn(kept);
    when(kept.copy()).thenReturn(handedOut);
    encoded.put(kept, "kept");

    StoredItem held = StoredItem.of(given);

    assertThat(held.item()).isSameAs(handedOut);
    assertThat(held.bytes()).isEqualTo(ascii("kept"));
    verify(items, never()).encode(given);
  }

  @Test
  void storedItemsAreTheSameOnlyWhenTheirBytesAre() {
    ItemRef sugar = item("sugar");
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
    ItemRef sugar = item("sugar");
    ItemRef feather = item("feather");
    StoredItem saved = StoredItem.of(sugar);
    written(saved);

    assertThat(StoredItem.of(sugar).sameItem(StoredItem.of(sugar))).isTrue();
    assertThat(StoredItem.of(sugar).sameItem(StoredItem.of(feather))).isFalse();
    assertThat(saved.sameItem(StoredItem.of(sugar))).isTrue();
    assertThat(StoredItem.of(sugar).sameItem(saved)).isTrue();
    assertThat(saved.sameItem(StoredItem.of(feather))).isFalse();
    assertThat(saved.sameItem(saved)).isTrue();
  }

  private ItemRef item(String key) {
    ItemRef item = mock(ItemRef.class);
    when(item.copy()).thenReturn(item);
    when(item.amount()).thenReturn(1);
    when(item.isSimilar(any(ItemRef.class))).thenAnswer(invocation -> invocation.getArgument(0) == item);
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
