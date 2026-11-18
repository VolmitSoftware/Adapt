package art.arcane.adapt.platform.event;

public interface Cancellable {
  boolean isCancelled();

  void setCancelled(boolean cancelled);
}
