package art.arcane.adapt.util.common.inventorygui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class GuiGroupLayout {
  private GuiGroupLayout() {
  }

  public static <T> List<List<T>> arrange(
      List<T> visible,
      Function<T, String> nameOf,
      ToIntFunction<T> rankOf,
      Comparator<T> defaultOrder,
      List<List<String>> declaredGroups
  ) {
    Map<String, Slot> slots = new HashMap<>();
    for (int group = 0; group < declaredGroups.size(); group++) {
      List<String> names = declaredGroups.get(group);
      for (int index = 0; index < names.size(); index++) {
        slots.putIfAbsent(names.get(index).toLowerCase(Locale.ROOT), new Slot(group, index));
      }
    }

    List<List<T>> buckets = new ArrayList<>(declaredGroups.size());
    for (int group = 0; group < declaredGroups.size(); group++) {
      buckets.add(new ArrayList<>());
    }
    List<T> leftovers = new ArrayList<>();
    for (T item : visible) {
      Slot slot = slots.get(nameOf.apply(item).toLowerCase(Locale.ROOT));
      if (slot == null) {
        leftovers.add(item);
      } else {
        buckets.get(slot.group()).add(item);
      }
    }

    Comparator<T> withinGroup = Comparator.comparingInt(rankOf)
        .thenComparingInt((T item) -> slots.get(nameOf.apply(item).toLowerCase(Locale.ROOT)).index());
    List<List<T>> arranged = new ArrayList<>(buckets.size() + 1);
    for (List<T> bucket : buckets) {
      bucket.sort(withinGroup);
      if (!bucket.isEmpty()) {
        arranged.add(bucket);
      }
    }
    leftovers.sort(defaultOrder);
    if (!leftovers.isEmpty()) {
      arranged.add(leftovers);
    }
    return arranged;
  }

  private record Slot(int group, int index) {
  }
}
