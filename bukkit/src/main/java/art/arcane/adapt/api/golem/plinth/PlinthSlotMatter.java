package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.data.AdaptMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** The mantle slice of plinth slots; the value format is {@link PlinthSlotCodec}. */
public class PlinthSlotMatter extends AdaptMatter<PlinthSlotItem> {
  public PlinthSlotMatter(int width, int height, int depth) {
    super(width, height, depth, PlinthSlotItem.class);
  }

  public PlinthSlotMatter() {
    this(1, 1, 1);
  }

  @Override
  public void writeNode(PlinthSlotItem slot, DataOutputStream out) throws IOException {
    PlinthSlotCodec.INSTANCE.write(slot, out);
  }

  @Override
  public PlinthSlotItem readNode(DataInputStream in) throws IOException {
    return PlinthSlotCodec.INSTANCE.read(in);
  }
}
