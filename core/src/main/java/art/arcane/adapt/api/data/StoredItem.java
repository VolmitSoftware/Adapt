package art.arcane.adapt.api.data;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.item.ItemRef;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One item inside a mantle value. Bytes read from disk are kept as they are, decoded on first use on the caller's
 * thread and written back unchanged, so an item this server cannot decode is still in the file for a later load.
 */
public final class StoredItem {
  private final boolean held;
  private volatile byte[] bytes;
  private volatile ItemRef item;
  private volatile RuntimeException failure;
  private volatile boolean decoded;
  private boolean warned;

  private StoredItem(ItemRef item, byte[] bytes) {
    this.held = item != null;
    this.item = item;
    this.bytes = bytes;
    this.decoded = held;
  }

  // An item the game hands in is copied here and encoded once, on its first save.
  public static StoredItem of(ItemRef item) {
    return new StoredItem(Objects.requireNonNull(Objects.requireNonNull(item).copy()), null);
  }

  public static StoredItem stored(byte[] bytes) {
    return new StoredItem(null, bytes.clone());
  }

  /**
   * A copy of the item, or null while its bytes do not decode. The outcome is remembered until the value is loaded again.
   */
  public ItemRef item() {
    ItemRef known = decode();
    return known == null ? null : known.copy();
  }

  public boolean readable() {
    return decode() != null;
  }

  /**
   * True for the zero-length marker older releases wrote in place of an item they could not store.
   */
  public boolean isEmpty() {
    return !held && bytes.length == 0;
  }

  public int length() {
    return encode().length;
  }

  public byte[] bytes() {
    return encode().clone();
  }

  public void writeTo(DataOutputStream out) throws IOException {
    out.write(encode());
  }

  /**
   * Logs the message once if this item holds bytes that did not decode. Later calls and readable items log nothing.
   */
  public void warnUnreadable(Supplier<String> message) {
    if (decode() != null || isEmpty()) {
      return;
    }
    synchronized (this) {
      if (warned) {
        return;
      }
      warned = true;
    }
    AdaptLog.warn(message.get(), failure);
  }

  /**
   * Stored items are the same when their bytes are. Two items the game handed in compare as stacks until both are saved.
   */
  public boolean sameItem(StoredItem other) {
    if (this == other) {
      return true;
    }
    byte[] mine = bytes;
    byte[] theirs = other.bytes;
    if (mine != null && theirs != null) {
      return Arrays.equals(mine, theirs);
    }
    return held && other.held && (item.equals(other.item) || (item.amount() == other.item.amount() && item.isSimilar(other.item)));
  }

  private ItemRef decode() {
    if (decoded) {
      return item;
    }
    synchronized (this) {
      if (decoded) {
        return item;
      }
      if (bytes.length > 0) {
        try {
          ItemRef read = AdaptCore.platform().host().items().decode(new String(bytes, StandardCharsets.US_ASCII));
          if (read == null || read.isEmpty()) {
            failure = new IllegalArgumentException("The stored item bytes do not decode to an item");
          } else {
            item = read;
          }
        } catch (RuntimeException unreadable) {
          failure = unreadable;
        }
      }
      decoded = true;
      return item;
    }
  }

  private byte[] encode() {
    byte[] known = bytes;
    if (known != null) {
      return known;
    }
    synchronized (this) {
      if (bytes == null) {
        bytes = AdaptCore.platform().host().items().encode(item).getBytes(StandardCharsets.US_ASCII);
      }
      return bytes;
    }
  }
}
