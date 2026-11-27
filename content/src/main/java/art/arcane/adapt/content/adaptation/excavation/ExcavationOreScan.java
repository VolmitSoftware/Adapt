package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.integration.HiddenVein;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.api.world.BlockScan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Budgeted scans for ore around a dig, including the nearest vein a hidden-ore plugin keeps out of the world. */
public final class ExcavationOreScan {
  private static final Id ANCIENT_DEBRIS = Id.minecraft("ancient_debris");

  private ExcavationOreScan() {
  }

  /** Scan limits: the full radius, the radius searched completely first, block reads, and ores returned. */
  public record Limits(int radius, int denseRadius, int maxSamples, int maxResults) {
  }

  /** A scan of {@code limits.radius()} around {@code origin} for ore that {@code allows} accepts, nearest first. */
  public static BlockScan request(Loc origin, Limits limits, Predicate<Id> allows,
                                                       Consumer<BlockScan.Result> completion) {
    List<BlockScan.Supplied> hiddenMatches = new ArrayList<>(1);
    WorldRef world = origin.world();
    HiddenVein hidden = nearestVein(Integrations.hiddenVeins(origin, limits.radius()), origin);
    if (hidden != null && hidden.location().world() != null && hidden.location().world().uid().equals(world.uid())
        && allows.test(hidden.display())) {
      Loc at = hidden.location();
      double dx = at.x() - origin.x();
      double dy = at.y() - origin.y();
      double dz = at.z() - origin.z();
      double distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);
      if (at.blockY() >= world.minHeight()
          && at.blockY() < world.maxHeight()
          && distanceSquared <= (double) limits.radius() * limits.radius()) {
        hiddenMatches.add(BlockScan.Supplied.at(
            at.blockX(), at.blockY(), at.blockZ(), hidden.display(), distanceSquared
        ));
      }
    }

    return BlockScan.around(origin)
        .radius(limits.radius())
        .denseRadius(limits.denseRadius())
        .maxSamples(limits.maxSamples())
        .maxResults(limits.maxResults())
        .supply(hiddenMatches)
        .matching((Id type) -> isOre(type) && allows.test(type))
        .onComplete(completion)
        .build();
  }

  public static HiddenVein nearestVein(List<HiddenVein> veins, Loc origin) {
    HiddenVein best = null;
    double bestDistanceSq = Double.MAX_VALUE;
    for (HiddenVein vein : veins) {
      Loc at = vein.location();
      double dx = (at.blockX() + 0.5) - origin.x();
      double dy = (at.blockY() + 0.5) - origin.y();
      double dz = (at.blockZ() + 0.5) - origin.z();
      double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
      if (distanceSq < bestDistanceSq) {
        best = vein;
        bestDistanceSq = distanceSq;
      }
    }
    return best;
  }

  public static boolean isOre(Id type) {
    return type.equals(ANCIENT_DEBRIS) || type.legacyName().endsWith("_ORE");
  }

  /** Shovels and pickaxes. */
  public static boolean isDigTool(ItemRef item) {
    if (item == null || item.type().isAir()) {
      return false;
    }

    String name = item.type().legacyName();
    return name.endsWith("_SHOVEL") || name.endsWith("_PICKAXE");
  }
}
