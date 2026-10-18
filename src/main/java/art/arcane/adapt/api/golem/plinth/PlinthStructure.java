package art.arcane.adapt.api.golem.plinth;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class PlinthStructure {
  public static final List<String> DEFAULT_PEDESTALS = List.of(
      "CHISELED_COPPER", "EXPOSED_CHISELED_COPPER", "WEATHERED_CHISELED_COPPER", "OXIDIZED_CHISELED_COPPER",
      "WAXED_CHISELED_COPPER", "WAXED_EXPOSED_CHISELED_COPPER", "WAXED_WEATHERED_CHISELED_COPPER", "WAXED_OXIDIZED_CHISELED_COPPER");

  private PlinthStructure() {
  }

  public static Set<Material> pedestals(List<String> names, Consumer<String> warn) {
    EnumSet<Material> pedestals = EnumSet.noneOf(Material.class);
    for (String name : names == null ? List.<String>of() : names) {
      String normalized = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
      Material material = Material.getMaterial(normalized);
      if (material == null || material.isLegacy()) {
        warn.accept("Ignoring Animus Plinth pedestal block \"" + name + "\": it is not a known block.");
        continue;
      }
      if (material == Material.LODESTONE) {
        warn.accept("Ignoring Animus Plinth pedestal block \"" + name + "\": the lodestone is the plinth's center.");
        continue;
      }
      pedestals.add(material);
    }
    return Collections.unmodifiableSet(pedestals);
  }

  public static boolean isPart(Material type, Set<Material> pedestals) {
    return type == Material.LODESTONE || pedestals.contains(type);
  }

  public static boolean valid(Blocks blocks, PlinthSpot center, Set<Material> pedestals) {
    if (blocks.type(center.x(), center.y(), center.z()) != Material.LODESTONE) {
      return false;
    }
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      PlinthSpot pedestal = center.slot(slot);
      Material type = blocks.type(pedestal.x(), pedestal.y(), pedestal.z());
      if (type == null || !pedestals.contains(type)) {
        return false;
      }
    }
    return true;
  }

  public static Optional<PlinthTarget> locate(Blocks blocks, PlinthSpot clicked, Set<Material> pedestals) {
    Material type = blocks.type(clicked.x(), clicked.y(), clicked.z());
    if (type == Material.LODESTONE) {
      return valid(blocks, clicked, pedestals) ? Optional.of(new PlinthTarget(clicked, PlinthSlot.CENTER)) : Optional.empty();
    }
    if (type == null || !pedestals.contains(type)) {
      return Optional.empty();
    }
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      PlinthSpot center = new PlinthSpot(clicked.world(), clicked.x() - slot.dx(), clicked.y(), clicked.z() - slot.dz());
      if (valid(blocks, center, pedestals)) {
        return Optional.of(new PlinthTarget(center, slot));
      }
    }
    return Optional.empty();
  }

  @FunctionalInterface
  public interface Blocks {
    Material type(int x, int y, int z);
  }

  public record PlinthTarget(PlinthSpot center, PlinthSlot slot) {
    public PlinthTarget {
      Objects.requireNonNull(center);
      Objects.requireNonNull(slot);
    }

    public PlinthSpot spot() {
      return center.slot(slot);
    }
  }
}
