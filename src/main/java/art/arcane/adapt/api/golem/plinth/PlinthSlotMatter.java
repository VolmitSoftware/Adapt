package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.nms.NMS;
import art.arcane.spatial.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class PlinthSlotMatter extends RawMatter<PlinthSlotItem> {
  public static final int MAX_ITEM_BYTES = 8 * 1024 * 1024;
  private static final int UNREADABLE = 0;

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
    if (!slot.readable()) {
      out.writeInt(UNREADABLE);
      return;
    }
    byte[] bytes = NMS.serializeStack(slot.item()).getBytes(StandardCharsets.US_ASCII);
    if (bytes.length > MAX_ITEM_BYTES) {
      Adapt.error("Not saving the Animus Plinth item " + slot.id() + ": it encodes to " + bytes.length + " bytes, more than the " + MAX_ITEM_BYTES + " byte limit.");
      out.writeInt(UNREADABLE);
      return;
    }
    out.writeInt(bytes.length);
    out.write(bytes);
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
    if (length == UNREADABLE) {
      return PlinthSlotItem.unreadable(id);
    }
    byte[] bytes = new byte[length];
    in.readFully(bytes);
    try {
      return PlinthSlotItem.of(id, NMS.deserializeStack(new String(bytes, StandardCharsets.US_ASCII)));
    } catch (RuntimeException corrupt) {
      Adapt.warn("Animus Plinth item " + id + " could not be read; it is removed when its chunk loads.", corrupt);
      return PlinthSlotItem.unreadable(id);
    }
  }
}
