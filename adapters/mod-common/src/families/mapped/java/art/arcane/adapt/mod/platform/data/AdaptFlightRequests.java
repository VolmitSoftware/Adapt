package art.arcane.adapt.mod.platform.data;

/**
 * Implemented on Player by PlayerFlightRequestMixin: whether the player's may-fly is only a flight request grant
 * (PlayerRef#grantFlightRequests), which must not cancel fall damage. Lives on the player object, so a respawned
 * player starts without it.
 */
public interface AdaptFlightRequests {
    boolean adapt$flightRequests();

    void adapt$setFlightRequests(boolean granted);
}
