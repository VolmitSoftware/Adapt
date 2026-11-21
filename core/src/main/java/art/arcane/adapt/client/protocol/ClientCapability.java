package art.arcane.adapt.client.protocol;

/** What one player's client negotiated: the protocol version both sides speak and the capability bits both support. */
public record ClientCapability(int version, int mask) {
  public boolean has(int capabilityBit) {
    return (mask & capabilityBit) == capabilityBit;
  }
}
