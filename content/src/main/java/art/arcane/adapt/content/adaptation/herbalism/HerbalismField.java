package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.function.Consumer;

/** Compost Cascade's field sweep: every block in a sphere, up to a block budget, skipping regions this thread does not own. */
public final class HerbalismField {
  private HerbalismField() {
  }

  public static int workFor(int candidates, int limit) {
    return Math.min(Math.max(0, candidates), Math.max(0, limit));
  }

  public static void scan(WorldRef world, Loc center, double radius, int blockBudget, Consumer<BlockRef> visit) {
    int r = Math.max(1, (int) Math.ceil(radius));
    int span = (2 * r) + 1;
    int budget = workFor(span * span * span, blockBudget);
    double rs = radius * radius;
    int inspected = 0;

    for (int x = -r; x <= r; x++) {
      for (int y = -r; y <= r; y++) {
        for (int z = -r; z <= r; z++) {
          if (inspected >= budget) {
            return;
          }

          if ((x * x) + (y * y) + (z * z) > rs) {
            continue;
          }

          inspected++;
          int blockX = center.blockX() + x;
          int blockY = center.blockY() + y;
          int blockZ = center.blockZ() + z;
          if (AdaptApi.scheduler().isRegionized()
              && !AdaptApi.scheduler().owns(new Loc(world, blockX, blockY, blockZ))) {
            continue;
          }
          visit.accept(world.blockAt(blockX, blockY, blockZ));
        }
      }
    }
  }
}
