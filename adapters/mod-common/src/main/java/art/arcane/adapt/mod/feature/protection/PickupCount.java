package art.arcane.adapt.mod.feature.protection;

final class PickupCount {
    private PickupCount() {
    }

    /** Items a pickup takes from a stack of {@code amount} that leaves {@code remaining} on the ground. */
    static int taken(int amount, int remaining) {
        return Math.max(0, amount - Math.min(amount, Math.max(0, remaining)));
    }
}
