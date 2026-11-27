package art.arcane.adapt.content.golem;

import java.util.Objects;

public final class GolemWorkStats {
  private static volatile Sink sink = Sink.NONE;

  private GolemWorkStats() {
  }

  public static void setSink(Sink next) {
    sink = Objects.requireNonNull(next);
  }

  public static void completed(GolemHandle handle, GolemTask task) {
    sink.completed(Objects.requireNonNull(handle), Objects.requireNonNull(task));
  }

  public static void moved(GolemHandle handle, long items) {
    if (items > 0L) {
      sink.moved(Objects.requireNonNull(handle), items);
    }
  }

  public interface Sink {
    Sink NONE = new Sink() {
      @Override
      public void completed(GolemHandle handle, GolemTask task) {
      }

      @Override
      public void moved(GolemHandle handle, long items) {
      }
    };

    void completed(GolemHandle handle, GolemTask task);

    void moved(GolemHandle handle, long items);
  }
}
