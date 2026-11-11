package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.data.AdaptMatter;
import art.arcane.adapt.api.data.StoredItem;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Stores a slot as its id, a length and that many item bytes. Reading never decodes the item: the bytes go into the
 * slot as they are and are written back the same on every save.
 */
public class PlinthSlotMatter extends AdaptMatter<PlinthSlotItem> {
  public static final int MAX_ITEM_BYTES = 8 * 1024 * 1024;
  private static final int LOST = 0;

  public PlinthSlotMatter(int width, int height, int depth) {
    super(width, height, depth, PlinthSlotItem.class);
  }

  public PlinthSlotMatter() {
    this(1, 1, 1);
  }

  @Override
  public void writeNode(PlinthSlotItem slot, DataOutputStream out) throws IOException {
    out.writeLong(slot.id().getMostSignificantBits());
    out.writeLong(slot.id().getLeastSignificantBits());
    StoredItem stored = slot.stored();
    int length = stored.length();
    if (length > MAX_ITEM_BYTES) {
      Adapt.error("Not saving the Animus Plinth item " + slot.id() + ": it encodes to " + length + " bytes, more than the " + MAX_ITEM_BYTES + " byte limit.");
      out.writeInt(LOST);
      return;
    }
    out.writeInt(length);
    stored.writeTo(out);
  }

  @Override
  public PlinthSlotItem readNode(DataInputStream in) throws IOException {
    UUID id = new UUID(in.readLong(), in.readLong());
    int length = in.readInt();
    if (length < 0 || length > MAX_ITEM_BYTES) {
      String message = "Animus Plinth item " + id + " claims " + length + " bytes; items hold 0-" + MAX_ITEM_BYTES + ".";
      Adapt.error(message);
      throw new IOException(message);
    }
    byte[] bytes = new byte[length];
    in.readFully(bytes);
    return PlinthSlotItem.stored(id, bytes);
  }
}
