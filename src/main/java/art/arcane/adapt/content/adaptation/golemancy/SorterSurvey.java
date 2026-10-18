package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

final class SorterSurvey {
  private static final Comparator<SorterDestination> BY_PRIORITY = Comparator.comparingInt(SorterDestination::priority).reversed()
      .thenComparingDouble(SorterDestination::distanceSquared);
  private static final Comparator<SorterDestination> BY_DISTANCE = Comparator.comparingDouble(SorterDestination::distanceSquared);
  private static final Comparator<Available> LISTING = Comparator.comparing((Available entry) -> entry.prototype().getType().name())
      .thenComparing(Comparator.comparingInt(Available::count).reversed());

  private final SorterMatcher matcher;
  private final GolemSorterCore.Config config;
  private final int level;
  private final SorterDestination input;
  private final List<SorterDestination> destinations;

  SorterSurvey(SorterMatcher matcher, GolemSorterCore.Config config, int level, SorterDestination input, List<SorterDestination> destinations) {
    this.matcher = Objects.requireNonNull(matcher);
    this.config = Objects.requireNonNull(config);
    this.level = level;
    this.input = Objects.requireNonNull(input);
    this.destinations = List.copyOf(destinations);
  }

  SorterMatcher matcher() {
    return matcher;
  }

  SorterDestination input() {
    return input;
  }

  Optional<SorterDestination> destination(ContainerId id) {
    if (input.id().equals(id)) {
      return Optional.of(input);
    }
    for (SorterDestination destination : destinations) {
      if (destination.id().equals(id)) {
        return Optional.of(destination);
      }
    }
    return Optional.empty();
  }

  Optional<SorterDestination> choose(ItemStack item) {
    if (SorterRules.shulker(item.getType()) && !SorterRules.unlocked(level, config.shulkerBoxLevel)) {
      return Optional.empty();
    }
    boolean filters = SorterRules.unlocked(level, config.filterLevel);
    if (filters) {
      Optional<SorterDestination> filtered = best(destination -> destination.kind() == SorterDestination.Kind.FILTER
          && destination.filter().filterCount() > 0 && receives(destination, item) && fits(destination, item), BY_PRIORITY);
      if (filtered.isPresent()) {
        return filtered;
      }
    }
    Optional<SorterDestination> holding = best(destination -> destination.kind() == SorterDestination.Kind.PLAIN
        && destination.stock().holds(held -> matcher.same(held, item)) && fits(destination, item), BY_DISTANCE);
    if (holding.isPresent()) {
      return holding;
    }
    Optional<SorterDestination> grouped = best(destination -> destination.kind() == SorterDestination.Kind.PLAIN
        && destination.stock().holds(held -> matcher.grouped(held, item)) && fits(destination, item), BY_DISTANCE);
    if (grouped.isPresent() || !filters) {
      return grouped;
    }
    return best(destination -> destination.kind() == SorterDestination.Kind.OVERFLOW && matcher.accepts(destination.filter(), item)
        && fits(destination, item), BY_PRIORITY);
  }

  int cap(SorterDestination destination, ItemStack item) {
    int target = restockTarget(destination);
    if (target <= 0) {
      return Integer.MAX_VALUE;
    }
    return Math.max(0, target - destination.stock().count(held -> matcher.same(held, item)));
  }

  List<RestockNeed> restockNeeds() {
    if (!SorterRules.unlocked(level, config.restockLevel)) {
      return List.of();
    }
    List<RestockNeed> needs = new ArrayList<>();
    for (SorterDestination destination : destinations) {
      int target = restockTarget(destination);
      if (target <= 0) {
        continue;
      }
      List<ItemStack> counted = new ArrayList<>();
      for (ItemStack entry : destination.filter().filterItems()) {
        if (counted.stream().anyMatch(previous -> matcher.same(previous, entry))) {
          continue;
        }
        counted.add(entry);
        int deficit = target - destination.stock().count(held -> matcher.same(held, entry));
        if (deficit > 0) {
          needs.add(new RestockNeed(destination, entry, deficit));
        }
      }
    }
    return needs;
  }

  Optional<SorterDestination> restockSource(RestockNeed need) {
    ItemStack wanted = need.item();
    SorterDestination target = need.target();
    SorterDestination nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (SorterDestination candidate : everyContainer()) {
      if (candidate.id().equals(target.id()) || wants(candidate, wanted) || !candidate.stock().holds(held -> matcher.same(held, wanted))) {
        continue;
      }
      double distance = candidate.id().distanceSquared(target.id());
      if (distance < nearestDistance) {
        nearest = candidate;
        nearestDistance = distance;
      }
    }
    return Optional.ofNullable(nearest);
  }

  Optional<SorterDestination> fetchSource(ItemStack prototype) {
    SorterDestination nearest = null;
    for (SorterDestination candidate : everyContainer()) {
      if (candidate.stock().holds(held -> held.isSimilar(prototype))
          && (nearest == null || candidate.distanceSquared() < nearest.distanceSquared())) {
        nearest = candidate;
      }
    }
    return Optional.ofNullable(nearest);
  }

  List<Available> available() {
    List<ItemStack> prototypes = new ArrayList<>();
    List<int[]> counts = new ArrayList<>();
    for (SorterDestination container : everyContainer()) {
      for (SorterStock.Entry entry : container.stock().entries()) {
        int index = indexOfSimilar(prototypes, entry.prototype());
        if (index < 0) {
          prototypes.add(entry.prototype());
          counts.add(new int[]{entry.count()});
        } else {
          counts.get(index)[0] += entry.count();
        }
      }
    }
    List<Available> listing = new ArrayList<>(prototypes.size());
    for (int index = 0; index < prototypes.size(); index++) {
      listing.add(new Available(prototypes.get(index), counts.get(index)[0]));
    }
    listing.sort(LISTING);
    return listing;
  }

  private boolean receives(SorterDestination destination, ItemStack item) {
    return restockTarget(destination) > 0 ? matcher.listed(destination.filter(), item) : matcher.accepts(destination.filter(), item);
  }

  private boolean fits(SorterDestination destination, ItemStack item) {
    return destination.stock().room(item) > 0 && cap(destination, item) > 0;
  }

  private boolean wants(SorterDestination destination, ItemStack item) {
    return restockTarget(destination) > 0 && matcher.listed(destination.filter(), item);
  }

  private int restockTarget(SorterDestination destination) {
    if (destination.kind() != SorterDestination.Kind.FILTER || !SorterRules.unlocked(level, config.restockLevel)
        || !destination.filter().whitelist()) {
      return 0;
    }
    return SorterRules.restockTarget(destination.filter());
  }

  private Optional<SorterDestination> best(Predicate<SorterDestination> eligible, Comparator<SorterDestination> order) {
    SorterDestination best = null;
    for (SorterDestination destination : destinations) {
      if (eligible.test(destination) && (best == null || order.compare(destination, best) < 0)) {
        best = destination;
      }
    }
    return Optional.ofNullable(best);
  }

  private List<SorterDestination> everyContainer() {
    List<SorterDestination> all = new ArrayList<>(destinations.size() + 1);
    all.add(input);
    all.addAll(destinations);
    return all;
  }

  private static int indexOfSimilar(List<ItemStack> prototypes, ItemStack item) {
    for (int index = 0; index < prototypes.size(); index++) {
      if (prototypes.get(index).isSimilar(item)) {
        return index;
      }
    }
    return -1;
  }

  record RestockNeed(SorterDestination target, ItemStack item, int deficit) {
  }

  record Available(ItemStack prototype, int count) {
  }
}
