package art.arcane.adapt.content.matter;

import art.arcane.adapt.content.golem.plinth.PlinthSlotCodec;
import art.arcane.adapt.content.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.engine.data.AdaptMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** The mantle slice {@code adapt:plinth_slot} of plinth slot items, written with the built-in plinth codec. */
public final class PlinthSlotItemMatter extends AdaptMatter<PlinthSlotItem> {
  public PlinthSlotItemMatter(int width, int height, int depth) {
    super(width, height, depth, PlinthSlotItem.class);
  }

  public PlinthSlotItemMatter() {
    this(1, 1, 1);
  }

  @Override
  public void writeNode(PlinthSlotItem value, DataOutputStream out) throws IOException {
    PlinthSlotCodec.INSTANCE.write(value, out);
  }

  @Override
  public PlinthSlotItem readNode(DataInputStream in) throws IOException {
    return PlinthSlotCodec.INSTANCE.read(in);
  }
}
