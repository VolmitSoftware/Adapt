package art.arcane.adapt.engine.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** The one mantle slice that holds every addon slice value, so the mantle needs no class per addon slice. */
public final class AddonSliceMatter extends AdaptMatter<AddonSliceValues> {
  public AddonSliceMatter(int width, int height, int depth) {
    super(width, height, depth, AddonSliceValues.class);
  }

  public AddonSliceMatter() {
    this(1, 1, 1);
  }

  @Override
  public void writeNode(AddonSliceValues value, DataOutputStream out) throws IOException {
    value.write(out);
  }

  @Override
  public AddonSliceValues readNode(DataInputStream in) throws IOException {
    return AddonSliceValues.read(in);
  }
}
