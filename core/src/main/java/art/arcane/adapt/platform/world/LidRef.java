package art.arcane.adapt.platform.world;

/**
 * The lid of a chest, barrel or shulker box. Opening it plays the open animation and sound as if a player opened
 * it; it stays open until closed again or its last viewer leaves. Region-owned like the block.
 */
public interface LidRef {
  /** Whether any player has the container open. */
  boolean viewed();

  boolean isOpen();

  void open();

  void close();
}
