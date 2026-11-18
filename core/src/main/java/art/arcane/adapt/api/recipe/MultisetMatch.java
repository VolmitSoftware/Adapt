package art.arcane.adapt.api.recipe;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

public final class MultisetMatch {
  private MultisetMatch() {
  }

  public static <S, T> boolean matches(List<S> specs, List<T> items, BiPredicate<S, T> accepts) {
    if (specs.size() != items.size()) {
      return false;
    }
    int[] itemBySpec = new int[specs.size()];
    Arrays.fill(itemBySpec, -1);
    for (int item = 0; item < items.size(); item++) {
      if (!assign(specs, items, accepts, itemBySpec, item, new boolean[specs.size()])) {
        return false;
      }
    }
    return true;
  }

  private static <S, T> boolean assign(List<S> specs, List<T> items, BiPredicate<S, T> accepts, int[] itemBySpec, int item, boolean[] visited) {
    for (int spec = 0; spec < specs.size(); spec++) {
      if (visited[spec] || !accepts.test(specs.get(spec), items.get(item))) {
        continue;
      }
      visited[spec] = true;
      int holder = itemBySpec[spec];
      if (holder < 0 || assign(specs, items, accepts, itemBySpec, holder, visited)) {
        itemBySpec[spec] = item;
        return true;
      }
    }
    return false;
  }
}
