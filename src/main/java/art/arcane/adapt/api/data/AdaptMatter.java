package art.arcane.adapt.api.data;

import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.matter.slices.RawMatter;

public abstract class AdaptMatter<T> extends RawMatter<T> {
  protected AdaptMatter(int width, int height, int depth, Class<T> type) {
    super(width, height, depth, type);
  }

  @Override
  public Palette<T> getGlobalPalette() {
    return null;
  }
}
