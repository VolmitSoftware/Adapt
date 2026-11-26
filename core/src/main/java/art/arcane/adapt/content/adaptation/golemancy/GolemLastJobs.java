package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemTask;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** The task each loaded golem finished last; forgotten with the handle. */
public final class GolemLastJobs {
  private static final Map<GolemHandle, GolemTask> LAST = Collections.synchronizedMap(new WeakHashMap<>());

  private GolemLastJobs() {
  }

  public static void finished(GolemHandle golem, GolemTask task) {
    LAST.put(Objects.requireNonNull(golem), Objects.requireNonNull(task));
  }

  public static @Nullable GolemTask last(GolemHandle golem) {
    return LAST.get(golem);
  }
}
