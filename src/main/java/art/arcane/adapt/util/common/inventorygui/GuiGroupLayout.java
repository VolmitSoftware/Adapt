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

  public static <T> Plan<T> plan(List<List<T>> groups, boolean reserveNavigationRow, boolean reserveSettingsRow) {
    int availableRows = GuiLayout.MAX_ROWS - (reserveSettingsRow ? 1 : 0);
    List<List<List<T>>> groupRows = new ArrayList<>(groups.size());
    int totalRows = 0;
    for (List<T> group : groups) {
      if (group.isEmpty()) {
        continue;
      }
      List<List<T>> rows = chunk(group, GuiLayout.WIDTH);
      groupRows.add(rows);
      totalRows += rows.size();
    }

    boolean navigation = reserveNavigationRow;
    int capacity = availableRows - (navigation ? 1 : 0);
    if (totalRows > capacity) {
      navigation = true;
      capacity = availableRows - 1;
    }

    List<List<List<T>>> pageRows = new ArrayList<>();
    List<List<T>> current = new ArrayList<>();
    for (List<List<T>> rows : groupRows) {
      if (rows.size() > capacity) {
        for (int from = 0; from < rows.size(); from += capacity) {
          flush(pageRows, current);
          current = new ArrayList<>(rows.subList(from, Math.min(rows.size(), from + capacity)));
        }
        continue;
      }
      if (current.size() + rows.size() > capacity) {
        flush(pageRows, current);
        current = new ArrayList<>();
      }
      current.addAll(rows);
    }
    flush(pageRows, current);
    if (pageRows.isEmpty()) {
      pageRows.add(List.of());
    }

    int contentRows = pageRows.size() == 1 ? Math.max(1, pageRows.get(0).size()) : capacity;
    int height = Math.max(1, Math.min(GuiLayout.MAX_ROWS,
        contentRows + (navigation ? 1 : 0) + (reserveSettingsRow ? 1 : 0)));

    List<Page<T>> pages = new ArrayList<>(pageRows.size());
    int offset = 0;
    for (List<List<T>> page : pageRows) {
      pages.add(new Page<>(page, offset));
      for (List<T> row : page) {
        offset += row.size();
      }
    }
    return new Plan<>(height, navigation, pages);
  }

  private static <T> void flush(List<List<List<T>>> pageRows, List<List<T>> current) {
    if (!current.isEmpty()) {
      pageRows.add(current);
    }
  }

  private static <T> List<List<T>> chunk(List<T> items, int size) {
    List<List<T>> chunks = new ArrayList<>((items.size() + size - 1) / size);
    for (int from = 0; from < items.size(); from += size) {
      chunks.add(items.subList(from, Math.min(items.size(), from + size)));
    }
    return chunks;
  }

  private record Slot(int group, int index) {
  }

  public record Plan<T>(int rows, boolean hasNavigationRow, List<Page<T>> pages) {
    public int pageCount() {
      return pages.size();
    }
  }

  public record Page<T>(List<List<T>> rows, int offset) {
    public int size() {
      int size = 0;
      for (List<T> row : rows) {
        size += row.size();
      }
      return size;
    }
  }
}
