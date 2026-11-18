package art.arcane.adapt.platform;

/** A scheduled task. Cancelling is idempotent and safe from any thread. */
public interface TaskHandle {
  void cancel();

  boolean isCancelled();
}
