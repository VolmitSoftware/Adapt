package art.arcane.adapt.pack.link;

import art.arcane.adapt.AdaptPorts;

import java.util.UUID;

/**
 * The platform transport for server resource packs: Bukkit calls the player pack API, the mods send the vanilla push
 * and pop packets. Installed through {@link AdaptPorts}; {@link #NONE} delivers nothing. Methods may be called from
 * any thread; implementations hop to the player's thread where the platform needs it.
 */
public interface PackLink {
  PackLink NONE = new PackLink() {
    @Override
    public boolean available() {
      return false;
    }

    @Override
    public void offer(UUID playerId, PackOffer offer) {
    }

    @Override
    public void remove(UUID playerId, UUID packId) {
    }

    @Override
    public void listen(StatusListener listener) {
    }
  };

  static PackLink installed() {
    return AdaptPorts.get(PackLink.class, NONE);
  }

  /** False when this server cannot offer a pack next to its own, so the pack is export-only. */
  boolean available();

  void offer(UUID playerId, PackOffer offer);

  void remove(UUID playerId, UUID packId);

  /** Sets the one receiver of client status reports; null stops reporting. */
  void listen(StatusListener listener);

  @FunctionalInterface
  interface StatusListener {
    void status(UUID playerId, UUID packId, PackState state);
  }
}
