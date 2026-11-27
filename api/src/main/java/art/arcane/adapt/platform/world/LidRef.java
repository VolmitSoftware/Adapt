package art.arcane.adapt.platform.world;

import org.jetbrains.annotations.ApiStatus;

/**
 * The lid of a chest, barrel or shulker box. Opening it plays the open animation and sound as if a player opened
 * it; it stays open until closed again or its last viewer leaves. Region-owned like the block.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface LidRef {
  /** Whether any player has the container open. */
  boolean viewed();

  /** True while the lid is open. On Spigot the state cannot be read and this is always false. */
  boolean isOpen();

  /** Opens the lid with the open animation and sound. */
  void open();

  /** Closes a lid opened by {@link #open()}, with the close sound; it stays open while a player still views it. */
  void close();
}
