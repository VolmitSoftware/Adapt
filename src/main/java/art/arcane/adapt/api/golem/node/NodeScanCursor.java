package art.arcane.adapt.api.golem.node;

import java.util.Arrays;

public final class NodeScanCursor {
  private int position;

  public synchronized Window next(int budget, int volume) {
    if (position >= volume) {
      position = 0;
    }
    int start = position;
    int count = Math.max(0, Math.min(budget, volume - start));
    position = start + count >= volume ? 0 : start + count;
    return new Window(start, count);
  }

  public record Window(int start, int count, int[] indices) {
    public Window(int start, int count) {
      this(start, count, null);
    }

    Window(int[] indices, int count) {
      this(0, count, Arrays.copyOf(indices, count));
    }
  }
}
