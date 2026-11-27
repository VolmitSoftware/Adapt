package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.content.data.StoredItem;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Stores a slot as its id, a length and that many item bytes. Reading never decodes the item: the bytes go into the
 * slot as they are and are written back the same on every save.
 */
public final class PlinthSlotCodec implements BlockSlice.Codec<PlinthSlotItem> {
  public static final int MAX_ITEM_BYTES = 8 * 1024 * 1024;
  private static final int LOST = 0;
  public static final PlinthSlotCodec INSTANCE = new PlinthSlotCodec();
  private static volatile BlockSlice<PlinthSlotItem> slice;

  private PlinthSlotCodec() {
  }

  /** Declares the plinth slot slice {@code adapt:plinth_slot}; the built-in addon calls this as it registers. */
  public static void declare(ContentRegistrar registrar) {
    slice = registrar.blockSlice("plinth_slot", PlinthSlotItem.class, INSTANCE);
  }

  /**
   * The plinth slot slice the built-in addon declared.
   *
   * @throws IllegalStateException before the built-in addon registered
   */
  public static BlockSlice<PlinthSlotItem> slice() {
    BlockSlice<PlinthSlotItem> declared = slice;
    if (declared == null) {
      throw new IllegalStateException("The plinth slot slice is not declared yet");
    }
    return declared;
  }

  @Override
  public void write(PlinthSlotItem slot, DataOutputStream out) throws IOException {
    out.writeLong(slot.id().getMostSignificantBits());
    out.writeLong(slot.id().getLeastSignificantBits());
    StoredItem stored = slot.stored();
    int length = stored.length();
    if (length > MAX_ITEM_BYTES) {
      AdaptApi.log().error("Not saving the Animus Plinth item " + slot.id() + ": it encodes to " + length + " bytes, more than the " + MAX_ITEM_BYTES + " byte limit.", null);
      out.writeInt(LOST);
      return;
    }
    out.writeInt(length);
    stored.writeTo(out);
  }

  @Override
  public PlinthSlotItem read(DataInputStream in) throws IOException {
    UUID id = new UUID(in.readLong(), in.readLong());
    int length = in.readInt();
    if (length < 0 || length > MAX_ITEM_BYTES) {
      String message = "Animus Plinth item " + id + " claims " + length + " bytes; items hold 0-" + MAX_ITEM_BYTES + ".";
      AdaptApi.log().error(message, null);
      throw new IOException(message);
    }
    byte[] bytes = new byte[length];
    in.readFully(bytes);
    return PlinthSlotItem.stored(id, bytes);
  }
}
