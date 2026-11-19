package art.arcane.adapt.bukkit.platform.data;

import org.bukkit.persistence.PersistentDataContainer;

import java.util.function.Consumer;

/** Where a {@link PdcDataHolder}'s container lives. */
public interface PdcStore {
  /** Current container for reading, or null when nothing can be stored. */
  PersistentDataContainer read();

  /** Applies a change to a writable container and persists it. */
  void write(Consumer<PersistentDataContainer> edit);
}
