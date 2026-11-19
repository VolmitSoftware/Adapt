package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PlinthStructure {
  public static final List<String> DEFAULT_PEDESTALS = List.of(
      "CHISELED_COPPER", "EXPOSED_CHISELED_COPPER", "WEATHERED_CHISELED_COPPER", "OXIDIZED_CHISELED_COPPER",
      "WAXED_CHISELED_COPPER", "WAXED_EXPOSED_CHISELED_COPPER", "WAXED_WEATHERED_CHISELED_COPPER", "WAXED_OXIDIZED_CHISELED_COPPER");

  private static final Id LODESTONE = Id.minecraft("lodestone");
  private static final String CHISELED_COPPER = "CHISELED_COPPER";
  private static final String CUT_COPPER = "CUT_COPPER";

  private PlinthStructure() {
  }

  /**
   * {@link #DEFAULT_PEDESTALS} on servers with chiseled copper (1.20.3+); the matching cut copper blocks before that,
   * so a plinth can still be built.
   */
  public static List<String> defaultPedestals() {
    List<String> pedestals = new ArrayList<>(DEFAULT_PEDESTALS.size());
    for (String name : DEFAULT_PEDESTALS) {
      pedestals.add(PlatformSupport.materialOr(name, name.replace(CHISELED_COPPER, CUT_COPPER)));
    }
    return List.copyOf(pedestals);
  }

  private static boolean known(Id material) {
    AdaptCore core = AdaptCore.current();
    return core == null || core.getPlatform().host().registries().exists(RegistryKind.BLOCK, material);
  }

  public static Set<Id> pedestals(List<String> names, Predicate<Id> isBlock, Consumer<String> warn) {
    Set<Id> pedestals = new LinkedHashSet<>();
    for (String name : names == null ? List.<String>of() : names) {
      Id material = name == null ? null : MaterialNames.exact(name.trim().toUpperCase(Locale.ROOT));
      if (material == null || !known(material) || !isBlock.test(material)) {
        warn.accept("Ignoring Animus Plinth pedestal block \"" + name + "\": it is not a known block.");
        continue;
      }
      if (material.equals(LODESTONE)) {
        warn.accept("Ignoring Animus Plinth pedestal block \"" + name + "\": the lodestone is the plinth's center.");
        continue;
      }
      pedestals.add(material);
    }
    return Set.copyOf(pedestals);
  }

  public static boolean isPart(Id type, Set<Id> pedestals) {
    return LODESTONE.equals(type) || pedestals.contains(type);
  }

  public static boolean valid(Blocks blocks, PlinthSpot center, Set<Id> pedestals) {
    if (!LODESTONE.equals(blocks.type(center.x(), center.y(), center.z()))) {
      return false;
    }
    for (PlinthSlot slot : PlinthSlot.PEDESTALS) {
      PlinthSpot pedestal = center.slot(slot);
      Id type = blocks.type(pedestal.x(), pedestal.y(), pedestal.z());
      if (type == null || !pedestals.contains(type)) {
        return false;
      }
    }
    return true;
  }

  public static Optional<PlinthTarget> locate(Blocks blocks, PlinthSpot clicked, Set<Id> pedestals) {
    Id type = blocks.type(clicked.x(), clicked.y(), clicked.z());
    if (LODESTONE.equals(type)) {
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
    Id type(int x, int y, int z);
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
